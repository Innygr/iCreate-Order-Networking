package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.compat.FTBTeamsCompat;
import com.innygr.icreateordernetworking.logistics.TerminalLoggerConnection;
import com.innygr.icreateordernetworking.logistics.TerminalLoggerConnectionDetector;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistent settings and diagnostics for the six-direction Terminal Logger.
 * The configured output is one of the six block faces; the other five faces
 * remain available for input/routing. UI and live inventory adapters are next.
 */
public final class TerminalLoggerBlockEntity extends BlockEntity {
    private Direction outputDirection = Direction.NORTH;
    private long acceptedDeliveries;
    private long rejectedDeliveries;
    private String lastStatus = "Idle";
    private ResourceLocation filterItemId;
    private UUID ownerId;
    private UUID ownerPartyTeamId;

    public TerminalLoggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL_LOGGER.get(), pos, state);
    }

    public Direction getOutputDirection() { return outputDirection; }
    public long getAcceptedDeliveries() { return acceptedDeliveries; }
    public long getRejectedDeliveries() { return rejectedDeliveries; }
    public String getLastStatus() { return lastStatus; }
    public ResourceLocation getFilterItemId() { return filterItemId; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getOwnerPartyTeamId() { return ownerPartyTeamId; }
    public boolean isConfigured() { return ownerId != null; }

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

    /** Brass variant only. Null clears the filter. */
    public boolean setFilterItemId(ResourceLocation itemId) {
        if (level != null && level.getBlockState(worldPosition).getBlock()
                instanceof TerminalLoggerBlock logger && !logger.supportsFiltering()) return false;
        if (itemId != null && !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(itemId)) {
            return false;
        }
        if (java.util.Objects.equals(filterItemId, itemId)) return true;
        filterItemId = itemId;
        setChanged();
        sync();
        return true;
    }

    /** True for all items on andesite; brass can restrict items to its selected filter. */
    public boolean acceptsItem(ResourceLocation itemId) {
        if (level != null && level.getBlockState(worldPosition).getBlock()
                instanceof TerminalLoggerBlock logger && !logger.supportsFiltering()) return true;
        return filterItemId == null || filterItemId.equals(itemId);
    }

    public void setOutputDirection(Direction direction) {
        if (direction == null || direction == outputDirection) return;
        outputDirection = direction;
        setChanged();
        sync();
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
    protected void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("OutputDirection", outputDirection.getName());
        tag.putLong("AcceptedDeliveries", acceptedDeliveries);
        tag.putLong("RejectedDeliveries", rejectedDeliveries);
        tag.putString("LastStatus", lastStatus);
        if (filterItemId != null) tag.putString("FilterItem", filterItemId.toString());
        if (ownerId != null) tag.putString("OwnerId", ownerId.toString());
        if (ownerPartyTeamId != null) tag.putString("OwnerPartyTeamId", ownerPartyTeamId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputDirection = Direction.byName(tag.getString("OutputDirection")) == null
                ? Direction.NORTH : Direction.byName(tag.getString("OutputDirection"));
        acceptedDeliveries = Math.max(0, tag.getLong("AcceptedDeliveries"));
        rejectedDeliveries = Math.max(0, tag.getLong("RejectedDeliveries"));
        lastStatus = safeStatus(tag.getString("LastStatus"));
        ownerId = parseUuid(tag, "OwnerId");
        ownerPartyTeamId = parseUuid(tag, "OwnerPartyTeamId");
        filterItemId = null;
        if (tag.contains("FilterItem")) {
            try {
                ResourceLocation candidate = ResourceLocation.parse(tag.getString("FilterItem"));
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(candidate)) {
                    filterItemId = candidate;
                }
            } catch (IllegalArgumentException ignored) {
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
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }
}
