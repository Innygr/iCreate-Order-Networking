package com.innygr.icreateordernetworking.blocks;

import java.util.Optional;
import java.util.UUID;

import com.innygr.icreateordernetworking.compat.FTBTeamsCompat;
import com.innygr.icreateordernetworking.logistics.FactoryIdentityProvider;
import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persistent identity and ownership for one Order Terminal factory. */
public final class OrderTerminalBlockEntity extends BlockEntity implements FactoryIdentityProvider {
    private UUID factoryId = UUID.randomUUID();
    private UUID ownerId;
    private UUID ownerPartyTeamId;

    public OrderTerminalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORDER_TERMINAL.get(), pos, state);
    }

    public boolean claimIfUnconfigured(UUID playerId) {
        if (level == null || level.isClientSide || playerId == null || ownerId != null) return false;
        ownerId = playerId;
        ownerPartyTeamId = FTBTeamsCompat.partyTeamId(playerId).orElse(null);
        setChanged();
        sync();
        return true;
    }

    @Override
    public UUID getFactoryId() {
        return factoryId;
    }

    @Override
    public UUID getFactoryOwnerId() {
        return ownerId;
    }

    @Override
    public UUID getFactoryPartyTeamId() {
        return ownerPartyTeamId;
    }

    public boolean isConfigured() {
        return ownerId != null;
    }

    @Override
    public boolean canAccessFactory(UUID playerId) {
        if (playerId == null || ownerId == null) return false;
        if (ownerId.equals(playerId)) return true;
        if (ownerPartyTeamId == null) return false;
        Optional<UUID> currentOwnerTeam = FTBTeamsCompat.partyTeamId(ownerId);
        if (currentOwnerTeam.isEmpty() || !ownerPartyTeamId.equals(currentOwnerTeam.get())) return false;
        return ownerPartyTeamId.equals(FTBTeamsCompat.partyTeamId(playerId).orElse(null));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FactoryId", factoryId.toString());
        if (ownerId != null) tag.putString("OwnerId", ownerId.toString());
        if (ownerPartyTeamId != null) tag.putString("OwnerPartyTeamId", ownerPartyTeamId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        factoryId = parseUuid(tag, "FactoryId", factoryId);
        ownerId = parseUuid(tag, "OwnerId", null);
        ownerPartyTeamId = parseUuid(tag, "OwnerPartyTeamId", null);
    }

    private static UUID parseUuid(CompoundTag tag, String key, UUID fallback) {
        if (!tag.contains(key)) return fallback;
        try {
            return UUID.fromString(tag.getString(key));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
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
