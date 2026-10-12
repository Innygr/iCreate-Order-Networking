package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.mojang.serialization.MapCodec;

/**
 * Six-port logistics terminal. Its block entity stores a configurable output
 * face and delivery diagnostics; actual Create transport adapters are separate.
 */
public final class TerminalLoggerBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final VoxelShape SHAPE = Shapes.box(0.125, 0.0, 0.125, 0.875, 0.75, 0.875);

    private final boolean brass;

    public TerminalLoggerBlock(BlockBehaviour.Properties properties, boolean brass) {
        super(properties);
        this.brass = brass;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<TerminalLoggerBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<net.minecraft.world.level.block.Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TerminalLoggerBlockEntity(pos, state);
    }

    /**
     * 1.21.1 item-use hook. A held item configures the brass filter; other item
     * interactions pass through to the normal empty-hand block interaction.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player, InteractionHand hand,
                                               BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TerminalLoggerBlockEntity logger)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!level.isClientSide && !logger.isConfigured()) {
            if (logger.bindOwnerIfUnconfigured(player.getUUID())) {
                player.displayClientMessage(Component.literal("Terminal Logger claimed; access is "
                        + (logger.getOwnerPartyTeamId() == null
                        ? "private to you." : "shared with your current FTB party.")), true);
                return ItemInteractionResult.SUCCESS;
            }
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;
        if (!logger.hasAccess(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this Terminal Logger."), true);
            return ItemInteractionResult.FAIL;
        }
        if (brass && !stack.isEmpty()) {
            ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
            if (logger.setFilterItemId(itemId)) {
                player.displayClientMessage(Component.literal("Brass Terminal Logger filter: " + itemId), true);
                return ItemInteractionResult.SUCCESS;
            }
            return ItemInteractionResult.FAIL;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /**
     * 1.21.1 empty-hand interaction hook for cycling the selected output face.
     */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof TerminalLoggerBlockEntity logger)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!logger.isConfigured()) {
            if (logger.bindOwnerIfUnconfigured(player.getUUID())) {
                player.displayClientMessage(Component.literal("Terminal Logger claimed; access is "
                        + (logger.getOwnerPartyTeamId() == null
                        ? "private to you." : "shared with your current FTB party.")), true);
                return InteractionResult.SUCCESS;
            }
        }
        if (!logger.hasAccess(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this Terminal Logger."), true);
            return InteractionResult.FAIL;
        }
        if (brass && player.isShiftKeyDown()) {
            logger.setFilterItemId(null);
            player.displayClientMessage(Component.literal("Brass Terminal Logger filter cleared"), true);
            return InteractionResult.SUCCESS;
        }
        Direction next = switch (logger.getOutputDirection()) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.DOWN;
            case DOWN -> Direction.UP;
            case UP -> Direction.NORTH;
        };
        logger.setOutputDirection(next);
        player.displayClientMessage(Component.literal((brass ? "Brass" : "Andesite")
                + " Terminal Logger output: " + next.getName()), true);
        return InteractionResult.SUCCESS;
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public boolean supportsFiltering() {
        return brass;
    }
}
