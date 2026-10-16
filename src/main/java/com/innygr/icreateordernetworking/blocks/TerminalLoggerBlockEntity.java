package com.innygr.icreateordernetworking.blocks;

import java.util.EnumMap;
import java.util.Optional;
import java.util.UUID;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.delivery.TrainShipmentMarker;
import com.innygr.icreateordernetworking.delivery.AutomatedDeliveryService;
import com.innygr.icreateordernetworking.compat.FTBTeamsCompat;
import com.innygr.icreateordernetworking.logistics.TerminalLoggerConnection;
import com.innygr.icreateordernetworking.logistics.TerminalLoggerConnectionDetector;
import com.innygr.icreateordernetworking.logistics.NetworkStatsData;
import com.innygr.icreateordernetworking.terminal.TerminalLoggerConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Six-way inventory junction. Five faces pull from adjacent NeoForge item-handler
 * capabilities; the configured face is output-only. Create's Portable Storage
 * Interface exposes its own item handler only during its native transfer state, so
 * this junction can exchange items through that capability without duplicating the
 * PSI handshake. Aeronautics docking lifecycle and route-specific order receipts
 * remain separate integration work.
 */
public final class TerminalLoggerBlockEntity extends BlockEntity {
    private static final int SLOT_COUNT = 18;
    private static final int TRANSFER_PER_TICK = 4;

    private final TerminalLoggerConfig routingConfig = new TerminalLoggerConfig();
    private long acceptedDeliveries;
    private long rejectedDeliveries;
    private String lastStatus = "Idle";
    private ResourceLocation filterItemId;
    private UUID ownerId;
    private UUID ownerPartyTeamId;
    private UUID factoryId;
    private UUID factoryPartyTeamId;

