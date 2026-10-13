package com.innygr.icreateordernetworking.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Redstone-testable thruster block with light and nearby ignition behaviour. */
public final class MissileThrusterBlock extends Block {
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    public MissileThrusterBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide()) level.scheduleTick(pos, this, 1);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        boolean powered = level.hasNeighborSignal(pos);
        if (state.getValue(LIT) != powered) {
            state = state.setValue(LIT, powered);
            level.setBlock(pos, state, Block.UPDATE_ALL);
        }
        if (!powered) return;

        for (Direction direction : Direction.values()) {
            BlockPos airPos = pos.relative(direction);
            if (!level.getBlockState(airPos).isAir()) continue;
            for (Direction supportDirection : Direction.values()) {
                BlockPos supportPos = airPos.relative(supportDirection);
                BlockState support = level.getBlockState(supportPos);
                if (support.isFlammable(level, supportPos, supportDirection.getOpposite())) {
                    if (random.nextInt(3) == 0) {
                        level.setBlockAndUpdate(airPos, Blocks.FIRE.defaultBlockState());
                    }
                    break;
                }
            }
        }
        level.scheduleTick(pos, this, 10);
    }
}
