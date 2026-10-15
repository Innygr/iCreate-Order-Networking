package com.innygr.icreateordernetworking.delivery;

import java.util.UUID;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.compat.AeronauticsDockingCompat;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Optional Create: Aeronautics adapter using Docking Connector item-handler capabilities.
 *
 * The request source reference is "sourceX,sourceY,sourceZ>destinationX,destinationY,destinationZ".
 * Both Terminal Logger endpoints must be owned by the claimant and adjacent to registered Docking Connectors.
 * Dispatch only proceeds when the source connector exposes a live item-handler capability; receipt is
 * finalized only after the destination logger receives the complete marked cargo.
 */
public final class AeronauticsDockingConnectorAdapter implements AutomatedDeliveryAdapter {
    @Override public DeliveryRoute route() { return DeliveryRoute.AIRSHIP; }

    @Override public boolean isAvailable() {
        return AeronauticsDockingCompat.isDockingConnectorAvailable();
    }

    @Override
    public boolean isAuthorized(ServerLevel level, DeliveryRequest request) {
        if (!isAvailable() || level == null || request == null
                || request.route() != DeliveryRoute.AIRSHIP || !request.isWellFormed()) return false;
        EndpointPair pair = EndpointPair.parse(request.sourceReference());
        if (pair == null || pair.source().equals(pair.destination())
                || !level.hasChunkAt(pair.source()) || !level.hasChunkAt(pair.destination())) return false;
        if (!(level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity source)
                || !(level.getBlockEntity(pair.destination()) instanceof TerminalLoggerBlockEntity destination)) return false;
        if (!request.actorId().equals(source.getOwnerId())
                || !request.actorId().equals(destination.getOwnerId())) return false;
        return sourceConnector(level, source) != null && destinationConnector(level, destination) != null;
    }

    @Override
    public PreparedDelivery prepareCargo(ServerLevel level, DeliveryRequest request) {
        if (!isAuthorized(level, request)) return null;
        EndpointPair pair = EndpointPair.parse(request.sourceReference());
        TerminalLoggerBlockEntity source = (TerminalLoggerBlockEntity) level.getBlockEntity(pair.source());
        // Reserve the requested quantity across multiple source stacks. Each stack
        // carries the same transaction marker and the total requested count.
        java.util.List<StackAllocation> allocations = new java.util.ArrayList<>();
        int remaining = request.count();
        for (int slot = 0; slot < source.getInventory().getSlots() && remaining > 0; slot++) {
            ItemStack stack = source.getInventory().getStackInSlot(slot);
            if (stack.isEmpty() || !BuiltInRegistries.ITEM.getKey(stack.getItem()).equals(request.itemId())
                    || TrainShipmentMarker.transactionId(stack) != null) continue;
            int amount = Math.min(remaining, stack.getCount());
            allocations.add(new StackAllocation(slot, amount, stack.getCount()));
            remaining -= amount;
        }
        if (remaining != 0) return null;

        // Partial extraction needs a separate empty slot for the marked piece.
        // Keep those target slots distinct from every original source slot; using a
        // slot that another allocation will refill would overwrite reserved cargo.
        java.util.List<Integer> availableTargets = new java.util.ArrayList<>();
        for (int slot = 0; slot < source.getInventory().getSlots(); slot++) {
            if (source.getInventory().getStackInSlot(slot).isEmpty()) availableTargets.add(slot);
        }
        java.util.List<StackAllocation> finalized = new java.util.ArrayList<>();
        for (StackAllocation allocation : allocations) {
            int targetSlot = allocation.sourceSlot();
            if (allocation.amount() != allocation.originalCount()) {
                targetSlot = availableTargets.stream().filter(slot -> slot != allocation.sourceSlot())
                        .findFirst().orElse(-1);
                if (targetSlot < 0) return null;
            }
            availableTargets.remove(Integer.valueOf(targetSlot));
            finalized.add(new StackAllocation(allocation.sourceSlot(), targetSlot,
                    allocation.amount(), allocation.originalCount()));
        }

        java.util.List<ItemStack> reservedStacks = new java.util.ArrayList<>();
        for (StackAllocation allocation : finalized) {
            ItemStack extracted = source.getInventory().extractItem(allocation.sourceSlot(), allocation.amount(), false);
            if (extracted.isEmpty() || extracted.getCount() != allocation.amount()) {
                if (!extracted.isEmpty()) {
                    TrainShipmentMarker.clear(extracted);
                    ItemStack remainderStack = source.getInventory().insertItem(allocation.sourceSlot(), extracted, false);
                    if (!remainderStack.isEmpty()) source.getInventory().insertItem(allocation.targetSlot(), remainderStack, false);
                }
                restoreReservation(source, finalized, reservedStacks);
                return null;
            }
            TrainShipmentMarker.mark(extracted, request);
            reservedStacks.add(extracted);
        }
        for (int i = 0; i < finalized.size(); i++) {
            int targetSlot = finalized.get(i).targetSlot();
            if (!source.getInventory().getStackInSlot(targetSlot).isEmpty()) {
                restoreReservation(source, finalized, reservedStacks);
                return null;
            }
            source.getInventory().setStackInSlot(targetSlot, reservedStacks.get(i));
        }
        return preparedFor(level, request, pair);
    }

