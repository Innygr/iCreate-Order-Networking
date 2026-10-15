package com.innygr.icreateordernetworking.delivery;

import java.util.UUID;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.simibubi.create.content.contraptions.actors.psi.PortableStorageInterfaceBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Create 6.0.x / Minecraft 1.21.1 train adapter.
 *
 * The request source reference is "sourceX,sourceY,sourceZ>destinationX,destinationY,destinationZ".
 * Both endpoints must be owned by the claimant and connected through active Create PSIs.
 * Cargo is tagged on the source Terminal Logger, inserted into the active source PSI handler,
 * carried by the train, and only finalized when the destination logger receives the whole stack.
 */
public final class PortableStorageInterfaceTrainAdapter implements AutomatedDeliveryAdapter {
    @Override public DeliveryRoute route() { return DeliveryRoute.TRAIN; }

    @Override public boolean isAvailable() {
        return ModList.get().isLoaded("create");
    }

    @Override
    public boolean isAuthorized(ServerLevel level, DeliveryRequest request) {
        if (!isAvailable() || level == null || request == null
                || request.route() != DeliveryRoute.TRAIN || !request.isWellFormed()) return false;
        EndpointPair pair = EndpointPair.parse(request.sourceReference());
        if (pair == null || pair.source().equals(pair.destination())
                || !level.hasChunkAt(pair.source()) || !level.hasChunkAt(pair.destination())) return false;
        if (!(level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity source)
                || !(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)) return false;
        if (!request.actorId().equals(source.getOwnerId())
                || !request.actorId().equals(destination.getOwnerId())) return false;
        return sourceInterface(level, source) != null && destinationInterface(level, destination) != null;
    }

