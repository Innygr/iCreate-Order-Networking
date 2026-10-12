package com.innygr.icreateordernetworking.delivery;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of verified server-side transport adapters.
 *
 * Register adapters during common setup after checking the owning mod/API.
 * This registry intentionally does not infer that a route is implemented just
 * because the corresponding mod is installed.
 */
public final class AutomatedDeliveryRegistry {
    private static final Map<DeliveryRoute, AutomatedDeliveryAdapter> ADAPTERS =
            new EnumMap<>(DeliveryRoute.class);

    private AutomatedDeliveryRegistry() {}

    /** Returns false if the adapter is null, unavailable, or its route is already registered. */
    public static synchronized boolean register(AutomatedDeliveryAdapter adapter) {
        if (adapter == null || adapter.route() == null || !adapter.isAvailable()
                || ADAPTERS.containsKey(adapter.route())) {
            return false;
        }
        ADAPTERS.put(adapter.route(), adapter);
        return true;
    }

    /** Removes an adapter only if the registered instance matches. */
    public static synchronized boolean unregister(DeliveryRoute route, AutomatedDeliveryAdapter adapter) {
        if (route == null || adapter == null || ADAPTERS.get(route) != adapter) return false;
        ADAPTERS.remove(route);
        return true;
    }

    public static synchronized Optional<AutomatedDeliveryAdapter> find(DeliveryRoute route) {
        if (route == null) return Optional.empty();
        AutomatedDeliveryAdapter adapter = ADAPTERS.get(route);
        if (adapter == null || !adapter.isAvailable()) return Optional.empty();
        return Optional.of(adapter);
    }

    public static synchronized Collection<DeliveryRoute> availableRoutes() {
        EnumMap<DeliveryRoute, AutomatedDeliveryAdapter> snapshot =
                new EnumMap<>(DeliveryRoute.class);
        ADAPTERS.forEach((route, adapter) -> {
            if (adapter.isAvailable()) snapshot.put(route, adapter);
        });
        return Collections.unmodifiableSet(snapshot.keySet());
    }

    /** Clears optional integrations on lifecycle shutdown/reload. */
    public static synchronized void clear() {
        ADAPTERS.clear();
    }
}
