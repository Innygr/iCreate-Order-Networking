package com.innygr.icreateordernetworking.compat;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create Display Link source for one Terminal Logger.
 *
 * This deliberately reports only the selected logger's local buffer and routing
 * diagnostics; it does not pretend that one logger represents the whole factory.
 */
public final class TerminalLoggerDisplaySource extends SingleLineDisplaySource {
    public static final TerminalLoggerDisplaySource INSTANCE = new TerminalLoggerDisplaySource();

    private TerminalLoggerDisplaySource() {}

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof TerminalLoggerBlockEntity logger)) return EMPTY_LINE;

        int occupiedSlots = 0;
        int bufferedItems = 0;
        for (int slot = 0; slot < logger.getInventory().getSlots(); slot++) {
            ItemStack stack = logger.getInventory().getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            occupiedSlots++;
            bufferedItems += stack.getCount();
        }

        String filter = logger.getFilterItemId() == null ? "ALL" : logger.getFilterItemId().toString();
        String owner = logger.getOwnerId() == null ? "UNCLAIMED"
                : logger.getOwnerId().toString().substring(0, 4);
        String status = logger.getLastStatus();
        if (status.length() > 32) status = status.substring(0, 32);
        return Component.literal("Logger " + owner + " " + logger.getOutputDirection().getName()
                + " B:" + bufferedItems + " S:" + occupiedSlots
                + " A:" + logger.getAcceptedDeliveries() + " R:" + logger.getRejectedDeliveries()
                + " F:" + filter + " " + status);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "terminal_logger";
    }
}
