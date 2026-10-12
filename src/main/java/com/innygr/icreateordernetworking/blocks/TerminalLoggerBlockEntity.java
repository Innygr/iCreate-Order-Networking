package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
    private ResourceLocation filterItemId;

    public TerminalLoggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL_LOGGER.get(), pos, state);
    }

    public Direction getOutputDirection() { return outputDirection; }
    public long getAcceptedDeliveries() { return acceptedDeliveries; }
    public long getRejectedDeliveries() { return rejectedDeliveries; }
    public String getLastStatus() { return lastStatus; }
    public ResourceLocation getFilterItemId() { return filterItemId; }

    /** Five faces accept inbound logistics; the configured face is output-only. */
    public boolean canInputFrom(Direction side) {
        return side != null && side != outputDirection && acceptsItemFromSide(side);
    }

    /** The configured face is the only permitted output face. */
    public boolean canOutputTo(Direction side) {
        return side != null && side == outputDirection;
    }

    private boolean acceptsItemFromSide(Direction side) {
        // Current routing is face-based. Future route adapters can apply stricter
        // per-route rules without changing the persistent output configuration.
        return side != outputDirection;
    }

    /**
     * Called by a trusted server-side transfer adapter after it has observed an
     * actual item movement. This method only records diagnostics; it never pays
     * an order or changes order progress itself.
     */
    public void recordTransfer(boolean accepted, ResourceLocation itemId, int count, String reason) {
        if (level == null || level.isClientSide || count < 1 || !acceptsItem(itemId)) {
            if (level != null && !level.isClientSide) recordRejected("Rejected transfer: invalid item/filter/count");
            return;
        }
        String detail = (reason == null || reason.isBlank() ? "Transfer" : reason)
                + " " + count + "x " + itemId;
        if (accepted) recordAccepted(detail);
        else recordRejected(detail);
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
    }

    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        outputDirection = Direction.byName(tag.getString("OutputDirection")) == null
                ? Direction.NORTH : Direction.byName(tag.getString("OutputDirection"));
        acceptedDeliveries = Math.max(0, tag.getLong("AcceptedDeliveries"));
        rejectedDeliveries = Math.max(0, tag.getLong("RejectedDeliveries"));
        lastStatus = safeStatus(tag.getString("LastStatus"));
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
