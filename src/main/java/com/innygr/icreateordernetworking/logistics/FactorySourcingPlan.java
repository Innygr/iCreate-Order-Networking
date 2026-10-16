package com.innygr.icreateordernetworking.logistics;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;

/**
 * A sourcing decision that prioritizes currently available factory stock.
 *
 * This is a plan only: callers must reserve/revalidate each allocation before
 * extracting cargo. The shortfall is the only quantity that should be offered
 * to remote suppliers or turned into an external network request.
 */
public record FactorySourcingPlan(
        int requestedCount,
        List<Allocation> allocations,
        int locallyAvailable,
        int shortfall) {

    public FactorySourcingPlan {
        if (requestedCount < 0 || locallyAvailable < 0 || shortfall < 0
                || locallyAvailable + shortfall != requestedCount) {
            throw new IllegalArgumentException("Inconsistent sourcing plan quantities");
        }
        allocations = List.copyOf(allocations);
    }

    /**
     * Build a sourcing plan against the Create logistics frequency represented
     * by a specific Stock Link. The link's frequency is the network scope; no
     * cargo is reserved or moved by planning.
     */
    public static FactorySourcingPlan createForStockLink(ServerLevel level, BlockPos stockLink,
                                                          BlockPos requester, Item item, int requestedCount) {
        if (requestedCount < 0) {
            throw new IllegalArgumentException("Requested count cannot be negative");
        }
        UUID networkId = com.innygr.icreateordernetworking.compat.CreateStockLinkCompat
                .networkIdAt(level, stockLink);
        if (networkId == null) {
            return new FactorySourcingPlan(requestedCount, List.of(), 0, requestedCount);
        }
        return create(level, networkId, requester, item, requestedCount);
    }

    /**
     * Authorization-enforcing entry point for actual order sourcing. Unlike the
     * discovery-only overload, this requires an explicit persisted Stock Link to
     * factory binding and revalidates the actor's current owner/team access.
     */
    public static FactorySourcingPlan createForAuthorizedStockLink(ServerLevel level, BlockPos stockLink,
                                                                    BlockPos requester, Item item,
                                                                    int requestedCount, UUID actorId) {
        if (requestedCount < 0) {
            throw new IllegalArgumentException("Requested count cannot be negative");
        }
        if (level == null || actorId == null || stockLink == null) {
            return new FactorySourcingPlan(requestedCount, List.of(), 0, requestedCount);
        }
        StockLinkBindingData.Binding binding = StockLinkBindingData.get(level)
                .authorizedBinding(level, stockLink, actorId);
        if (binding == null) {
            return new FactorySourcingPlan(requestedCount, List.of(), 0, requestedCount);
        }
        UUID networkId = binding.createNetworkId();
        return create(level, networkId, requester, item, requestedCount);
    }

    public static FactorySourcingPlan create(ServerLevel level, UUID networkId,
                                             BlockPos requester, Item item, int requestedCount) {
        if (requestedCount < 0) {
            throw new IllegalArgumentException("Requested count cannot be negative");
        }
        if (requestedCount == 0) {
            return java.util.Optional.empty();
        }

        List<Allocation> allocations = new ArrayList<>();
        int remaining = requestedCount;
        for (FactoryEndpointRegistry.StockOffer offer :
                FactoryEndpointRegistry.findStock(level, networkId, requester, item)) {
            if (remaining == 0) break;
            int amount = Math.min(remaining, offer.availableCount());
            if (amount <= 0) continue;
            allocations.add(new Allocation(offer, amount));
            remaining -= amount;
        }

        int locallyAvailable = requestedCount - remaining;
        return new FactorySourcingPlan(requestedCount, allocations, locallyAvailable, remaining);
    }

    public record Allocation(FactoryEndpointRegistry.StockOffer source, int count) {
        public Allocation {
            if (source == null || count < 1 || count > source.availableCount()) {
                throw new IllegalArgumentException("Invalid factory stock allocation");
            }
        }
    }

    public boolean fullySourcedLocally() {
        return shortfall == 0;
    }

    /**
     * Build an order-bound sourcing plan only for the current claimant of an active order.
     * This is deliberately planning-only: adapters must separately reserve and verify cargo
     * before any shipment is dispatched.
     */
    public static java.util.Optional<FactorySourcingPlan> createForAuthorizedOrder(ServerLevel level, UUID orderId,
            BlockPos stockLink, BlockPos requester, UUID actorId) {
        if (level == null || orderId == null || stockLink == null || actorId == null
                || level.getServer() == null || !level.getServer().isSameThread()) {
            return java.util.Optional.empty();
        }
        com.innygr.icreateordernetworking.orders.Order order =
                com.innygr.icreateordernetworking.orders.OrderData.get(level).find(orderId);
        if (order == null
                || (order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.OPEN
                    && order.status() != com.innygr.icreateordernetworking.orders.OrderStatus.IN_PROGRESS)
                || !actorId.equals(order.claimedBy())
                || order.remainingCount() < 1) {
            return java.util.Optional.empty();
        }
        net.minecraft.world.item.Item item =
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(order.itemId());
        if (item == null || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item)
                .equals(net.minecraft.core.registries.BuiltInRegistries.ITEM.getDefaultKey())) {
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(createForAuthorizedStockLink(level, stockLink, requester, item,
                order.remainingCount(), actorId));
    }
}
