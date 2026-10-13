package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import com.innygr.icreateordernetworking.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Sound-state hooks for the Scrapper.
 *
 * The loop-named cues are retriggered at intervals while the corresponding
 * state is active. Processing logic should call startCrushing() and
 * stopCrushing() at the beginning and end of each operation.
 */
public final class ScrapperBlockEntity extends BlockEntity {
    private static final int IDLE_CUE_INTERVAL_TICKS = 100;
    private static final int ACTIVE_CUE_INTERVAL_TICKS = 40;
    private static final float IDLE_VOLUME = 0.45F;
    private static final float ACTIVE_VOLUME = 0.75F;

    private int cueTimer;
    private boolean crushing;

    public ScrapperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SCRAPPER.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  ScrapperBlockEntity scrapper) {
        if (level.isClientSide) {
            return;
        }

        int interval = scrapper.crushing ? ACTIVE_CUE_INTERVAL_TICKS : IDLE_CUE_INTERVAL_TICKS;
        if (++scrapper.cueTimer >= interval) {
            scrapper.cueTimer = 0;
            if (scrapper.crushing) {
                scrapper.playCue(ModSounds.GRINDER_ACTIVE_LOOP.get(), ACTIVE_VOLUME, 1.0F);
            } else {
                scrapper.playCue(ModSounds.GRINDER_IDLE_LOOP.get(), IDLE_VOLUME, 1.0F);
            }
        }
    }

    /** Call once when the Scrapper starts processing an item. */
    public void startCrushing() {
        if (crushing) {
            return;
        }
        crushing = true;
        cueTimer = 0;
        playCue(ModSounds.GRINDER_BEGIN.get(), ACTIVE_VOLUME, 1.0F);
    }

    /** Call when processing ends or pauses; plays the metallic finishing cue. */
    public void stopCrushing() {
        if (!crushing) {
            return;
        }
        crushing = false;
        cueTimer = 0;
        playCue(ModSounds.GRINDER_METAL.get(), ACTIVE_VOLUME, 1.0F);
    }

    public boolean isCrushing() {
        return crushing;
    }

    private void playCue(SoundEvent sound, float volume, float pitch) {
        if (level != null && !level.isClientSide) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }
}
