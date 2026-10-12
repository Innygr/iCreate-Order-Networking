package com.innygr.icreateordernetworking.blocks;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * Early-game scrap-processing workstation block.
 * Unlike Terminal Logger blocks, the Scrapper intentionally emits light.
 * Processing behavior is implemented separately from delivery verification.
 */
public final class ScrapperBlock extends Block {
    public ScrapperBlock(BlockBehaviour.Properties properties) {
        super(properties.lightLevel(state -> 12));
    }
}
