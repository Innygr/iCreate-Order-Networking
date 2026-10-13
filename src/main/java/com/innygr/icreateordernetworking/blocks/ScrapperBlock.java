package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Early-game scrap-processing workstation block.
 * Unlike Terminal Logger blocks, the Scrapper intentionally emits light.
 */
public final class ScrapperBlock extends BaseEntityBlock {
    public ScrapperBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> 12));
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ScrapperBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return type == ModBlockEntities.SCRAPPER.get()
                ? (tickLevel, pos, tickState, blockEntity) ->
                    ScrapperBlockEntity.serverTick(tickLevel, pos, tickState, (ScrapperBlockEntity) blockEntity)
                : null;
    }
}
