package com.innygr.icreateordernetworking.logistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Item;

/**
 * Explicit registry of live factory endpoints.
 *
 * Terminals query this registry rather than scanning chunks or the entire world.
 * Endpoint implementations must expose only stock that is actually available
 * for reservation; production capacity is deliberately a separate concern.
 *
 * All calls are expected to run on the logical server thread.
 */
public final class FactoryEndpointRegistry {
    private static final Map<EndpointKey, FactoryEndpoint> ENDPOINTS = new HashMap<>();

    private FactoryEndpointRegistry() {}

    /**
     * Register or replace the endpoint at a dimension/position pair.
     * A block entity should register when it becomes usable and unregister
     * when removed or unloaded.
     */
    public static void register(FactoryEndpoint endpoint) {
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(endpoint.dimension(), "endpoint dimension");
        Objects.requireNonNull(endpoint.position(), "endpoint position");
        Objects.requireNonNull(endpoint.networkId(), "endpoint network id");
        ENDPOINTS.put(key(endpoint.dimension(), endpoint.position()), endpoint);
    }

    /**
     * Remove an endpoint only if the currently registered instance matches.
     * This prevents a stale block entity's unload callback from removing a
     * newer endpoint registered at the same position.
     */
    public static void unregister(FactoryEndpoint endpoint) {
        if (endpoint == null || endpoint.dimension() == null || endpoint.position() == null) return;
        ENDPOINTS.remove(key(endpoint.dimension(), endpoint.position()), endpoint);
    }

    /** Find currently available stock on the same dimension and network. */
    public static List<StockOffer> findStock(ServerLevel level, UUID networkId,
                                             BlockPos requester, Item item) {
        if (level == null || networkId == null || requester == null || item == null) {
            return List.of();
        }

        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(item);
        ResourceKey<Level> dimension = level.dimension();
        List<StockOffer> offers = new ArrayList<>();

        for (Map.Entry<EndpointKey, FactoryEndpoint> entry : ENDPOINTS.entrySet()) {
            EndpointKey endpointKey = entry.getKey();
            FactoryEndpoint endpoint = entry.getValue();
            if (!endpointKey.dimension().equals(dimension)
                    || !networkId.equals(endpoint.networkId())) {
                continue;
            }

            int available;
            try {
                available = Math.max(0, endpoint.availableCount(itemId));
            } catch (RuntimeException ignored) {
                // A broken/unavailable endpoint must not prevent other factories
                // from being considered.
                continue;
            }
            if (available == 0) continue;

            BlockPos source = endpoint.position();
            offers.add(new StockOffer(source, itemId, available,
                    source.distSqr(requester), endpoint));
        }

        offers.sort(Comparator.comparingDouble(StockOffer::distanceSquared)
                .thenComparing(offer -> offer.position().asLong()));
        return List.copyOf(offers);
    }

    /** Clear transient registrations during server shutdown/reload. */
    public static void clear() {
        ENDPOINTS.clear();
    }

    private static EndpointKey key(ResourceKey<Level> dimension, BlockPos position) {
        return new EndpointKey(dimension, BlockPos.asLong(position.getX(), position.getY(), position.getZ()));
    }

    private record EndpointKey(ResourceKey<Level> dimension, long packedPosition) {
        private BlockPos position() {
            return BlockPos.of(packedPosition);
        }
    }

    /** Read-only candidate returned to a terminal's sourcing planner. */
    public record StockOffer(BlockPos position, ResourceLocation itemId, int availableCount,
                             double distanceSquared, FactoryEndpoint endpoint) {
        public StockOffer {
            position = position.immutable();
        }
    }

    /**
     * Implemented by inventories/factory blocks that participate in logistics.
     * availableCount must exclude stock already reserved by other requests.
     */
    public interface FactoryEndpoint {
        ResourceKey<Level> dimension();
        BlockPos position();
        UUID networkId();
        int availableCount(ResourceLocation itemId);
    }
}
