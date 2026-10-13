package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
import com.mojang.serialization.MapCodec;

/** Factory identity block and binding point for adjacent loggers and Network Statistics displays. */
public final class OrderTerminalBlock extends BaseEntityBlock {
    public OrderTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<OrderTerminalBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrderTerminalBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OrderTerminalBlockEntity terminal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        interact(terminal, level, pos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof OrderTerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        interact(terminal, level, pos, player);
        return InteractionResult.SUCCESS;
    }

    private static void interact(OrderTerminalBlockEntity terminal, Level level, BlockPos pos, Player player) {
        if (!terminal.isConfigured()) {
            if (terminal.claimIfUnconfigured(player.getUUID())) {
                player.displayClientMessage(Component.literal("Order Terminal claimed. Factory ID: "
                        + shortId(terminal.getFactoryId()) + ". Place a claimed Network Statistics block next to it, then right-click this terminal to bind it."), true);
                return;
            }
        }

        if (!terminal.canAccessFactory(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this factory's Order Terminal."), true);
            return;
        }

        int adjacentDisplays = 0;
        int boundDisplays = 0;
        int adjacentLoggers = 0;
        int boundLoggers = 0;
        int inaccessibleTargets = 0;
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = pos.relative(direction);
            BlockEntity adjacentEntity = level.getBlockEntity(adjacent);
            if (adjacentEntity instanceof NetworkStatisticsBlockEntity statistics) {
                adjacentDisplays++;
                if (statistics.isConfigured() && statistics.bindFactoryFrom(player.getUUID(), terminal)) {
                    boundDisplays++;
                } else {
                    inaccessibleTargets++;
                }
            } else if (adjacentEntity instanceof TerminalLoggerBlockEntity logger) {
                adjacentLoggers++;
                if (logger.isConfigured() && logger.bindFactory(player.getUUID(), terminal.getFactoryId(),
                        terminal.getFactoryOwnerId(), terminal.getFactoryPartyTeamId())) {
                    boundLoggers++;
                } else {
                    inaccessibleTargets++;
                }
            }
        }

        if (adjacentDisplays == 0 && adjacentLoggers == 0) {
            player.displayClientMessage(Component.literal("Factory " + shortId(terminal.getFactoryId())
                    + " is ready. Place a claimed Network Statistics block and/or Terminal Logger directly beside this terminal, then right-click again to bind them."), true);
        } else if (boundDisplays > 0 || boundLoggers > 0) {
            player.displayClientMessage(Component.literal("Factory " + shortId(terminal.getFactoryId())
                    + ": bound " + boundDisplays + " statistics display(s) and " + boundLoggers
                    + " Terminal Logger(s)." + (inaccessibleTargets > 0 ? " " + inaccessibleTargets
                    + " adjacent target(s) were unclaimed or inaccessible." : "")), true);
        } else {
            player.displayClientMessage(Component.literal("No adjacent targets could be bound. Claim each display/logger first and check owner/team permissions."), true);
        }
    }

    private static String shortId(java.util.UUID id) {
        return id.toString().substring(0, 8);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
