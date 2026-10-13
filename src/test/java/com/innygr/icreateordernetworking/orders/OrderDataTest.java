package com.innygr.icreateordernetworking.orders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.innygr.icreateordernetworking.delivery.DeliveryRoute;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

class OrderDataTest {
    @Test
    void orderStateSurvivesSavedDataRoundTrip() {
        OrderData original = new OrderData();
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID claimant = UUID.randomUUID();
        Order order = new Order(id, owner, "owner", ResourceLocation.parse("minecraft:iron_ingot"),
                64, 16, 3, claimant, OrderStatus.IN_PROGRESS, 123L, "team-a");
        original.add(order);
        original.reserveDelivery(UUID.randomUUID(), id, DeliveryRoute.TRAIN, claimant, 8);

        CompoundTag saved = original.save(new CompoundTag(), null);
        OrderData restored = OrderData.load(saved, null);

        Order loaded = restored.find(id);
        assertEquals(OrderStatus.IN_PROGRESS, loaded.status());
        assertEquals(64, loaded.requestedCount());
        assertEquals(16, loaded.fulfilledCount());
        assertEquals(48, loaded.remainingCount());
        assertEquals("team-a", loaded.ownerTeamKey());
        assertTrue(restored.hasPendingDeliveryForOrder(id));
    }

    @Test
    void pendingDeliveryReservationsReduceAvailableOrderQuantity() {
        OrderData data = new OrderData();
        UUID orderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID claimantId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        data.add(new Order(orderId, ownerId, "owner", ResourceLocation.parse("minecraft:iron_ingot"),
                11, 0, 0, claimantId, OrderStatus.IN_PROGRESS, 1L));

        assertTrue(data.reserveDelivery(first, orderId, DeliveryRoute.TRAIN, claimantId, 7));
        assertFalse(data.reserveDelivery(UUID.randomUUID(), orderId, DeliveryRoute.BELT, claimantId, 5));
        assertTrue(data.reserveDelivery(second, orderId, DeliveryRoute.BELT, claimantId, 4));
        assertEquals(11L, data.pendingDeliveryCountForOrder(orderId, null));
        assertEquals(4L, data.pendingDeliveryCountForOrder(orderId, first));

        // Progress can be durable before the pending receipt is finalized.
        // That transaction must stop reserving quantity during recovery.
        data.markAutomatedProgressApplied(first);
        assertEquals(4L, data.pendingDeliveryCountForOrder(orderId, null));
    }

    @Test
    void loadFinalizesReceiptWhenDurableProgressMarkerAlreadyExists() {
        OrderData original = new OrderData();
        UUID orderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID claimantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        original.add(new Order(orderId, ownerId, "owner", ResourceLocation.parse("minecraft:iron_ingot"),
                10, 5, 0, claimantId, OrderStatus.IN_PROGRESS, 1L));
        assertTrue(original.reserveDelivery(transactionId, orderId, DeliveryRoute.TRAIN, claimantId, 5));
        original.markAutomatedProgressApplied(transactionId);
        assertFalse(original.deliveryReceipt(transactionId).progressApplied());

        OrderData restored = OrderData.load(original.save(new CompoundTag(), null), null);

        assertTrue(restored.deliveryReceipt(transactionId).progressApplied());
        assertFalse(restored.hasPendingDeliveryForOrder(orderId));
    }

    @Test
    void reservationsRequireTheExactClaimantAndUniqueTransaction() {
        OrderData data = new OrderData();
        UUID orderId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID claimantId = UUID.randomUUID();
        UUID transactionId = UUID.randomUUID();
        data.add(new Order(orderId, ownerId, "owner", ResourceLocation.parse("minecraft:iron_ingot"),
                10, 0, 0, claimantId, OrderStatus.IN_PROGRESS, 1L));

        assertTrue(data.reserveDelivery(transactionId, orderId, DeliveryRoute.TRAIN, claimantId, 6));
        assertFalse(data.reserveDelivery(transactionId, orderId, DeliveryRoute.TRAIN, claimantId, 1));
        assertFalse(data.reserveDelivery(UUID.randomUUID(), orderId, DeliveryRoute.BELT,
                UUID.randomUUID(), 1));
        assertFalse(data.reserveDelivery(UUID.randomUUID(), orderId, DeliveryRoute.BELT,
                claimantId, 5));
        assertFalse(data.reserveDelivery(UUID.randomUUID(), orderId, DeliveryRoute.MANUAL,
                claimantId, 1));
        assertEquals(6L, data.pendingDeliveryCountForOrder(orderId, null));
    }

    @Test
    void legacyReceiptPathAcceptsOnlyManualHandoffs() {
        OrderData data = new OrderData();
        UUID orderId = UUID.randomUUID();

        assertTrue(data.recordDelivery(UUID.randomUUID(), orderId, DeliveryRoute.MANUAL));
        assertFalse(data.recordDelivery(UUID.randomUUID(), orderId, DeliveryRoute.TRAIN));
    }

    @Test
    void malformedAndInconsistentOrdersAreIgnoredDuringLoad() {
        CompoundTag root = new CompoundTag();
        ListTag orders = new ListTag();

        CompoundTag malformedId = validOrder(UUID.randomUUID().toString());
        malformedId.putString("Id", "not-a-uuid");
        orders.add(malformedId);

        CompoundTag invalidCount = validOrder(UUID.randomUUID().toString());
        invalidCount.putInt("Requested", 0);
        orders.add(invalidCount);

        CompoundTag inconsistentStatus = validOrder(UUID.randomUUID().toString());
        inconsistentStatus.putString("Status", OrderStatus.OPEN.name());
        inconsistentStatus.putString("ClaimedBy", UUID.randomUUID().toString());
        orders.add(inconsistentStatus);

        CompoundTag invalidItem = validOrder(UUID.randomUUID().toString());
        invalidItem.putString("Item", "not a valid resource id!");
        orders.add(invalidItem);

        root.put("Orders", orders);
        OrderData loaded = OrderData.load(root, null);

        assertNull(loaded.find(UUID.fromString(invalidCount.getString("Id"))));
        assertNull(loaded.find(UUID.fromString(inconsistentStatus.getString("Id"))));
        assertTrue(loaded.all().isEmpty());
    }

    private static CompoundTag validOrder(String id) {
        CompoundTag order = new CompoundTag();
        order.putString("Id", id);
        order.putString("Owner", UUID.randomUUID().toString());
        order.putString("OwnerName", "owner");
        order.putString("Item", "minecraft:iron_ingot");
        order.putInt("Requested", 16);
        order.putInt("Fulfilled", 0);
        order.putInt("Reward", 2);
        order.putString("Status", OrderStatus.OPEN.name());
        order.putLong("CreatedAt", 1L);
        return order;
    }
}
