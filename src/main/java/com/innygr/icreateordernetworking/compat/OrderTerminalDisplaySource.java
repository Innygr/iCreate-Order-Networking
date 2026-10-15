package com.innygr.icreateordernetworking.compat;

import com.innygr.icreateordernetworking.blocks.OrderTerminalBlockEntity;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderStatus;
import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class OrderTerminalDisplaySource extends SingleLineDisplaySource {
    public static final OrderTerminalDisplaySource INSTANCE = new OrderTerminalDisplaySource();
    private OrderTerminalDisplaySource() {}

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        BlockEntity source = context.getSourceBlockEntity();
        if (!(source instanceof OrderTerminalBlockEntity terminal)) return EMPTY_LINE;
        if (!(terminal.getLevel() instanceof ServerLevel level)) return EMPTY_LINE;
        if (terminal.getFactoryOwnerId() == null) return Component.literal("Order Terminal unclaimed");
        int active = 0;
        long needed = 0;
        for (Order order : OrderData.get(level).all()) {
            if (!terminal.getFactoryOwnerId().equals(order.ownerId())) continue;
            if (order.status() != OrderStatus.OPEN && order.status() != OrderStatus.IN_PROGRESS) continue;
            active++;
            needed += order.remainingCount();
        }
        return Component.literal("Orders:" + active + " Needed:" + needed);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) { return true; }

    @Override
    protected String getTranslationKey() { return "order_terminal"; }
}