    private final ItemStackHandler inventory = new ItemStackHandler(SLOT_COUNT) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
            sync();
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return acceptsItem(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()));
        }
    };

    private final EnumMap<Direction, IItemHandler> sidedHandlers = new EnumMap<>(Direction.class);

    public TerminalLoggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL_LOGGER.get(), pos, state);
        routingConfig.setOutput(Direction.NORTH);
        for (Direction side : Direction.values()) {
            sidedHandlers.put(side, new IItemHandler() {
                @Override
                public int getSlots() {
                    return inventory.getSlots();
                }

                @Override
                public ItemStack getStackInSlot(int slot) {
                    return inventory.getStackInSlot(slot);
                }

                @Override
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (!routingConfig.isInputEnabled(side) || stack.isEmpty() || !acceptsItem(
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                        return stack;
                    }
                    return inventory.insertItem(slot, stack, simulate);
                }

                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (side != routingConfig.output()) return ItemStack.EMPTY;
                    // Cargo reserved for a durable shipment is locked to its route
                    // adapter. A belt, funnel, or other capability consumer must not
                    // be able to steal it from the logger while dispatch is pending.
                    if (TrainShipmentMarker.transactionId(inventory.getStackInSlot(slot)) != null) {
                        // A Packager directly attached to the configured output face
                        // is the one permitted consumer for Frogport-bound reserved
                        // cargo. Other pipes, belts, and automation still cannot steal it.
                        BlockEntity adjacent = level == null ? null : level.getBlockEntity(worldPosition.relative(side));
                        if (!(adjacent instanceof com.simibubi.create.content.logistics.packager.PackagerBlockEntity packager)
                                || packager.targetInventory == null
                                || packager.targetInventory.getInventory() == null) {
                            return ItemStack.EMPTY;
                        }
                    }
                    return inventory.extractItem(slot, amount, simulate);
                }

                @Override
                public int getSlotLimit(int slot) {
                    return inventory.getSlotLimit(slot);
                }

                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return routingConfig.isInputEnabled(side) && inventory.isItemValid(slot, stack);
                }
            });
        }
    }

    /**
     * Sided automation view: five faces accept filtered items and only the
     * selected output face allows extraction. Null-side access is deliberately
     * not exposed to avoid bypassing the face configuration.
     */
    public IItemHandler getSidedItemHandler(Direction side) {
        return side == null ? null : sidedHandlers.get(side);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerminalLoggerBlockEntity logger) {
        if (level.isClientSide || level.getGameTime() % 4 != 0) return;
        logger.pullInputs(level);
        logger.pushOutput(level);
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            AutomatedDeliveryService.resumeReservedAtSource(serverLevel, pos);
            TrainShipmentMarker.tryConfirmAtDestination(serverLevel, logger);
        }
    }

    private void pullInputs(Level level) {
        for (Direction side : Direction.values()) {
            if (!routingConfig.isInputEnabled(side)) continue;
            BlockPos sourcePos = worldPosition.relative(side);
            IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, side.getOpposite());
            if (source == null) continue;

            for (int sourceSlot = 0; sourceSlot < source.getSlots(); sourceSlot++) {
                ItemStack offered = source.extractItem(sourceSlot, TRANSFER_PER_TICK, true);
                if (offered.isEmpty() || !acceptsItem(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offered.getItem()))) continue;

                // First simulate insertion to learn how much buffer space exists.
                ItemStack simulatedRemainder = offered.copy();
                for (int targetSlot = 0; targetSlot < inventory.getSlots() && !simulatedRemainder.isEmpty(); targetSlot++) {
                    simulatedRemainder = inventory.insertItem(targetSlot, simulatedRemainder, true);
                }
                int wanted = offered.getCount() - simulatedRemainder.getCount();
                if (wanted <= 0) continue;

                // Extract first, then insert only what the source actually gave us.
                // This ordering prevents duplication if an endpoint changes state
                // between simulation and extraction (e.g. a docking interface closes).
                ItemStack extracted = source.extractItem(sourceSlot, wanted, false);
                if (extracted.isEmpty()) continue;
                ItemStack remainder = extracted;
                for (int targetSlot = 0; targetSlot < inventory.getSlots() && !remainder.isEmpty(); targetSlot++) {
                    remainder = inventory.insertItem(targetSlot, remainder, false);
                }

                // The simulation and real insertion should agree on the server
                // thread; if an unusual handler rejects part of it, return the
                // remainder to the source instead of voiding it.
                if (!remainder.isEmpty()) {
                    ItemStack toReturn = remainder;
                    for (int sourceTarget = 0; sourceTarget < source.getSlots() && !toReturn.isEmpty(); sourceTarget++) {
                        toReturn = source.insertItem(sourceTarget, toReturn, false);
                    }
                    if (!toReturn.isEmpty() && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        net.minecraft.world.Containers.dropItemStack(serverLevel,
                                worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, toReturn);
                    }
                }
                int accepted = extracted.getCount() - remainder.getCount();
                if (accepted > 0) {
                    ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(extracted.getItem());
                    recordAccepted("Pulled " + accepted + "x " + itemId + " from " + side.getName());
                    recordNetworkTransfer(level, itemId, NetworkStatsData.EventType.INPUT, accepted,
                            "Input from " + side.getName());
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        TrainShipmentMarker.tryConfirmAtDestination(serverLevel, this);
                    }
                    return;
                }
            }
        }
    }

    private void pushOutput(Level level) {
        BlockPos targetPos = worldPosition.relative(routingConfig.output());
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, routingConfig.output().getOpposite());
        if (target == null) return;

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            // Tagged train cargo must only leave through the adapter's transaction-aware PSI handoff.
            if (stored.isEmpty() || TrainShipmentMarker.transactionId(stored) != null) continue;
            for (int targetSlot = 0; targetSlot < target.getSlots(); targetSlot++) {
                ItemStack remainder = target.insertItem(targetSlot, stored.copyWithCount(
                        Math.min(TRANSFER_PER_TICK, stored.getCount())), false);
                int moved = Math.min(TRANSFER_PER_TICK, stored.getCount()) - remainder.getCount();
                if (moved > 0) {
                    inventory.extractItem(slot, moved, false);
                    ResourceLocation itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stored.getItem());
                    recordAccepted("Output " + moved + "x " + itemId);
                    recordNetworkTransfer(level, itemId, NetworkStatsData.EventType.OUTPUT, moved,
                            "Output to " + routingConfig.output().getName());
                    return;
                }
            }
        }
    }

    /**
     * Returns this owner's network statistics only when the requester has access
     * to this logger. A null result means access is denied or the logger is not
     * attached to a server world.
     */
    public NetworkStatsData.Snapshot getNetworkStats(UUID requesterId) {
        if (level == null || level.isClientSide || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)
                || !hasAccess(requesterId) || ownerId == null) return null;
        return NetworkStatsData.get(serverLevel).snapshot(ownerId);
    }

    private void recordNetworkTransfer(Level level, ResourceLocation itemId,
                                       NetworkStatsData.EventType type, int count, String detail) {
        if (ownerId == null || !(level instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
        UUID currentOwnerTeam = ownerPartyTeamId != null
                && ownerPartyTeamId.equals(FTBTeamsCompat.partyTeamId(ownerId).orElse(null))
                ? ownerPartyTeamId : null;
        UUID statsTeamId = factoryPartyTeamId != null ? factoryPartyTeamId : currentOwnerTeam;
        NetworkStatsData.get(serverLevel).record(serverLevel, ownerId, factoryId, statsTeamId,
                worldPosition, itemId, type, count, detail);
    }

    public ItemStackHandler getInventory() { return inventory; }
    public Direction getOutputDirection() { return routingConfig.output(); }
    public boolean isInputEnabled(Direction side) { return routingConfig.isInputEnabled(side); }
    public long getAcceptedDeliveries() { return acceptedDeliveries; }
    public long getRejectedDeliveries() { return rejectedDeliveries; }
    public String getLastStatus() { return lastStatus; }
    public ResourceLocation getFilterItemId() { return filterItemId; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getOwnerPartyTeamId() { return ownerPartyTeamId; }
    public UUID getFactoryId() { return factoryId; }
    public UUID getFactoryPartyTeamId() { return factoryPartyTeamId; }
    public boolean isConfigured() { return ownerId != null; }

    /**
     * Bind this logger to a stable factory identity. Intended for a trusted
     * Order Terminal/factory interaction after the factory owner is authorized.
     */
    public boolean bindFactory(UUID requesterId, UUID selectedFactoryId,
                               UUID selectedFactoryOwnerId, UUID selectedFactoryTeamId) {
        if (level == null || level.isClientSide || selectedFactoryId == null
                || selectedFactoryOwnerId == null || !hasAccess(requesterId)) return false;
        boolean personalFactoryOwner = selectedFactoryTeamId == null
                && selectedFactoryOwnerId.equals(requesterId)
                && ownerId != null && ownerId.equals(requesterId)
                && ownerPartyTeamId == null;
        boolean sameLiveParty = selectedFactoryTeamId != null
                && selectedFactoryTeamId.equals(ownerPartyTeamId)
                && selectedFactoryTeamId.equals(FTBTeamsCompat.partyTeamId(requesterId).orElse(null))
                && selectedFactoryTeamId.equals(FTBTeamsCompat.partyTeamId(selectedFactoryOwnerId).orElse(null));
        if (!personalFactoryOwner && !sameLiveParty) return false;
        factoryId = selectedFactoryId;
        factoryPartyTeamId = selectedFactoryTeamId;
        setChanged();
        sync();
        return true;
    }

    /**
     * Atomically claims an unconfigured logger for the first interacting player.
     * Minecraft invokes block interactions on the server thread, so a second
     * player cannot overwrite a successful first-use binding.
     */
    public boolean bindOwnerIfUnconfigured(UUID playerId) {
        if (level == null || level.isClientSide || playerId == null || ownerId != null) return false;
        ownerId = playerId;
        ownerPartyTeamId = FTBTeamsCompat.partyTeamId(playerId).orElse(null);
        setChanged();
        sync();
        return true;
    }

    /**
     * Owner access is permanent for this placed block. Teammate access is based
     * on live membership, not a cached roster, and is only granted to the party
     * team that owned the logger at first use.
     */
    public boolean hasAccess(UUID playerId) {
        if (playerId == null || ownerId == null) return false;
        if (ownerId.equals(playerId)) return true;
        if (ownerPartyTeamId == null) return false;
        // Team sharing pauses if the owner leaves or changes parties. The owner
        // keeps access and can restore sharing by rejoining the bound party.
        Optional<UUID> currentOwnerTeam = FTBTeamsCompat.partyTeamId(ownerId);
        if (currentOwnerTeam.isEmpty() || !ownerPartyTeamId.equals(currentOwnerTeam.get())) {
            return false;
        }
        Optional<UUID> currentTeam = FTBTeamsCompat.partyTeamId(playerId);
        return currentTeam.isPresent() && ownerPartyTeamId.equals(currentTeam.get());
    }

    /**
     * Read-only adjacent endpoint discovery for diagnostics. An empty list means
     * no candidate endpoint was found or this block entity is not on the server.
     */
    public java.util.List<TerminalLoggerConnection> getDetectedConnections() {
        return TerminalLoggerConnectionDetector.detect(this);
    }

    public boolean canInputFrom(Direction side, ResourceLocation itemId) {
        return side != null && routingConfig.isInputEnabled(side) && acceptsItem(itemId);
    }

    public boolean canOutputTo(Direction side) { return side != null && side == routingConfig.output(); }

    /** Records diagnostics after an observed transfer; does not itself fulfil an order. */
    public void recordTransfer(boolean accepted, ResourceLocation itemId, int count, String reason) {
        if (level == null || level.isClientSide || count < 1 || !acceptsItem(itemId)) {
            if (level != null && !level.isClientSide) {
                recordRejected("Rejected transfer: invalid item/filter/count");
                // Keep filter rejections visible in Network Stats when the attempted
                // item and quantity are valid enough to identify a real transfer attempt.
                // Invalid/null item IDs and non-positive quantities cannot form a useful event.
                if (itemId != null && count > 0) {
                    recordNetworkTransfer(level, itemId, NetworkStatsData.EventType.REJECTED,
                            count, "Rejected transfer: invalid item/filter/count");
                }
            }
            return;
        }
        String detail = (reason == null || reason.isBlank() ? "Transfer" : reason)
                + " " + count + "x " + itemId;
        if (accepted) {
            recordAccepted(detail);
        } else {
            recordRejected(detail);
            recordNetworkTransfer(level, itemId, NetworkStatsData.EventType.REJECTED, count,
                    reason == null || reason.isBlank() ? "Rejected transfer" : reason);
        }
    }

    public boolean setFilterItemId(ResourceLocation itemId) {
        if (level != null && level.getBlockState(worldPosition).getBlock()
                instanceof TerminalLoggerBlock logger && !logger.supportsFiltering()) return false;
        if (itemId != null && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) return false;
        if (java.util.Objects.equals(filterItemId, itemId)) return true;
        filterItemId = itemId;
        setChanged();
        sync();
        return true;
    }

    public boolean acceptsItem(ResourceLocation itemId) {
        if (itemId == null) return false;
        if (level != null && level.getBlockState(worldPosition).getBlock()
                instanceof TerminalLoggerBlock logger && !logger.supportsFiltering()) return true;
        return filterItemId == null || filterItemId.equals(itemId);
    }

    public void setOutputDirection(Direction direction) {
        if (direction == null || direction == routingConfig.output()) return;
        routingConfig.setOutput(direction);
        setChanged();
        sync();
    }

    /** Enable or disable a physical input face; the output face is always excluded. */
    public boolean setInputEnabled(Direction side, boolean enabled) {
        if (side == null || (side == routingConfig.output() && enabled)) return false;
        if (routingConfig.isInputEnabled(side) == enabled) return true;
        routingConfig.setInputEnabled(side, enabled);
        setChanged();
        sync();
        return true;
    }

    public void recordAccepted(String message) {
        acceptedDeliveries++;
        lastStatus = safeStatus(message);
        setChanged();
        sync();
    }

    public void recordRejected(String message) {
        rejectedDeliveries++;
        lastStatus = safeStatus(message);
        setChanged();
        sync();
    }

    private static String safeStatus(String message) {
        if (message == null || message.isBlank()) return "No details";
        return message.length() > 120 ? message.substring(0, 120) : message;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("OutputDirection", routingConfig.output().getName());
        tag.put("RoutingConfig", routingConfig.save());
        tag.putLong("AcceptedDeliveries", acceptedDeliveries);
        tag.putLong("RejectedDeliveries", rejectedDeliveries);
        tag.putString("LastStatus", lastStatus);
        tag.put("Inventory", inventory.serializeNBT(registries));
        if (filterItemId != null) tag.putString("FilterItem", filterItemId.toString());
        if (ownerId != null) tag.putString("OwnerId", ownerId.toString());
        if (ownerPartyTeamId != null) tag.putString("OwnerPartyTeamId", ownerPartyTeamId.toString());
        if (factoryId != null) tag.putString("FactoryId", factoryId.toString());
        if (factoryPartyTeamId != null) tag.putString("FactoryPartyTeamId", factoryPartyTeamId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Direction loaded = Direction.byName(tag.getString("OutputDirection"));
        routingConfig.setOutput(loaded == null ? Direction.NORTH : loaded);
        if (tag.contains("RoutingConfig", net.minecraft.nbt.Tag.TAG_COMPOUND)) {
            routingConfig.load(tag.getCompound("RoutingConfig"));
        }
        acceptedDeliveries = Math.max(0, tag.getLong("AcceptedDeliveries"));
        rejectedDeliveries = Math.max(0, tag.getLong("RejectedDeliveries"));
        lastStatus = safeStatus(tag.getString("LastStatus"));
        ownerId = parseUuid(tag, "OwnerId");
        ownerPartyTeamId = parseUuid(tag, "OwnerPartyTeamId");
        factoryId = parseUuid(tag, "FactoryId");
        factoryPartyTeamId = parseUuid(tag, "FactoryPartyTeamId");
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        filterItemId = null;
        if (tag.contains("FilterItem")) {
            try {
                ResourceLocation candidate = ResourceLocation.parse(tag.getString("FilterItem"));
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(candidate)) filterItemId = candidate;
            } catch (RuntimeException ignored) {
                // Invalid resource identifiers should not prevent the block entity from loading.
                filterItemId = null;
            }
        }
    }

    private static UUID parseUuid(CompoundTag tag, String key) {
        if (!tag.contains(key)) return null;
        try {
            return UUID.fromString(tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
