package com.innygr.icreateordernetworking.blocks;

import com.innygr.icreateordernetworking.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;

/** Factory-scoped statistics display configuration block. */
public final class NetworkStatisticsBlock extends BaseEntityBlock {
    private static final ResourceLocation CREATE_WRENCH =
            ResourceLocation.fromNamespaceAndPath("create", "wrench");

    public NetworkStatisticsBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public MapCodec<NetworkStatisticsBlock> codec() {
        return MapCodec.unit(this);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkStatisticsBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return null;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                               BlockPos pos, Player player,
                                               net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NetworkStatisticsBlockEntity stats)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) return ItemInteractionResult.SUCCESS;

        if (!stats.isConfigured()) {
            if (stats.claimIfUnconfigured(player.getUUID())) {
                player.displayClientMessage(Component.literal("Network Statistics block claimed. Factory binding is ready for the Order Terminal interaction."), true);
                return ItemInteractionResult.SUCCESS;
            }
        }
        if (!stats.hasAccess(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this Network Statistics block."), true);
            return ItemInteractionResult.FAIL;
        }

        ResourceLocation heldItem = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (CREATE_WRENCH.equals(heldItem)) {
            if (stats.getMode() == NetworkStatisticsBlockEntity.Mode.TEAM_GLOBAL) {
                stats.setFactoryMode(player.getUUID());
                player.displayClientMessage(Component.literal("Network Statistics mode: Factory"), true);
            } else if (stats.setTeamGlobal(player.getUUID())) {
                player.displayClientMessage(Component.literal("Network Statistics mode: Team Global"), true);
            } else {
                player.displayClientMessage(Component.literal("Team Global requires a valid FTB party team."), true);
            }
            return ItemInteractionResult.SUCCESS;
        }

        reportStats(player, stats);
        return ItemInteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NetworkStatisticsBlockEntity stats)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!stats.isConfigured()) {
            if (stats.claimIfUnconfigured(player.getUUID())) {
                player.displayClientMessage(Component.literal("Network Statistics block claimed."), true);
                return InteractionResult.SUCCESS;
            }
        }
        if (!stats.hasAccess(player.getUUID())) {
            player.displayClientMessage(Component.literal("You do not have access to this Network Statistics block."), true);
            return InteractionResult.FAIL;
        }
        reportStats(player, stats);
        return InteractionResult.SUCCESS;
    }

    private static void reportStats(Player player, NetworkStatisticsBlockEntity stats) {
        if (stats.getMode() == NetworkStatisticsBlockEntity.Mode.FACTORY && stats.getFactoryId() == null) {
            player.displayClientMessage(Component.literal("No factory selected. Right-click the relevant Order Terminal to bind this display."), true);
            return;
        }
        net.minecraft.world.level.Level level = player.level();
        if (level.isClientSide) return;
        com.innygr.icreateordernetworking.logistics.NetworkStatsData.Snapshot snapshot =
                stats.getStats(player.getUUID());
        if (snapshot == null) {
            player.displayClientMessage(Component.literal("Statistics unavailable: factory/team access is missing or no transfer data has been recorded."), true);
            return;
        }
        player.displayClientMessage(Component.literal("Network Stats [" + stats.getMode().name()
                + "] In: " + snapshot.totalInputItems()
                + " | Out: " + snapshot.totalOutputItems()
                + " | Rejected events: " + snapshot.rejectedTransfers()), true);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
