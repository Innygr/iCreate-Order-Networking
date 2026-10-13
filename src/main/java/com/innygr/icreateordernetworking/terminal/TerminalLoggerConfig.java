package com.innygr.icreateordernetworking.terminal;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;

/**
 * Server-owned routing configuration for a Terminal Logger.
 *
 * The block is conceptually a six-input funnel: every side can accept cargo,
 * while a single configurable side is the output. This class stores only the
 * routing policy; a registered block entity must enforce it during real item
 * transfer and serialize this config with its own block-entity data.
 */
public final class TerminalLoggerConfig {
    private Direction output = Direction.DOWN;
    private final Map<Direction, Boolean> enabledInputs = new EnumMap<>(Direction.class);

    public TerminalLoggerConfig() {
        for (Direction direction : Direction.values()) enabledInputs.put(direction, true);
        enabledInputs.put(output, false);
    }

    public Direction output() {
        return output;
    }

    public boolean isInputEnabled(Direction side) {
        return side != null && side != output && Boolean.TRUE.equals(enabledInputs.get(side));
    }

    /** Configure which side emits accepted cargo. Output side cannot also be an input. */
    public void setOutput(Direction side) {
        Direction next = Objects.requireNonNull(side, "side");
        if (next == output) return;

        // The former output face becomes an input again so exactly one side is
        // excluded by default. Preserve explicit input settings on all other faces.
        enabledInputs.put(output, true);
        output = next;
        enabledInputs.put(output, false);
    }

    public void setInputEnabled(Direction side, boolean enabled) {
        Objects.requireNonNull(side, "side");
        if (side == output && enabled) {
            throw new IllegalArgumentException("The configured output side cannot also be an input");
        }
        enabledInputs.put(side, enabled);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Output", output.getName());
        CompoundTag inputs = new CompoundTag();
        for (Direction direction : Direction.values()) {
            inputs.putBoolean(direction.getName(), isInputEnabled(direction));
        }
        tag.put("Inputs", inputs);
        return tag;
    }

    public void load(CompoundTag tag) {
        Direction restored = Direction.byName(tag.getString("Output"));
        output = restored == null ? Direction.DOWN : restored;
        enabledInputs.clear();
        CompoundTag inputs = tag.getCompound("Inputs");
        for (Direction direction : Direction.values()) {
            boolean enabled = inputs.contains(direction.getName())
                    ? inputs.getBoolean(direction.getName()) : true;
            enabledInputs.put(direction, direction != output && enabled);
        }
    }
}