package com.innygr.icreateordernetworking.client;

import com.innygr.icreateordernetworking.ICreateOrderNetworking;
import com.innygr.icreateordernetworking.blocks.OrderTerminalBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Client-only input hook, kept out of common block classes for dedicated-server safety. */
@EventBusSubscriber(modid = ICreateOrderNetworking.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class OrderTerminalClientEvents {
    private OrderTerminalClientEvents() {}

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!event.getLevel().isClientSide || event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().isEmpty()) return;
        BlockState state = event.getLevel().getBlockState(event.getPos());
        if (!(state.getBlock() instanceof OrderTerminalBlock)) return;

        BlockPos terminalPos = state.getValue(OrderTerminalBlock.HALF)
                == net.minecraft.world.level.block.state.properties.DoubleBlockHalf.UPPER
                ? event.getPos().below() : event.getPos();
        Minecraft.getInstance().setScreen(new OrderTerminalScreen(terminalPos));
    }
}
