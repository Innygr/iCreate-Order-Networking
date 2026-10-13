package com.innygr.icreateordernetworking.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

class TerminalLoggerConfigTest {
    @Test
    void changingOutputReenablesTheFormerOutputAsAnInput() {
        TerminalLoggerConfig config = new TerminalLoggerConfig();

        assertEquals(Direction.DOWN, config.output());
        assertFalse(config.isInputEnabled(Direction.DOWN));
        assertTrue(config.isInputEnabled(Direction.NORTH));

        config.setOutput(Direction.NORTH);

        assertEquals(Direction.NORTH, config.output());
        assertTrue(config.isInputEnabled(Direction.DOWN));
        assertFalse(config.isInputEnabled(Direction.NORTH));
        for (Direction direction : Direction.values()) {
            assertEquals(direction != Direction.NORTH, config.isInputEnabled(direction));
        }
    }

    @Test
    void explicitInputDisablesSurviveOutputChangesAndSerialization() {
        TerminalLoggerConfig config = new TerminalLoggerConfig();
        config.setInputEnabled(Direction.EAST, false);
        config.setOutput(Direction.SOUTH);

        CompoundTag saved = config.save();
        TerminalLoggerConfig restored = new TerminalLoggerConfig();
        restored.load(saved);

        assertEquals(Direction.SOUTH, restored.output());
        assertFalse(restored.isInputEnabled(Direction.SOUTH));
        assertFalse(restored.isInputEnabled(Direction.EAST));
        assertTrue(restored.isInputEnabled(Direction.DOWN));
        assertTrue(restored.isInputEnabled(Direction.UP));
        assertTrue(restored.isInputEnabled(Direction.NORTH));
        assertTrue(restored.isInputEnabled(Direction.WEST));
    }

    @Test
    void outputFaceCannotAlsoBeEnabledAsAnInput() {
        TerminalLoggerConfig config = new TerminalLoggerConfig();

        assertThrows(IllegalArgumentException.class,
                () -> config.setInputEnabled(config.output(), true));
    }
}
