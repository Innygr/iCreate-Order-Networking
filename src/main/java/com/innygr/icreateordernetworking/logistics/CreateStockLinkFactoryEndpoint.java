package com.innygr.icreateordernetworking.logistics;

import com.innygr.icreateordernetworking.compat.CreateStockLinkCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

/** Live, read-only stock offer backed by one Create Stock Link. */
public final class CreateStockLinkFactoryEndpoint implements FactoryEndpointRegistry.FactoryEndpoint {
    private final ServerLevel level;
    private final BlockPos position;

    public CreateStockLinkFactoryEndpoint(ServerLevel level, BlockPos position) {
        if (level == null || position == null) throw new IllegalArgumentException("level and position are required");
        this.level = level;
        this.position = position.immutable();
    }

    @Override
    public ResourceKey<Level> dimension() {
        return level.dimension();
    }

    @Override
    public BlockPos position() {
        return position;
    }

    @Override
    public java.util.UUID networkId() {
        return CreateStockLinkCompat.networkIdAt(level, position);
    }

    @Override
    public int availableCount(ResourceLocation itemId) {
        if (itemId == null || !level.getServer().isSameThread()) return 0;
        Item item = BuiltInRegistries.ITEM.get(itemId);
        if (item == null || item == BuiltInRegistries.ITEM.get(BuiltInRegistries.ITEM.getDefaultKey())) return 0;
        return CreateStockLinkCompat.availableCountAt(level, position, item);
    }
}
