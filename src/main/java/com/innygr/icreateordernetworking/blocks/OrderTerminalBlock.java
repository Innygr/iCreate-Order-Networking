package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.logistics.StockLinkBindingData;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

/** Two-block-tall factory identity block and binding point for adjacent loggers and displays. */
public final class OrderTerminalBlock extends BaseEntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;

    public OrderTerminalBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(HALF, DoubleBlockHalf.LOWER));
    }

    @Override
    public MapCodec<OrderTerminalBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos pos = context.getClickedPos();
        Level level = context.getLevel();
        if (pos.getY() >= level.getMaxBuildHeight() - 1 || !level.getBlockState(pos.above()).canBeReplaced(context)) {
            return null;
        }
        return defaultBlockState().setValue(HALF, DoubleBlockHalf.LOWER);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, LivingEntity placer, ItemStack stack) {
        level.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);
        super.setPlacedBy(level, pos, state, placer, stack);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf half = state.getValue(HALF);
        if ((half == DoubleBlockHalf.LOWER && direction == Direction.UP)
                || (half == DoubleBlockHalf.UPPER && direction == Direction.DOWN)) {
            if (!neighborState.is(this) || neighborState.getValue(HALF) == half) {
                return Blocks.AIR.defaultBlockState();
            }
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(HALF) == DoubleBlockHalf.LOWER
                ? new OrderTerminalBlockEntity(pos, state) : null;
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
        BlockPos basePos = getBasePos(state, pos);
        if (!(level.getBlockEntity(basePos) instanceof OrderTerminalBlockEntity terminal)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        interact(terminal, level, basePos, player);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        BlockPos basePos = getBasePos(state, pos);
        if (!(level.getBlockEntity(basePos) instanceof OrderTerminalBlockEntity terminal)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        interact(terminal, level, basePos, player);
        return InteractionResult.SUCCESS;
    }

    private static BlockPos getBasePos(BlockState state, BlockPos pos) {
        return state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos;
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide) {
            BlockPos otherPos = state.getValue(HALF) == DoubleBlockHalf.UPPER ? pos.below() : pos.above();
            BlockState otherState = level.getBlockState(otherPos);
            if (otherState.is(this) && otherState.getValue(HALF) != state.getValue(HALF)) {
                level.setBlock(otherPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
            }
        }
        return super.playerWillDestroy(level, pos, state, player);
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
        int adjacentStockLinks = 0;
        int boundStockLinks = 0;
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
            } else if (adjacentEntity instanceof PackagerLinkBlockEntity) {
                adjacentStockLinks++;
                if (level instanceof net.minecraft.server.level.ServerLevel serverLevel
                        && StockLinkBindingData.get(serverLevel).bind(serverLevel, adjacent, player.getUUID(), terminal)) {
                    boundStockLinks++;
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

        if (adjacentDisplays == 0 && adjacentLoggers == 0 && adjacentStockLinks == 0) {
            player.displayClientMessage(Component.literal("Factory " + shortId(terminal.getFactoryId())
                    + " is ready. Place a claimed Network Statistics block and/or Terminal Logger directly beside this terminal, then right-click again to bind them."), true);
        } else if (boundDisplays > 0 || boundLoggers > 0) {
            player.displayClientMessage(Component.literal("Factory " + shortId(terminal.getFactoryId())
                    + ": bound " + boundDisplays + " statistics display(s) and " + boundLoggers
                    + " Terminal Logger(s), " + boundStockLinks + " Create Stock Link(s)." + (inaccessibleTargets > 0 ? " " + inaccessibleTargets
                    + " adjacent target(s) were unclaimed or inaccessible." : "")), true);
        } else {
            player.displayClientMessage(Component.literal("No adjacent targets could be bound. Claim each display/logger first, ensure Stock Links are configured, and check owner/team permissions."), true);
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
