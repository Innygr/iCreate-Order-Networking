package com.innygr.icreateordernetworking.compat;

import java.util.HashMap;
import java.util.Map;

import com.innygr.icreateordernetworking.logistics.CreateStockLinkFactoryEndpoint;
import com.innygr.icreateordernetworking.logistics.FactoryEndpointRegistry;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Registers loaded Create Stock Links as read-only factory stock endpoints.
 * It never scans arbitrary chunks; only already-loaded chunk contents and
 * explicit place/break events are observed.
 */
public final class CreateStockLinkEndpointBridge {
    private final Map<String, CreateStockLinkFactoryEndpoint> registered = new HashMap<>();

    public void clear() {
        for (CreateStockLinkFactoryEndpoint endpoint : registered.values())
            FactoryEndpointRegistry.unregister(endpoint);
        registered.clear();
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)
                || !CreateStockLinkCompat.isAvailable()) return;
        // Defer one server task so Create has finished initializing each link's
        // LogisticallyLinkedBehaviour and restored its persisted frequency UUID.
        var positions = chunk.getBlockEntities().values().stream()
                .filter(PackagerLinkBlockEntity.class::isInstance)
                .map(blockEntity -> blockEntity.getBlockPos().immutable())
                .toList();
        level.getServer().execute(() -> positions.forEach(position -> register(level, position)));
    }

    @SubscribeEvent
    public void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !(event.getChunk() instanceof LevelChunk chunk)) return;
        for (var blockEntity : chunk.getBlockEntities().values()) {
            if (blockEntity instanceof PackagerLinkBlockEntity)
                unregister(level, blockEntity.getBlockPos());
        }
    }

    @SubscribeEvent
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)
                || !CreateStockLinkCompat.isAvailable()) return;
        BlockPos position = event.getPos().immutable();
        level.getServer().execute(() -> register(level, position));
    }

    @SubscribeEvent
    public void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level)
            unregister(level, event.getPos());
    }

    private void register(ServerLevel level, BlockPos position) {
        if (!level.getServer().isSameThread() || !level.hasChunkAt(position)
                || !(level.getBlockEntity(position) instanceof PackagerLinkBlockEntity)) return;
        unregister(level, position);
        CreateStockLinkFactoryEndpoint endpoint = new CreateStockLinkFactoryEndpoint(level, position);
        if (endpoint.networkId() == null) return;
        FactoryEndpointRegistry.register(endpoint);
        registered.put(key(level, position), endpoint);
    }

    private void unregister(ServerLevel level, BlockPos position) {
        CreateStockLinkFactoryEndpoint endpoint = registered.remove(key(level, position));
        if (endpoint != null) FactoryEndpointRegistry.unregister(endpoint);
    }

    private static String key(ServerLevel level, BlockPos position) {
        return level.dimension().location() + ":" + position.asLong();
    }
}