    @Override
    public PreparedDelivery resumeCargo(ServerLevel level, ShipmentJournalData.Shipment shipment) {
        if (level == null || shipment == null || shipment.route() != DeliveryRoute.AIRSHIP
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
                DeliveryRoute.AIRSHIP, shipment.claimantId(), shipment.sourceReference(), shipment.transactionId());
        java.util.List<Integer> markedSlots = findMarkedSlots(source, shipment.transactionId());
        if (markedSlots.isEmpty()) return null;
        int markedCount = markedSlots.stream()
                .mapToInt(slot -> source.getInventory().getStackInSlot(slot).getCount()).sum();
        return markedCount == shipment.count() ? preparedFor(level, request, pair) : null;
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
                BlockPos connectorPos = sourceConnector(level, liveSource);
                if (connectorPos == null) return false;
                IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, connectorPos,
                        liveSource.getOutputDirection().getOpposite());
                if (target == null) return false;

                java.util.List<Integer> markedSlots = findMarkedSlots(liveSource, request.transactionId());
                if (markedSlots.isEmpty()) return false;
                int markedCount = markedSlots.stream()
                        .mapToInt(slot -> liveSource.getInventory().getStackInSlot(slot).getCount()).sum();
                if (markedCount != request.count()) return false;

                // Simulate the complete multi-stack transfer before mutating either endpoint.
                java.util.List<ItemStack> virtualSlots = new java.util.ArrayList<>();
                for (int slot = 0; slot < target.getSlots(); slot++) {
                    virtualSlots.add(target.getStackInSlot(slot).copy());
                }
                for (int sourceSlot : markedSlots) {
                    ItemStack cargo = liveSource.getInventory().getStackInSlot(sourceSlot).copy();
                    for (int slot = 0; slot < target.getSlots() && !cargo.isEmpty(); slot++) {
                        ItemStack current = virtualSlots.get(slot);
                        if (!current.isEmpty()) {
                            if (!ItemStack.isSameItemSameComponents(current, cargo)) continue;
                            int room = Math.min(current.getMaxStackSize(), target.getSlotLimit(slot)) - current.getCount();
                            int moved = Math.min(Math.max(0, room), cargo.getCount());
                            if (moved > 0) {
                                current.grow(moved);
                                cargo.shrink(moved);
                            }
                        } else if (target.isItemValid(slot, cargo)) {
                            int moved = Math.min(cargo.getCount(), Math.min(cargo.getMaxStackSize(), target.getSlotLimit(slot)));
                            if (moved > 0) {
                                virtualSlots.set(slot, cargo.copyWithCount(moved));
                                cargo.shrink(moved);
                            }
                        }
                    }
                    if (!cargo.isEmpty()) return false;
                }

                java.util.List<ItemStack> cargoStacks = new java.util.ArrayList<>();
                for (int sourceSlot : markedSlots) {
                    ItemStack extracted = liveSource.getInventory().extractItem(sourceSlot, Integer.MAX_VALUE, false);
                    if (extracted.isEmpty() || !TrainShipmentMarker.matches(extracted, request.transactionId())) {
                        if (!extracted.isEmpty()) liveSource.getInventory().insertItem(sourceSlot, extracted, false);
                        restoreCargoToSource(liveSource, markedSlots, cargoStacks);
                        return false;
                    }
                    cargoStacks.add(extracted);
                }

