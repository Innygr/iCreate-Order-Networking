package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

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

    public TerminalLoggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL_LOGGER.get(), pos, state);
    }

    public Direction getOutputDirection() { return outputDirection; }
    public long getAcceptedDeliveries() { return acceptedDeliveries; }
    public long getRejectedDeliveries() { return rejectedDeliveries; }
    public String getLastStatus() { return lastStatus; }

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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputDirection = Direction.byName(tag.getString("OutputDirection")) == null
                ? Direction.NORTH : Direction.byName(tag.getString("OutputDirection"));
        acceptedDeliveries = Math.max(0, tag.getLong("AcceptedDeliveries"));
        rejectedDeliveries = Math.max(0, tag.getLong("RejectedDeliveries"));
        lastStatus = safeStatus(tag.getString("LastStatus"));
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
