package com.innygr.icreateordernetworking.delivery;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.compat.CreateCompat;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.packagePort.frogport.FrogportBlockEntity;
import com.simibubi.create.foundation.item.ItemHelper;
import net.createmod.catnip.data.Pair;
import org.apache.commons.lang3.mutable.MutableBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

/**
 * Create 6.0.x / Minecraft 1.21.1 Frogport route.
 *
 * The route is intentionally Packager-mediated: each endpoint is a Terminal
 * Logger adjacent to a Packager, with the Frogport above that Packager. The
 * source Packager packages the transaction-marked items and Create routes that
 * package using its normal address/Frogport behaviour. No direct Frogport-to-
 * Logger connection is assumed. A destination receipt is accepted only after
 * the marked items have been unpacked into the named destination Logger.
 */
public final class PackagerFrogportAdapter implements AutomatedDeliveryAdapter {
    @Override public DeliveryRoute route() { return DeliveryRoute.FROGPORT; }

    @Override
    public boolean isAvailable() {
        return CreateCompat.isFrogportAvailable();
    }

    @Override
    public boolean isAuthorized(ServerLevel level, DeliveryRequest request) {
        if (!isAvailable() || level == null || request == null
                || request.route() != DeliveryRoute.FROGPORT || !request.isWellFormed()) return false;
        EndpointPair pair = EndpointPair.parse(request.sourceReference());
        if (pair == null || pair.source().equals(pair.destination())
                || !level.hasChunkAt(pair.source()) || !level.hasChunkAt(pair.destination())) return false;
        if (!(level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity source)
                || !(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)
                || !request.actorId().equals(source.getOwnerId())
                || !request.actorId().equals(destination.getOwnerId())) return false;
        PackagerBlockEntity sourcePackager = findSourcePackager(level, source);
        PackagerBlockEntity destinationPackager = findDestinationPackager(level, destination);
        // Create's packager must have a live inventory target and a nonblank
        // package address. The Frogport owns the routing target; the destination
        // receipt, not the address string, proves where the cargo arrived.
        return sourcePackager != null && destinationPackager != null
                && sourcePackager.targetInventory != null
                && sourcePackager.targetInventory.getInventory() != null
                && sourcePackager.signBasedAddress != null
                && !sourcePackager.signBasedAddress.isBlank();
    }

