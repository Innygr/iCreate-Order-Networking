package com.innygr.icreateordernetworking.network;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server-authoritative Terminal Logger face configuration. Mode -1 claims/opens; 0=input, 1=output, 2=disabled. */
public record TerminalLoggerFaceActionPacket(BlockPos pos, int direction, int mode) implements CustomPacketPayload {
    public static final Type<TerminalLoggerFaceActionPacket> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath("icreate_order_networking", "terminal_logger_face"));
    public static final StreamCodec<RegistryFriendlyByteBuf, TerminalLoggerFaceActionPacket> STREAM_CODEC =
            StreamCodec.composite(BlockPos.STREAM_CODEC, TerminalLoggerFaceActionPacket::pos,
                    ByteBufCodecs.VAR_INT, TerminalLoggerFaceActionPacket::direction,
                    ByteBufCodecs.VAR_INT, TerminalLoggerFaceActionPacket::mode,
                    TerminalLoggerFaceActionPacket::new);

    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }

    public static void handle(TerminalLoggerFaceActionPacket packet, IPayloadContext context) {
        if (!(context.player() instanceof ServerPlayer player)) return;
        context.enqueueWork(() -> {
            if (player.level().isClientSide || player.distanceToSqr(packet.pos().getX() + 0.5,
                    packet.pos().getY() + 0.5, packet.pos().getZ() + 0.5) > 64.0
                    || packet.direction() < 0 || packet.direction() >= Direction.values().length
                    || packet.mode() < -1 || packet.mode() > 2
                    || !(player.level().getBlockEntity(packet.pos()) instanceof TerminalLoggerBlockEntity logger)) {
                player.displayClientMessage(Component.literal("Terminal Logger action rejected: invalid target or out of reach."), true);
                return;
            }
            if (!logger.isConfigured()) {
                if (!logger.bindOwnerIfUnconfigured(player.getUUID())) {
                    player.displayClientMessage(Component.literal("Terminal Logger could not be claimed."), true);
                    return;
                }
                player.displayClientMessage(Component.literal("Terminal Logger claimed."), true);
            }
            if (!logger.hasAccess(player.getUUID())) {
                player.displayClientMessage(Component.literal("You do not have access to this Terminal Logger."), true);
                return;
            }
            if (packet.mode() == -1) return;
            Direction side = Direction.values()[packet.direction()];
            if (!logger.setFaceMode(side, packet.mode())) {
                player.displayClientMessage(Component.literal("Terminal Logger face setting was rejected."), true);
                return;
            }
            String mode = switch (packet.mode()) {
                case 0 -> "INPUT";
                case 1 -> "OUTPUT";
                default -> "DISABLED";
            };
            player.displayClientMessage(Component.literal("Terminal Logger " + side.getName() + ": " + mode), true);
        });
    }
}