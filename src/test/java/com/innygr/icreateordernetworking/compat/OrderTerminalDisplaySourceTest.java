package com.innygr.icreateordernetworking.compat;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.innygr.icreateordernetworking.orders.Order;
import com.innygr.icreateordernetworking.orders.OrderStatus;

import net.minecraft.resources.ResourceLocation;

class OrderTerminalDisplaySourceTest {
    @Test
    void summaryCountsOnlyActiveOrdersOwnedByTerminalOwner() {
        UUID owner = UUID.randomUUID();
        UUID otherOwner = UUID.randomUUID();
        UUID claimant = UUID.randomUUID();
        ResourceLocation iron = ResourceLocation.parse("minecraft:iron_ingot");
        List<Order> orders = List.of(
                new Order(UUID.randomUUID(), owner, "owner", iron, 64, 16, 1,
                        claimant, OrderStatus.IN_PROGRESS, 1L),
                new Order(UUID.randomUUID(), owner, "owner", iron, 20, 0, 1,
                        null, OrderStatus.OPEN, 2L),
                new Order(UUID.randomUUID(), owner, "owner", iron, 5, 5, 1,
                        claimant, OrderStatus.COMPLETED, 3L),
                new Order(UUID.randomUUID(), owner, "owner", iron, 10, 0, 1,
                        null, OrderStatus.CANCELLED, 4L),
                new Order(UUID.randomUUID(), otherOwner, "other", iron, 100, 0, 1,
                        null, OrderStatus.OPEN, 5L));

        OrderTerminalDisplaySource.Summary summary = OrderTerminalDisplaySource.summarize(orders, owner);

        assertEquals(2, summary.activeOrders());
        assertEquals(68L, summary.outstandingItems());
    }

    @Test
    void summaryForUnclaimedTerminalHasNoOrders() {
        OrderTerminalDisplaySource.Summary summary =
                OrderTerminalDisplaySource.summarize(List.of(), null);

        assertEquals(0, summary.activeOrders());
        assertEquals(0L, summary.outstandingItems());
    }
}
