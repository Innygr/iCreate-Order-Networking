package com.innygr.icreateordernetworking.orders;

import java.util.UUID;
import net.minecraft.resources.ResourceLocation;

/** Order state. Mutations are package-private so gameplay changes go through server rules. */
public final class Order {
    private final UUID id;
    private final UUID ownerId;
    private final String ownerName;
    private final ResourceLocation itemId;
    private final int requestedCount;
    private int fulfilledCount;
    private final int rewardPerItem;
    private UUID claimedBy;
    private OrderStatus status;
    private final long createdAt;

    public Order(UUID id, UUID ownerId, String ownerName, ResourceLocation itemId,
                 int requestedCount, int fulfilledCount, int rewardPerItem,
                 UUID claimedBy, OrderStatus status, long createdAt) {
        this.id = id; this.ownerId = ownerId; this.ownerName = ownerName; this.itemId = itemId;
        this.requestedCount = requestedCount; this.fulfilledCount = fulfilledCount;
        this.rewardPerItem = rewardPerItem; this.claimedBy = claimedBy;
        this.status = status; this.createdAt = createdAt;
    }

    public UUID id() { return id; }
    public UUID ownerId() { return ownerId; }
    public String ownerName() { return ownerName; }
    public ResourceLocation itemId() { return itemId; }
    public int requestedCount() { return requestedCount; }
    public int fulfilledCount() { return fulfilledCount; }
    public int remainingCount() { return requestedCount - fulfilledCount; }
    public int rewardPerItem() { return rewardPerItem; }
    public UUID claimedBy() { return claimedBy; }
    public OrderStatus status() { return status; }
    public long createdAt() { return createdAt; }

    void claim(UUID playerId) { claimedBy = playerId; status = OrderStatus.IN_PROGRESS; }
    void fulfil(int count) {
        fulfilledCount += count;
        status = fulfilledCount >= requestedCount ? OrderStatus.COMPLETED : OrderStatus.IN_PROGRESS;
    }
    void cancel() { status = OrderStatus.CANCELLED; }
}