    @Override
    public PreparedDelivery prepareCargo(ServerLevel level, DeliveryRequest request) {
        if (!isAuthorized(level, request)) return null;
        EndpointPair pair = EndpointPair.parse(request.sourceReference());
        TerminalLoggerBlockEntity source = (TerminalLoggerBlockEntity) level.getBlockEntity(pair.source());

        // Factory sourcing may already have reserved and marked the cargo after
        // persisting the journal. Reuse that exact reservation instead of sourcing twice.
        int existingMarked = 0;
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            ItemStack stack = source.getInventory().getStackInSlot(slot);
            if (TrainShipmentMarker.matches(stack, request.transactionId())) {
                if (!BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId())) return null;
                existingMarked += stack.getCount();
            }
        }
        if (existingMarked > 0) {
            return existingMarked == request.count() ? preparedFor(level, request, pair) : null;
        }

        // Keep the normal direct-logger path for cargo already present at source.
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            ItemStack current = source.getInventory().getStackInSlot(slot);
            if (current.isEmpty()
                    || !BuiltInRegistries.ITEM.getKey(current.getItem()).equals(request.itemId())
                    || TrainShipmentMarker.transactionId(current) != null
                    || current.getCount() < request.count()) continue;

            int targetSlot = slot;
            if (current.getCount() > request.count()) {
                targetSlot = findEmptySlot(source.getInventory(), slot);
                if (targetSlot < 0) continue;
            }
            ItemStack cargo = source.getInventory().extractItem(slot, request.count(), false);
            if (cargo.isEmpty() || cargo.getCount() != request.count()) {
                if (!cargo.isEmpty()) source.getInventory().insertItem(slot, cargo, false);
                return null;
            }
            if (targetSlot != slot) {
                if (!source.getInventory().getStackInSlot(targetSlot).isEmpty()) {
                    source.getInventory().insertItem(slot, cargo, false);
                    return null;
                }
                TrainShipmentMarker.mark(cargo, request);
                source.getInventory().setStackInSlot(targetSlot, cargo);
            } else {
                TrainShipmentMarker.mark(cargo, request);
                source.getInventory().setStackInSlot(slot, cargo);
            }
            return preparedFor(level, request, pair);
        }
        return null;
    }

    @Override
    public PreparedDelivery resumeCargo(ServerLevel level, ShipmentJournalData.Shipment shipment) {
        if (level == null || shipment == null || shipment.route() != DeliveryRoute.FROGPORT
                || shipment.stage() != ShipmentJournalData.Stage.RESERVED
                || level.getServer() == null || !level.getServer().isSameThread()) return null;
        EndpointPair pair = EndpointPair.parse(shipment.sourceReference());
        if (pair == null || !level.hasChunkAt(pair.source()) || !level.hasChunkAt(pair.destination())) return null;
        if (!(level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity source)
                || !(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)
                || !shipment.claimantId().equals(source.getOwnerId())
                || !shipment.claimantId().equals(destination.getOwnerId())
                || findSourcePackager(level, source) == null
                || findDestinationPackager(level, destination) == null) return null;
        Order order = OrderData.get(level).find(shipment.orderId());
        if (order == null) return null;
        DeliveryRequest request = new DeliveryRequest(shipment.orderId(), order.itemId(), shipment.count(),
                DeliveryRoute.FROGPORT, shipment.claimantId(), shipment.sourceReference(), shipment.transactionId());

        int markedInSource = 0;
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            ItemStack stack = source.getInventory().getStackInSlot(slot);
            if (!TrainShipmentMarker.matches(stack, shipment.transactionId())) continue;
            if (!TrainShipmentMarker.matches(stack, shipment)) return null;
            markedInSource += stack.getCount();
        }
        PackagerBlockEntity packager = findSourcePackager(level, source);
        int markedInPackage = countMarkedPackages(packager, shipment);
        if (markedInSource == shipment.count() || markedInPackage == shipment.count()) {
            return preparedFor(level, request, pair);
        }
        // Missing source cargo and no matching source package may mean it is
        // already in transit. Never replace it with fresh stock during recovery.
        return null;
    }

    private PreparedDelivery preparedFor(ServerLevel level, DeliveryRequest request, EndpointPair pair) {
        return new PreparedDelivery() {
            @Override
            public boolean commit() {
                if (level.getServer() == null || !level.getServer().isSameThread()) return false;
                TerminalLoggerBlockEntity source =
                        (TerminalLoggerBlockEntity) level.getBlockEntity(pair.source());
                PackagerBlockEntity packager = source == null ? null : findSourcePackager(level, source);
                if (packager == null) return false;

                // A Stock Link is the request boundary for Create logistics. Do not
                // use attemptToSend(null): that generic path can take unrelated stock
                // from the target inventory and does not carry an item-specific request.
                ItemStack markedCargo = findMarkedSourceCargo(source, request);
                PackagerLinkBlockEntity stockLink = findStockLink(level, packager);
                if (markedCargo == null || stockLink == null
                        || countMarkedSourceCargo(source, request) != request.count())
                    return false;

                Pair<PackagerBlockEntity, PackagingRequest> pair;
                try {
                    pair = stockLink.processRequest(markedCargo.copyWithCount(1), request.count(),
                            packager.signBasedAddress, 0, new MutableBoolean(true),
                            request.orderId().hashCode(), null, null);
                } catch (RuntimeException | LinkageError ignored) {
                    return false;
                }
                if (pair == null || pair.getFirst() != packager || pair.getSecond() == null
                        || pair.getSecond().getCount() != request.count()) return false;

                PackagingRequest packagingRequest = pair.getSecond();
                List<PackagingRequest> requests = new ArrayList<>();
                requests.add(packagingRequest);
                int previousCount = packagingRequest.getCount();
                ItemStack previousHeldBox = packager.heldBox;
                int previousQueuedCount = packager.queuedExitingPackages.size();
                try {
                    packager.attemptToSend(requests);
                } catch (RuntimeException | LinkageError ignored) {
                    // Keep the RESERVED journal and any marked physical cargo for recovery.
                    return false;
                }
                if (packagingRequest.getCount() >= previousCount) return false;

                // Create has now extracted the exact requested stack into a held or
                // queued package. Attach the transaction marker to that newly-created
                // package's contents before the package can leave the Packager.
                if (!markNewestCreatedPackage(packager, request, previousHeldBox, previousQueuedCount))
                    return false;
                ShipmentJournalData.Shipment shipment =
                        ShipmentJournalData.get(level).find(request.transactionId());
                return shipment != null && countMarkedPackages(packager, shipment) == request.count();
            }

            @Override
            public void rollback() {
                // The durable reservation owns any marked source cargo. Never clear
                // markers or restore fresh stock here; restart reconciliation must
                // decide whether the cargo remains at source or is already packaged.
            }
        };
    }

    @Override
    public boolean verifyDestinationReceipt(ServerLevel level, ShipmentJournalData.Shipment shipment,
                                            String destinationReference) {
        if (level == null || shipment == null || shipment.route() != DeliveryRoute.FROGPORT
                || destinationReference == null) return false;
        EndpointPair pair = EndpointPair.parse(shipment.sourceReference());
        if (pair == null || !destinationReference.equals(EndpointPair.format(pair.destination()))
                || !level.hasChunkAt(pair.destination())) return false;
        if (!(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)
                || !shipment.claimantId().equals(destination.getOwnerId())
                || !TrainShipmentMarker.hasOnlyMatchingStacks(destination, shipment)) return false;
        return TrainShipmentMarker.countMatchingIn(destination, shipment) == shipment.count();
    }

    private static ItemStack findMarkedSourceCargo(TerminalLoggerBlockEntity source, DeliveryRequest request) {
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            ItemStack stack = source.getInventory().getStackInSlot(slot);
            if (TrainShipmentMarker.matches(stack, request.transactionId())
                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId())) return stack;
        }
        return null;
    }

    private static int countMarkedSourceCargo(TerminalLoggerBlockEntity source, DeliveryRequest request) {
        int count = 0;
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            ItemStack stack = source.getInventory().getStackInSlot(slot);
            if (TrainShipmentMarker.matches(stack, request.transactionId())
                    && BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId())) count += stack.getCount();
        }
        return count;
    }

    private static PackagerLinkBlockEntity findStockLink(ServerLevel level, PackagerBlockEntity packager) {
        for (Direction side : Direction.values()) {
            BlockPos pos = packager.getBlockPos().relative(side);
            if (!level.hasChunkAt(pos)
                    || !(level.getBlockEntity(pos) instanceof PackagerLinkBlockEntity link)) continue;
            if (link.getPackager() == packager) return link;
        }
        return null;
    }

    private static boolean markNewestCreatedPackage(PackagerBlockEntity packager, DeliveryRequest request,
                                                      ItemStack previousHeldBox, int previousQueuedCount) {
        ItemStack created;
        boolean newHeldBox = packager.heldBox != previousHeldBox;
        if (newHeldBox && PackageItem.isPackage(packager.heldBox)) {
            created = packager.heldBox;
        } else if (packager.queuedExitingPackages.size() > previousQueuedCount) {
            created = packager.queuedExitingPackages.get(packager.queuedExitingPackages.size() - 1).stack;
        } else {
            return false;
        }
        if (!PackageItem.isPackage(created)) return false;
        ItemStackHandler contents = PackageItem.getContents(created);
        int marked = 0;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId()))
                continue;
            java.util.UUID existingTransaction = TrainShipmentMarker.transactionId(stack);
            if (request.transactionId().equals(existingTransaction)) {
                // Factory-sourced cargo is marked before it enters the source logger;
                // Create must preserve that exact marker inside the package.
                marked += stack.getCount();
                continue;
            }
            if (existingTransaction != null) continue;
            TrainShipmentMarker.mark(stack, request);
            contents.setStackInSlot(slot, stack);
            marked += stack.getCount();
        }
        if (marked != request.count()) return false;
        created.set(AllDataComponents.PACKAGE_CONTENTS, ItemHelper.containerContentsFromHandler(contents));
        if (created == packager.heldBox) packager.heldBox = created.copy();
        else {
            var queued = packager.queuedExitingPackages.get(packager.queuedExitingPackages.size() - 1);
            queued.stack = created.copy();
        }
        packager.setChanged();
        packager.notifyUpdate();
        return true;
    }

    private static int findEmptySlot(ItemStackHandler inventory, int except) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (slot != except && inventory.getStackInSlot(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static int countMarkedPackages(PackagerBlockEntity packager,
                                           ShipmentJournalData.Shipment shipment) {
        if (packager == null || shipment == null) return 0;
        int count = countMarkedInPackage(packager.heldBox, shipment);
        if (count >= shipment.count()) return count;
        for (BigItemStack queued : packager.queuedExitingPackages) {
            count += countMarkedInPackage(queued.stack, shipment);
            if (count >= shipment.count()) return count;
        }
        return count;
    }

    private static int countMarkedInPackage(ItemStack box, ShipmentJournalData.Shipment shipment) {
        if (!PackageItem.isPackage(box)) return 0;
        ItemStackHandler contents = PackageItem.getContents(box);
        int count = 0;
        for (int slot = 0; slot < contents.getSlots(); slot++) {
            ItemStack stack = contents.getStackInSlot(slot);
            if (TrainShipmentMarker.matches(stack, shipment)) count += stack.getCount();
        }
        return count;
    }

    private static PackagerBlockEntity findSourcePackager(ServerLevel level,
                                                           TerminalLoggerBlockEntity logger) {
        BlockPos pos = logger.getBlockPos().relative(logger.getOutputDirection());
        if (!level.hasChunkAt(pos)
                || !(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager)
                || !(level.getBlockEntity(pos.above()) instanceof FrogportBlockEntity)
                || packager.getBlockState().getValue(PackagerBlock.FACING) != logger.getOutputDirection()) return null;
        IItemHandler loggerFace = level.getCapability(Capabilities.ItemHandler.BLOCK,
                logger.getBlockPos(), logger.getOutputDirection());
        if (loggerFace == null || packager.targetInventory == null
                || packager.targetInventory.getInventory() != loggerFace) return null;
        return packager;
    }

    private static PackagerBlockEntity findDestinationPackager(ServerLevel level,
                                                                TerminalLoggerBlockEntity logger) {
        for (Direction side : Direction.values()) {
            if (!logger.isInputEnabled(side)) continue;
            BlockPos pos = logger.getBlockPos().relative(side);
            if (!level.hasChunkAt(pos)
                    || !(level.getBlockEntity(pos) instanceof PackagerBlockEntity packager)
                    || !(level.getBlockEntity(pos.above()) instanceof FrogportBlockEntity)
                    || packager.getBlockState().getValue(PackagerBlock.FACING) != side) continue;
            IItemHandler loggerFace = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    logger.getBlockPos(), side);
            if (loggerFace != null && packager.targetInventory != null
                    && packager.targetInventory.getInventory() == loggerFace) return packager;
        }
        return null;
    }

    private record EndpointPair(BlockPos source, BlockPos destination) {
        static EndpointPair parse(String value) {
            if (value == null || value.length() > 256) return null;
            String[] endpoints = value.split(">", -1);
            if (endpoints.length != 2) return null;
            BlockPos source = parsePos(endpoints[0]);
            BlockPos destination = parsePos(endpoints[1]);
            return source == null || destination == null ? null : new EndpointPair(source, destination);
        }
        static String format(BlockPos pos) {
            return pos.getX() + "," + pos.getY() + "," + pos.getZ();
        }
        private static BlockPos parsePos(String value) {
            String[] parts = value.split(",", -1);
            if (parts.length != 3) return null;
            try {
                return new BlockPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
    }
}
