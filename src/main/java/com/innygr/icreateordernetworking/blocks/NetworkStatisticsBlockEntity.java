package com.innygr.icreateordernetworking.blocks;

import java.util.Optional;
import java.util.UUID;

import com.innygr.icreateordernetworking.compat.FTBTeamsCompat;
import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.logistics.NetworkStatsData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent configuration for a factory-scoped Network Statistics display. */
public final class NetworkStatisticsBlockEntity extends BlockEntity {
    public enum Mode { FACTORY, TEAM_GLOBAL }

    private UUID ownerId;
    private UUID ownerPartyTeamId;
    private UUID factoryId;
    private UUID factoryOwnerId;
    private UUID factoryPartyTeamId;
    private Mode mode = Mode.FACTORY;

    public NetworkStatisticsBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.NETWORK_STATISTICS.get(), pos, state);
    }

    public boolean claimIfUnconfigured(UUID playerId) {
        if (level == null || level.isClientSide || playerId == null || ownerId != null) return false;
        ownerId = playerId;
        ownerPartyTeamId = FTBTeamsCompat.partyTeamId(playerId).orElse(null);
        setChanged();
        sync();
        return true;
    }

    public boolean hasAccess(UUID playerId) {
        if (playerId == null || ownerId == null) return false;
        if (ownerId.equals(playerId)) return true;
        if (ownerPartyTeamId == null) return false;
        Optional<UUID> ownerTeam = FTBTeamsCompat.partyTeamId(ownerId);
        Optional<UUID> requesterTeam = FTBTeamsCompat.partyTeamId(playerId);
        return ownerTeam.filter(ownerPartyTeamId::equals).isPresent()
                && requesterTeam.filter(ownerPartyTeamId::equals).isPresent();
    }

    /** Switch to a team-wide view only for the currently bound FTB party. */
    public boolean setTeamGlobal(UUID requesterId) {
        if (level == null || level.isClientSide || !hasAccess(requesterId)
                || ownerPartyTeamId == null
                || !ownerPartyTeamId.equals(FTBTeamsCompat.partyTeamId(ownerId).orElse(null))) {
            return false;
        }
        mode = Mode.TEAM_GLOBAL;
        setChanged();
        sync();
        return true;
    }

    public boolean setFactoryMode(UUID requesterId) {
        if (level == null || level.isClientSide || !hasAccess(requesterId)) return false;
        mode = Mode.FACTORY;
        setChanged();
        sync();
        return true;
    }

    /**
     * Server-side hook for the Order Terminal interaction. The caller must
     * authorize the player against the selected factory before invoking this.
     */
    public boolean bindFactory(UUID requesterId, UUID selectedFactoryId,
                               UUID selectedFactoryOwnerId, UUID selectedFactoryTeamId) {
        if (level == null || level.isClientSide || selectedFactoryId == null
                || selectedFactoryOwnerId == null || !hasAccess(requesterId)) return false;

        boolean isFactoryOwner = selectedFactoryOwnerId.equals(requesterId);
        boolean sharesFactoryTeam = selectedFactoryTeamId != null
                && selectedFactoryTeamId.equals(FTBTeamsCompat.partyTeamId(requesterId).orElse(null));
        if (!isFactoryOwner && !sharesFactoryTeam) return false;

        factoryId = selectedFactoryId;
        factoryOwnerId = selectedFactoryOwnerId;
        factoryPartyTeamId = selectedFactoryTeamId;
        mode = Mode.FACTORY;
        setChanged();
        sync();
        return true;
    }

    /**
     * Returns the selected view's persisted snapshot, or null when unconfigured,
     * unauthorized, or the target factory/team can no longer be accessed.
     */
    public NetworkStatsData.Snapshot getStats(UUID requesterId) {
        if (level == null || level.isClientSide
                || !(level instanceof ServerLevel serverLevel) || !hasAccess(requesterId)) return null;
        NetworkStatsData data = NetworkStatsData.get(serverLevel);
        if (mode == Mode.TEAM_GLOBAL) {
            UUID currentTeam = FTBTeamsCompat.partyTeamId(requesterId).orElse(null);
            if (currentTeam == null || !canViewTeamGlobal(requesterId)
                    || !currentTeam.equals(ownerPartyTeamId)) return null;
            return data.snapshotTeam(currentTeam);
        }
        if (factoryId == null || factoryOwnerId == null) return null;
        if (factoryPartyTeamId != null) {
            UUID currentTeam = FTBTeamsCompat.partyTeamId(requesterId).orElse(null);
            if (!factoryPartyTeamId.equals(currentTeam)
                    || !factoryPartyTeamId.equals(FTBTeamsCompat.partyTeamId(factoryOwnerId).orElse(null))) {
                return null;
            }
        } else if (!factoryOwnerId.equals(requesterId)) {
            return null;
        }
        return data.snapshotFactory(factoryId);
    }

    public boolean isConfigured() { return ownerId != null; }
    public UUID getFactoryId() { return factoryId; }
    public UUID getFactoryOwnerId() { return factoryOwnerId; }
    public UUID getFactoryPartyTeamId() { return factoryPartyTeamId; }
    public Mode getMode() { return mode; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getOwnerPartyTeamId() { return ownerPartyTeamId; }

    public boolean canViewTeamGlobal(UUID requesterId) {
        return hasAccess(requesterId) && ownerPartyTeamId != null
                && ownerPartyTeamId.equals(FTBTeamsCompat.partyTeamId(ownerId).orElse(null));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        putUuid(tag, "OwnerId", ownerId);
        putUuid(tag, "OwnerPartyTeamId", ownerPartyTeamId);
        putUuid(tag, "FactoryId", factoryId);
        putUuid(tag, "FactoryOwnerId", factoryOwnerId);
        putUuid(tag, "FactoryPartyTeamId", factoryPartyTeamId);
        tag.putString("Mode", mode.name());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = getUuid(tag, "OwnerId");
        ownerPartyTeamId = getUuid(tag, "OwnerPartyTeamId");
        factoryId = getUuid(tag, "FactoryId");
        factoryOwnerId = getUuid(tag, "FactoryOwnerId");
        factoryPartyTeamId = getUuid(tag, "FactoryPartyTeamId");
        try {
            mode = Mode.valueOf(tag.getString("Mode"));
        } catch (IllegalArgumentException ignored) {
            mode = Mode.FACTORY;
        }
        if (mode == Mode.TEAM_GLOBAL && ownerPartyTeamId == null) mode = Mode.FACTORY;
    }

    private static void putUuid(CompoundTag tag, String key, UUID value) {
        if (value != null) tag.putUUID(key, value);
    }

    private static UUID getUuid(CompoundTag tag, String key) {
        return tag.hasUUID(key) ? tag.getUUID(key) : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
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