                java.util.Map<Integer, Integer> insertedBySlot = new java.util.LinkedHashMap<>();
                boolean transferComplete = true;
                for (ItemStack stack : cargoStacks) {
                    ItemStack remainder = stack;
                    for (int slot = 0; slot < target.getSlots() && !remainder.isEmpty(); slot++) {
                        int before = target.getStackInSlot(slot).getCount();
                        remainder = target.insertItem(slot, remainder, false);
                        int after = target.getStackInSlot(slot).getCount();
                        if (after > before) insertedBySlot.merge(slot, after - before, Integer::sum);
                    }
                    if (!remainder.isEmpty()) {
                        transferComplete = false;
                        break;
                    }
                }
                if (!transferComplete) {
                    insertedBySlot.forEach((slot, count) -> target.extractItem(slot, count, false));
                    restoreCargoToSource(liveSource, markedSlots, cargoStacks);
                    return false;
                }
                liveSource.recordTransfer(true, request.itemId(), request.count(),
                        "Airship dispatch " + request.transactionId());
                committed = true;
                return true;
            }

            @Override
            public void rollback() {
                if (committed) return;
                TerminalLoggerBlockEntity liveSource = level.getBlockEntity(pair.source()) instanceof TerminalLoggerBlockEntity logger
                        ? logger : null;
                if (liveSource == null) return;
                TrainShipmentMarker.clearTransaction(liveSource, request.transactionId());
            }
        };
    }

    @Override
    public boolean verifyDestinationReceipt(ServerLevel level, ShipmentJournalData.Shipment shipment,
                                            String destinationReference) {
        if (level == null || shipment == null || shipment.route() != DeliveryRoute.AIRSHIP
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
        java.util.List<Integer> slots = findMarkedSlots(logger, transactionId);
        return slots.isEmpty() ? -1 : slots.get(0);
    }

    private static java.util.List<Integer> findMarkedSlots(TerminalLoggerBlockEntity logger, UUID transactionId) {
        java.util.List<Integer> slots = new java.util.ArrayList<>();
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            if (TrainShipmentMarker.matches(logger.getInventory().getStackInSlot(slot), transactionId)) slots.add(slot);
        }
        return slots;
    }

    private static void restoreReservation(TerminalLoggerBlockEntity source,
            java.util.List<StackAllocation> allocations, java.util.List<ItemStack> extracted) {
        for (int i = 0; i < extracted.size(); i++) {
            ItemStack stack = extracted.get(i).copy();
            TrainShipmentMarker.clear(stack);
            int preferred = allocations.get(i).sourceSlot();
            if (source.getInventory().getStackInSlot(preferred).isEmpty()) {
                source.getInventory().setStackInSlot(preferred, stack);
            } else {
                ItemStack remainder = source.getInventory().insertItem(preferred, stack, false);
                for (int slot = 0; slot < source.getInventory().getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = source.getInventory().insertItem(slot, remainder, false);
                }
            }
        }
    }

    private static void restoreCargoToSource(TerminalLoggerBlockEntity source,
            java.util.List<Integer> sourceSlots, java.util.List<ItemStack> cargoStacks) {
        for (int i = 0; i < cargoStacks.size(); i++) {
            ItemStack stack = cargoStacks.get(i);
            if (stack.isEmpty()) continue;
            int preferred = sourceSlots.get(i);
            if (source.getInventory().getStackInSlot(preferred).isEmpty()) {
                source.getInventory().setStackInSlot(preferred, stack);
            } else {
                ItemStack remainder = source.getInventory().insertItem(preferred, stack, false);
                for (int slot = 0; slot < source.getInventory().getSlots() && !remainder.isEmpty(); slot++) {
                    remainder = source.getInventory().insertItem(slot, remainder, false);
                }
            }
        }
    }

    private record StackAllocation(int sourceSlot, int targetSlot, int amount, int originalCount) {
        StackAllocation(int sourceSlot, int amount, int originalCount) {
            this(sourceSlot, -1, amount, originalCount);
        }
    }

    private static BlockPos sourceConnector(ServerLevel level, TerminalLoggerBlockEntity logger) {
        BlockPos connector = logger.getBlockPos().relative(logger.getOutputDirection());
        if (!isDockingConnector(level, connector)) return null;
        return level.getCapability(Capabilities.ItemHandler.BLOCK, connector,
                logger.getOutputDirection().getOpposite()) == null ? null : connector;
    }

    private static BlockPos destinationConnector(ServerLevel level, TerminalLoggerBlockEntity logger) {
        for (Direction side : Direction.values()) {
            if (!logger.isInputEnabled(side)) continue;
            BlockPos connector = logger.getBlockPos().relative(side);
            if (!isDockingConnector(level, connector)) continue;
            if (level.getCapability(Capabilities.ItemHandler.BLOCK, connector, side.getOpposite()) != null) {
                return connector;
            }
        }
        return null;
    }

    private static boolean isDockingConnector(ServerLevel level, BlockPos pos) {
        return level != null && level.hasChunkAt(pos)
                && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())
                        .equals(AeronauticsDockingCompat.dockingConnectorId());
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
