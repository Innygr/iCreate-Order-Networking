package com.innygr.icreateordernetworking.blocks;

import java.util.UUID;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Persisted missile/team identity, including the team's banner for display by a renderer. */
public final class MissileTagBlockEntity extends BlockEntity {
    private UUID missileId = UUID.randomUUID();
    private String teamName = "";
    private ItemStack banner = ItemStack.EMPTY;

    public MissileTagBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MISSILE_TAG.get(), pos, state);
    }

    public UUID getMissileId() { return missileId; }
    public String getTeamName() { return teamName; }
    public ItemStack getBanner() { return banner.copy(); }

    /** Called by assembly/configuration code after the optional tag is detected. */
    public void configureIdentity(UUID id, String name, ItemStack teamBanner) {
        if (id != null) missileId = id;
        teamName = name == null ? "" : name.strip();
        if (teamName.length() > 32) teamName = teamName.substring(0, 32);
        banner = teamBanner == null ? ItemStack.EMPTY : teamBanner.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("MissileId", missileId.toString());
        tag.putString("TeamName", teamName);
        if (!banner.isEmpty()) tag.put("TeamBanner", banner.save(registries));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("MissileId")) {
            try { missileId = UUID.fromString(tag.getString("MissileId")); }
            catch (IllegalArgumentException ignored) { missileId = UUID.randomUUID(); }
        }
        teamName = tag.getString("TeamName");
        banner = tag.contains("TeamBanner")
                ? ItemStack.parse(registries, tag.getCompound("TeamBanner")).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
