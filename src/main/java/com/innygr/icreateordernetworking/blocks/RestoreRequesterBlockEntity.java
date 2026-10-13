package com.innygr.icreateordernetworking.blocks;

import java.util.UUID;
import com.innygr.icreateordernetworking.orders.OrderService;
import com.innygr.icreateordernetworking.orders.OrderTeamAccess;
import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

/** Watches adjacent inventories and creates one no-reward order for any stock deficit. */
public final class RestoreRequesterBlockEntity extends BlockEntity {
    public static final int TARGET_STOCK = 64;
    private static final int CHECK_INTERVAL_TICKS = 100;
    private UUID ownerId;
    private String ownerName = "Unknown";
    private String ownerTeamKey;
    private ResourceLocation itemId;
    private String status = "Unconfigured";

    public RestoreRequesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.RESTORE_REQUESTER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, RestoreRequesterBlockEntity requester) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel
                && level.getGameTime() % CHECK_INTERVAL_TICKS == 0) requester.checkStock(serverLevel);
    }

    public boolean configure(Player player, ItemStack held) {
        if (level == null || level.isClientSide || player == null || held == null || held.isEmpty()
                || ownerId != null || !(level instanceof ServerLevel)) return false;
        ownerId = player.getUUID();
        ownerName = player.getName().getString();
        if (player instanceof ServerPlayer serverPlayer) ownerTeamKey = OrderTeamAccess.teamKey(serverPlayer);
        itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(held.getItem());
        status = "Watching for " + itemId;
        setChanged();
        sync();
        player.displayClientMessage(Component.literal("Restore Requester claimed. It will order " + itemId
                + " whenever adjacent inventory stock falls below " + TARGET_STOCK + "."), true);
        return true;
    }

    public boolean hasAccess(UUID playerId) {
        if (playerId == null || ownerId == null) return false;
        if (ownerId.equals(playerId)) return true;
        if (ownerTeamKey == null || !(level instanceof ServerLevel serverLevel)) return false;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(playerId);
        return player != null && ownerTeamKey.equals(OrderTeamAccess.teamKey(player));
    }

    public boolean isConfigured() { return ownerId != null && itemId != null; }
    public String getStatus() { return status; }

    public boolean setItem(ResourceLocation newItemId) {
        if (!isConfigured() || newItemId == null
                || !net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(newItemId)) return false;
        itemId = newItemId;
        status = "Watching for " + itemId;
        setChanged();
        sync();
        return true;
    }

    private void checkStock(ServerLevel serverLevel) {
        if (!isConfigured() || ownerId == null) return;
        int stock = 0;
        boolean foundInventory = false;
        for (Direction direction : Direction.values()) {
            BlockPos adjacentPos = worldPosition.relative(direction);
            IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, adjacentPos, direction.getOpposite());
            if (handler == null) continue;
            foundInventory = true;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.isEmpty() && itemId.equals(
                        net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()))) {
                    stock = Math.min(TARGET_STOCK, stock + stack.getCount());
                }
            }
        }
        if (!foundInventory) {
            status = "No adjacent item-handler inventory found";
            setChanged();
            return;
        }
        if (stock >= TARGET_STOCK) {
            status = "Stock healthy: " + stock + "/" + TARGET_STOCK;
            setChanged();
            return;
        }
        int deficit = TARGET_STOCK - stock;
        if (OrderService.hasActiveOrderForOwnerItem(serverLevel, ownerId, itemId)) {
            status = "Low stock (" + stock + "/" + TARGET_STOCK + "); an order is already active";
            setChanged();
            return;
        }
        if (OrderService.createRestockOrder(serverLevel, ownerId, ownerName, ownerTeamKey, itemId, deficit) != null)
            status = "Created restock order for " + deficit + "x " + itemId;
        else status = "Could not create restock order";
        setChanged();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (ownerId != null) tag.putUUID("OwnerId", ownerId);
        tag.putString("OwnerName", ownerName);
        if (ownerTeamKey != null) tag.putString("OwnerTeamKey", ownerTeamKey);
        if (itemId != null) tag.putString("ItemId", itemId.toString());
        tag.putString("Status", status);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ownerId = tag.hasUUID("OwnerId") ? tag.getUUID("OwnerId") : null;
        ownerName = tag.contains("OwnerName") ? tag.getString("OwnerName") : "Unknown";
        ownerTeamKey = tag.contains("OwnerTeamKey") ? tag.getString("OwnerTeamKey") : null;
        itemId = null;
        if (tag.contains("ItemId")) {
            try {
                ResourceLocation parsed = ResourceLocation.parse(tag.getString("ItemId"));
                if (net.minecraft.core.registries.BuiltInRegistries.ITEM.containsKey(parsed)) itemId = parsed;
            } catch (IllegalArgumentException ignored) { itemId = null; }
        }
        status = tag.contains("Status") ? tag.getString("Status") : "Unconfigured";
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }
    private void sync() {
        if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
}