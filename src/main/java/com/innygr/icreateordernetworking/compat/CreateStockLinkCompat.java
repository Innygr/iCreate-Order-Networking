package com.innygr.icreateordernetworking.compat;

import java.util.List;
import java.util.UUID;

import com.simibubi.create.content.logistics.packager.InventorySummary;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Server-side read-only bridge to Create's Stock Link (Packager Link).
 *
 * A Stock Link reports the currently available stock of the Packager it links
 * to. It is not itself a cargo inventory and must not be treated as proof that
 * an item was dispatched or received. Shipment reservation, dispatch, and
 * receipt remain owned by the durable delivery transaction.
 */
public final class CreateStockLinkCompat {
    private CreateStockLinkCompat() {}

    public static boolean isAvailable() {
        return CreateCompat.isCreateLoaded()
                && BuiltInRegistries.BLOCK.containsKey(
                        ResourceLocation.fromNamespaceAndPath("create", "stock_link"));
    }

    /**
     * Return the Create logistics frequency at a loaded Stock Link.
     * This frequency is the identity of the Create logistics network, not an
     * iCreate factory UUID; callers must explicitly bind the two network scopes.
     */
    public static UUID networkIdAt(ServerLevel level, BlockPos position) {
        PackagerLinkBlockEntity link = getLink(level, position);
        if (link == null || link.behaviour == null) return null;
        return link.behaviour.freqId;
    }

    /**
     * Read the linked Packager's live available count for an item. Create's
     * own summary excludes unavailable/unlinked Packagers; missing or disabled
     * links safely report zero rather than inventing stock.
     */
    public static int availableCountAt(ServerLevel level, BlockPos position, Item item) {
        if (item == null) return 0;
        PackagerLinkBlockEntity link = getLink(level, position);
        if (link == null || link.behaviour == null) return 0;
        try {
            InventorySummary summary = link.behaviour.getSummary(null);
            return Math.max(0, summary.getCountOf(new ItemStack(item)));
        } catch (RuntimeException | LinkageError ignored) {
            return 0;
        }
    }

    /** Read a stable snapshot of the linked Packager's current stock summary. */
    public static List<StockEntry> stockAt(ServerLevel level, BlockPos position) {
        PackagerLinkBlockEntity link = getLink(level, position);
        if (link == null || link.behaviour == null) return List.of();
        try {
            return link.behaviour.getSummary(null).getStacks().stream()
                    .filter(entry -> entry != null && entry.stack != null && !entry.stack.isEmpty()
                            && entry.count > 0)
                    .map(entry -> new StockEntry(
                            BuiltInRegistries.ITEM.getKey(entry.stack.getItem()),
                            entry.stack.copyWithCount(1), entry.count))
                    .filter(entry -> entry.itemId() != null)
                    .toList();
        } catch (RuntimeException | LinkageError ignored) {
            return List.of();
        }
    }

    private static PackagerLinkBlockEntity getLink(ServerLevel level, BlockPos position) {
        if (level == null || position == null || !isAvailable()
                || !level.getServer().isSameThread() || !level.hasChunkAt(position)) return null;
        if (!(level.getBlockEntity(position) instanceof PackagerLinkBlockEntity link)
                || link.isRemoved() || link.isChunkUnloaded()) return null;
        return link;
    }

    public record StockEntry(ResourceLocation itemId, ItemStack representativeStack, int count) {
        public StockEntry {
            representativeStack = representativeStack.copy();
            if (count < 0) throw new IllegalArgumentException("count cannot be negative");
        }

        @Override
        public ItemStack representativeStack() {
            return representativeStack.copy();
        }
    }
}
