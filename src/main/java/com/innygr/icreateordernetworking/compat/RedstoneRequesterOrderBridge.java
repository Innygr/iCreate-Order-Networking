package com.innygr.icreateordernetworking.compat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.innygr.icreateordernetworking.blocks.OrderTerminalBlockEntity;
import com.innygr.icreateordernetworking.orders.OrderService;
import com.innygr.icreateordernetworking.orders.OrderTeamAccess;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.redstoneRequester.RedstoneRequesterBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Bridges Create's built-in Redstone Requester to iCreate's order board.
 * A powered requester directly beside an accessible Order Terminal creates
 * one no-reward order per requested item. Existing active orders suppress repeats.
 */
public final class RedstoneRequesterOrderBridge {
    private final Set<String> latchedRequesters = new HashSet<>();

    @net.neoforged.bus.api.SubscribeEvent
    public void onNeighborNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof RedstoneRequesterBlockEntity requester)) return;

        String key = level.dimension().location() + ":" + pos.asLong();
        boolean powered = level.getBlockState(pos).hasProperty(BlockStateProperties.POWERED)
                && level.getBlockState(pos).getValue(BlockStateProperties.POWERED);
        if (!powered) {
            latchedRequesters.remove(key);
            return;
        }
        if (latchedRequesters.contains(key)) return;
        if (createOrders(level, pos, requester)) latchedRequesters.add(key);
    }

    private static boolean createOrders(ServerLevel level, BlockPos requesterPos,
                                        RedstoneRequesterBlockEntity requester) {
        if (requester.encodedRequest == null || requester.encodedRequest.isEmpty()) return false;

        OrderTerminalBlockEntity terminal = null;
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(requesterPos.relative(direction)) instanceof OrderTerminalBlockEntity candidate
                    && candidate.isConfigured()) {
                terminal = candidate;
                break;
            }
        }
        if (terminal == null) return false;

        UUID ownerId = terminal.getFactoryOwnerId();
        if (ownerId == null) return false;
        ServerPlayer onlineOwner = level.getServer().getPlayerList().getPlayer(ownerId);
        String ownerName = onlineOwner == null ? "Factory " + ownerId.toString().substring(0, 8)
                : onlineOwner.getGameProfile().getName();
        String ownerTeamKey = onlineOwner == null ? null : OrderTeamAccess.teamKey(onlineOwner);

        boolean hasValidRequest = false;
        for (BigItemStack entry : requester.encodedRequest.stacks()) {
            if (entry == null || entry.stack == null || entry.stack.isEmpty()
                    || entry.count < 1 || entry.count > OrderService.MAX_ORDER_COUNT) continue;
            var itemId = BuiltInRegistries.ITEM.getKey(entry.stack.getItem());
            if (itemId == null || itemId.equals(BuiltInRegistries.ITEM.getDefaultKey())) continue;
            hasValidRequest = true;
            OrderService.createRestockOrder(level, ownerId, ownerName, ownerTeamKey, itemId, entry.count);
        }
        return hasValidRequest;
    }
}