    @Override
    public PreparedDelivery prepareCargo(ServerLevel level, DeliveryRequest request) {
        if (!isAuthorized(level, request)) return null;
        EndpointPair pair = EndpointPair.parse(request.sourceReference());
        TerminalLoggerBlockEntity source = (TerminalLoggerBlockEntity) level.getBlockEntity(pair.source());
        if (request.count() > 64) return null; // One marked stack per shipment keeps receipt accounting exact.
        int sourceSlot = -1;
        int markerSlot = -1;
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            ItemStack stack = source.getInventory().getStackInSlot(slot);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId())
                    || TrainShipmentMarker.transactionId(stack) != null
                    || stack.getCount() < request.count()) continue;
            sourceSlot = slot;
            if (stack.getCount() == request.count()) {
                markerSlot = slot;
            } else {
                for (int target = 0; target < source.getInventory().getSlots(); target++) {
                    if (target != slot && source.getInventory().getStackInSlot(target).isEmpty()) {
                        markerSlot = target;
                        break;
                    }
                }
            }
            if (markerSlot >= 0) break;
        }
        if (sourceSlot < 0 || markerSlot < 0) return null;

        ItemStack reserved = source.getInventory().extractItem(sourceSlot, request.count(), false);
        if (reserved.isEmpty() || reserved.getCount() != request.count()) {
            if (!reserved.isEmpty()) source.getInventory().insertItem(sourceSlot, reserved, false);
            return null;
        }
        TrainShipmentMarker.mark(reserved, request);
        ItemStack remainder = source.getInventory().insertItem(markerSlot, reserved, false);
        if (!remainder.isEmpty()) {
            TrainShipmentMarker.clear(remainder);
            source.getInventory().insertItem(sourceSlot, remainder, false);
            return null;
        }
        return preparedFor(level, request, pair);
    }

    @Override
    public PreparedDelivery resumeCargo(ServerLevel level, ShipmentJournalData.Shipment shipment) {
        if (level == null || shipment == null || shipment.route() != DeliveryRoute.TRAIN
                || shipment.stage() != ShipmentJournalData.Stage.RESERVED || level.getServer() == null
                || !level.getServer().isSameThread()) return null;
        EndpointPair pair = EndpointPair.parse(shipment.sourceReference());
        if (pair == null || !level.hasChunkAt(pair.source()) || !level.hasChunkAt(pair.destination())) return null;
        if (!(level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity source)
                || !(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)
                || !shipment.claimantId().equals(source.getOwnerId())
                || !shipment.claimantId().equals(destination.getOwnerId())) return null;
        Order order = OrderData.get(level).find(shipment.orderId());
        if (order == null) return null;
        DeliveryRequest request = new DeliveryRequest(shipment.orderId(), order.itemId(), shipment.count(),
                DeliveryRoute.TRAIN, shipment.claimantId(), shipment.sourceReference(), shipment.transactionId());
        if (findMarkedSlot(source, shipment.transactionId()) < 0) return null;
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            if (TrainShipmentMarker.matches(source.getInventory().getStackInSlot(slot), shipment)) {
                return preparedFor(level, request, pair);
            }
        }
        return null;
    }

    private PreparedDelivery preparedFor(ServerLevel level, DeliveryRequest request, EndpointPair pair) {
        return new PreparedDelivery() {
            private boolean committed;

            @Override
            public boolean commit() {
                if (committed) return true;
                if (level.getServer() == null || !level.getServer().isSameThread()) return false;
                TerminalLoggerBlockEntity liveSource = level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity logger
                        ? logger : null;
                if (liveSource == null) return false;
                PortableStorageInterfaceBlockEntity psi = sourceInterface(level, liveSource);
                if (psi == null || !psi.canTransfer()) return false;
                IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, psi.getBlockPos(),
                        liveSource.getOutputDirection().getOpposite());
                if (target == null) return false;

                int markedSlot = findMarkedSlot(liveSource, request.transactionId());
                if (markedSlot < 0) return false;
                ItemStack marked = liveSource.getInventory().getStackInSlot(markedSlot);
                if (marked.getCount() != request.count()) return false;
                ItemStack simulated = marked.copy();
                for (int slot = 0; slot < target.getSlots() && !simulated.isEmpty(); slot++) {
                    simulated = target.insertItem(slot, simulated, true);
                }
                if (!simulated.isEmpty()) return false;

                ItemStack toSend = liveSource.getInventory().extractItem(markedSlot, request.count(), false);
                if (toSend.isEmpty() || !TrainShipmentMarker.matches(toSend, request.transactionId())) {
                    if (!toSend.isEmpty()) liveSource.getInventory().insertItem(markedSlot, toSend, false);
                    return false;
                }
                ItemStack remaining = toSend;
                for (int slot = 0; slot < target.getSlots() && !remaining.isEmpty(); slot++) {
                    remaining = target.insertItem(slot, remaining, false);
                }
                if (!remaining.isEmpty()) {
                    liveSource.getInventory().insertItem(markedSlot, remaining, false);
                    return false;
                }
                liveSource.recordTransfer(true, request.itemId(), request.count(),
                        "Train dispatch " + request.transactionId());
                committed = true;
                return true;
            }

            @Override
            public void rollback() {
                if (committed) return;
                TerminalLoggerBlockEntity liveSource = level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity logger
                        ? logger : null;
                if (liveSource == null) return;
                int slot = findMarkedSlot(liveSource, request.transactionId());
                if (slot < 0) return;
                ItemStack clean = liveSource.getInventory().getStackInSlot(slot).copy();
                TrainShipmentMarker.clear(clean);
                liveSource.getInventory().setStackInSlot(slot, clean);
            }
        };
    }

    @Override
    public boolean verifyDestinationReceipt(ServerLevel level, ShipmentJournalData.Shipment shipment,
                                            String destinationReference) {
        if (level == null || shipment == null || shipment.route() != DeliveryRoute.TRAIN
                || destinationReference == null) return false;
        EndpointPair pair = EndpointPair.parse(shipment.sourceReference());
        if (pair == null || !destinationReference.equals(EndpointPair.format(pair.destination()))
                || !level.hasChunkAt(pair.destination())) return false;
        if (!(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)
                || !shipment.claimantId().equals(destination.getOwnerId())) return false;
        if (TrainShipmentMarker.countIn(destination, shipment.transactionId()) < shipment.count()) return false;
        for (int slot = 0; slot < destination.getInventory().getSlots(); slot++) {
            if (TrainShipmentMarker.matches(destination.getInventory().getStackInSlot(slot), shipment)) return true;
        }
        return false;
    }

    private static int findMarkedSlot(TerminalLoggerBlockEntity logger, UUID transactionId) {
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            if (TrainShipmentMarker.matches(logger.getInventory().getStackInSlot(slot), transactionId)) return slot;
        }
        return -1;
    }

    private static PortableStorageInterfaceBlockEntity sourceInterface(ServerLevel level,
                                                                       TerminalLoggerBlockEntity logger) {
        BlockPos psiPos = logger.getBlockPos().relative(logger.getOutputDirection());
        if (!level.hasChunkAt(psiPos)
                || !(level.getBlockEntity(psiPos) instanceof PortableStorageInterfaceBlockEntity psi)
                || !psi.canTransfer()) return null;
        return psi;
    }

    private static PortableStorageInterfaceBlockEntity destinationInterface(ServerLevel level,
                                                                            TerminalLoggerBlockEntity logger) {
        for (Direction side : Direction.values()) {
            if (!logger.isInputEnabled(side)) continue;
            BlockPos psiPos = logger.getBlockPos().relative(side);
            if (!level.hasChunkAt(psiPos)) continue;
            if (level.getBlockEntity(psiPos) instanceof PortableStorageInterfaceBlockEntity psi) return psi;
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
