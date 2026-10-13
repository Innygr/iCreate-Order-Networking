package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Adjacent-inventory stock watcher that automatically opens replenishment orders. */
public final class RestoreRequesterBlock extends BaseEntityBlock {
    public static final MapCodec<RestoreRequesterBlock> CODEC = simpleCodec(RestoreRequesterBlock::new);
    public RestoreRequesterBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RestoreRequesterBlockEntity(pos, state);
    }
    @Override public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.RESTORE_REQUESTER.get(),
                RestoreRequesterBlockEntity::serverTick);
    }
    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
            BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RestoreRequesterBlockEntity requester))
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!requester.isConfigured()) {
            if (requester.configure(player, stack)) return ItemInteractionResult.SUCCESS;
            player.displayClientMessage(Component.literal("Hold the item you want restocked and right-click to configure this Restore Requester."), true);
            return ItemInteractionResult.FAIL;
        }
        if (!requester.hasAccess(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this Restore Requester."), true);
            return ItemInteractionResult.FAIL;
        }
        if (!stack.isEmpty()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (requester.setItem(itemId)) {
                player.displayClientMessage(Component.literal("Restore Requester now restocks " + itemId
                        + " to " + RestoreRequesterBlockEntity.TARGET_STOCK + " items."), true);
                return ItemInteractionResult.SUCCESS;
            }
        }
        player.displayClientMessage(Component.literal(requester.getStatus()), true);
        return ItemInteractionResult.SUCCESS;
    }
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof RestoreRequesterBlockEntity requester)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!requester.isConfigured()) {
            player.displayClientMessage(Component.literal("Hold the item to restock and right-click this block."), true);
            return InteractionResult.SUCCESS;
        }
        if (!requester.hasAccess(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this Restore Requester."), true);
            return InteractionResult.FAIL;
        }
        player.displayClientMessage(Component.literal(requester.getStatus()), true);
        return InteractionResult.SUCCESS;
    }
    @Override protected RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
}