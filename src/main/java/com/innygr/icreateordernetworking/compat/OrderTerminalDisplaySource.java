package com.innygr.icreateordernetworking.compat;

import com.innygr.icreateordernetworking.blocks.OrderTerminalBlockEntity;
import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderData;
import com.innygr.icreateordernetworking.orders.OrderStatus;
import java.util.Collection;
import java.util.UUID;
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
        Summary summary = summarize(OrderData.get(level).all(), terminal.getFactoryOwnerId());
        return Component.literal("Orders:" + summary.activeOrders() + " Needed:" + summary.outstandingItems());
    }

    public record Summary(int activeOrders, long outstandingItems) {}

    static Summary summarize(Collection<Order> orders, UUID ownerId) {
        int active = 0;
        long needed = 0;
        if (ownerId == null) return new Summary(0, 0);
        for (Order order : orders) {
            if (!ownerId.equals(order.ownerId())) continue;
            if (order.status() != OrderStatus.OPEN && order.status() != OrderStatus.IN_PROGRESS) continue;
            active++;
            needed += order.remainingCount();
        }
        return new Summary(active, needed);
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) { return true; }

    @Override
    protected String getTranslationKey() { return "order_terminal"; }
}
