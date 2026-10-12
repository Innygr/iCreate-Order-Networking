package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
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
 * Six-way Create logistics junction. Five faces are input faces and pull from
 * adjacent item-handler capabilities; the configured face is output-only.
 * PSI and Docking Connector endpoints are handled through their exposed item
 * capability when docked. Route-specific order verification remains separate.
 */
public final class TerminalLoggerBlockEntity extends BlockEntity {
    private static final int SLOT_COUNT = 18;
    private static final int TRANSFER_PER_TICK = 4;

    private Direction outputDirection = Direction.NORTH;
    private long acceptedDeliveries;
    private long rejectedDeliveries;
    private String lastStatus = "Idle";
    private ResourceLocation filterItemId;

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

    public TerminalLoggerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TERMINAL_LOGGER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TerminalLoggerBlockEntity logger) {
        if (level.isClientSide || level.getGameTime() % 4 != 0) return;
        logger.pullInputs(level);
        logger.pushOutput(level);
    }

    private void pullInputs(Level level) {
        for (Direction side : Direction.values()) {
            if (side == outputDirection) continue;
            BlockPos sourcePos = worldPosition.relative(side);
            IItemHandler source = level.getCapability(Capabilities.ItemHandler.BLOCK, sourcePos, side.getOpposite());
            if (source == null) continue;

            for (int sourceSlot = 0; sourceSlot < source.getSlots(); sourceSlot++) {
                ItemStack offered = source.extractItem(sourceSlot, TRANSFER_PER_TICK, true);
                if (offered.isEmpty() || !acceptsItem(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offered.getItem()))) continue;

                ItemStack remainder = offered.copy();
                for (int targetSlot = 0; targetSlot < inventory.getSlots() && !remainder.isEmpty(); targetSlot++) {
                    remainder = inventory.insertItem(targetSlot, remainder, false);
                }
                int accepted = offered.getCount() - remainder.getCount();
                if (accepted > 0) {
                    // Re-extract exactly the amount successfully inserted. This
                    // prevents voiding items when our buffer is nearly full.
                    source.extractItem(sourceSlot, accepted, false);
                    recordAccepted("Pulled " + accepted + "x " + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(offered.getItem())
                            + " from " + side.getName());
                    return; // One source slot per cycle avoids starving other sources.
                }
            }
        }
    }

    private void pushOutput(Level level) {
        BlockPos targetPos = worldPosition.relative(outputDirection);
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, targetPos, outputDirection.getOpposite());
        if (target == null) return;

        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stored = inventory.getStackInSlot(slot);
            if (stored.isEmpty()) continue;
            for (int targetSlot = 0; targetSlot < target.getSlots(); targetSlot++) {
                ItemStack remainder = target.insertItem(targetSlot, stored.copyWithCount(
                        Math.min(TRANSFER_PER_TICK, stored.getCount())), false);
                int moved = Math.min(TRANSFER_PER_TICK, stored.getCount()) - remainder.getCount();
                if (moved > 0) {
                    inventory.extractItem(slot, moved, false);
                    recordAccepted("Output " + moved + "x "
                            + net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stored.getItem()));
                    return;
                }
            }
        }
    }

    public ItemStackHandler getInventory() { return inventory; }
    public Direction getOutputDirection() { return outputDirection; }
    public long getAcceptedDeliveries() { return acceptedDeliveries; }
    public long getRejectedDeliveries() { return rejectedDeliveries; }
    public String getLastStatus() { return lastStatus; }
    public ResourceLocation getFilterItemId() { return filterItemId; }

    public boolean canInputFrom(Direction side, ResourceLocation itemId) {
        return side != null && side != outputDirection && acceptsItem(itemId);
    }

    public boolean canOutputTo(Direction side) { return side != null && side == outputDirection; }

    /** Records diagnostics after an observed transfer; does not itself fulfil an order. */
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("OutputDirection", outputDirection.getName());
        tag.putLong("AcceptedDeliveries", acceptedDeliveries);
        tag.putLong("RejectedDeliveries", rejectedDeliveries);
        tag.putString("LastStatus", lastStatus);
        tag.put("Inventory", inventory.serializeNBT(registries));
        if (filterItemId != null) tag.putString("FilterItem", filterItemId.toString());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        Direction loaded = Direction.byName(tag.getString("OutputDirection"));
        outputDirection = loaded == null ? Direction.NORTH : loaded;
        acceptedDeliveries = Math.max(0, tag.getLong("AcceptedDeliveries"));
        rejectedDeliveries = Math.max(0, tag.getLong("RejectedDeliveries"));
        lastStatus = safeStatus(tag.getString("LastStatus"));
        if (tag.contains("Inventory")) inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        filterItemId = null;
        if (tag.contains("FilterItem")) {
            try {
                ResourceLocation candidate = ResourceLocation.parse(tag.getString("FilterItem"));
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(candidate)) filterItemId = candidate;
            } catch (IllegalArgumentException ignored) {
                filterItemId = null;
            }
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
