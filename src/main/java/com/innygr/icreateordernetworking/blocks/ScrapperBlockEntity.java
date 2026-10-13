package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sound-state hooks for the Scrapper.
 *
 * Processing code should call startCrushing() when an item begins being
 * processed and stopCrushing() when processing finishes or pauses. Until a
 * custom sound pack is added, these cues intentionally reuse vanilla sounds.
 */
public final class ScrapperBlockEntity extends BlockEntity {
    private static final int IDLE_CUE_INTERVAL_TICKS = 100;
    private static final float IDLE_VOLUME = 0.35F;
    private static final float CRUSHING_VOLUME = 0.7F;

    private int idleCueTimer;
    private boolean crushing;

    public ScrapperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRAPPER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ScrapperBlockEntity scrapper) {
        if (level.isClientSide || scrapper.crushing) {
            return;
        }

        if (++scrapper.idleCueTimer >= IDLE_CUE_INTERVAL_TICKS) {
            scrapper.idleCueTimer = 0;
            scrapper.playCue(SoundEvents.CHAIN_STEP, IDLE_VOLUME,
                    0.7F + level.random.nextFloat() * 0.2F);
        }
    }

    /** Call when the Scrapper starts crushing an item. */
    public void startCrushing() {
        if (crushing) {
            return;
        }
        crushing = true;
        idleCueTimer = 0;
        playCue(SoundEvents.ANVIL_USE, CRUSHING_VOLUME, 0.85F + levelRandom() * 0.3F);
    }

    /** Call when the current crushing operation ends or pauses. */
    public void stopCrushing() {
        crushing = false;
        idleCueTimer = 0;
    }

    public boolean isCrushing() {
        return crushing;
    }

    private float levelRandom() {
        return level == null ? 0.5F : level.random.nextFloat();
    }

    private void playCue(SoundEvent sound, float volume, float pitch) {
        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}
