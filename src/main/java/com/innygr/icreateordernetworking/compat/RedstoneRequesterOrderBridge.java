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

        // NeighborNotifyEvent identifies the block sending the notification, not
        // necessarily the requester receiving it. Inspect the source and adjacent
        // positions so both a redstone update and a newly placed terminal can retry.
        BlockPos sourcePos = event.getPos();
        processRequester(level, sourcePos);
        for (Direction direction : Direction.values()) {
            processRequester(level, sourcePos.relative(direction));
        }
    }

    private void processRequester(ServerLevel level, BlockPos pos) {
        String key = level.dimension().location() + ":" + pos.asLong();
        if (!(level.getBlockEntity(pos) instanceof RedstoneRequesterBlockEntity requester)) {
            // A requester may be broken while powered. Drop its latch when a
            // nearby notification reaches that position, avoiding stale entries
            // and allowing a replacement requester to activate normally.
            latchedRequesters.remove(key);
            return;
        }

        var state = level.getBlockState(pos);
        boolean powered = state.hasProperty(BlockStateProperties.POWERED)
                && state.getValue(BlockStateProperties.POWERED);
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
        String ownerName;
        String ownerTeamKey;
        if (onlineOwner != null) {
            ownerName = onlineOwner.getGameProfile().getName();
            ownerTeamKey = OrderTeamAccess.teamKey(onlineOwner);
        } else {
            // Offline factory owners still have cached profiles on most servers.
            // Resolve their scoreboard team by cached name instead of silently
            // turning a team order into a private order whenever they log out.
            var cachedProfile = level.getServer().getProfileCache().get(ownerId);
            ownerName = cachedProfile.map(profile -> profile.getName())
                    .orElse("Factory " + ownerId.toString().substring(0, 8));
            ownerTeamKey = cachedProfile
                    .map(profile -> level.getServer().getScoreboard()
                            .getPlayersTeam(profile.getName()))
                    .map(team -> team.getName())
                    .orElse(null);
        }

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
