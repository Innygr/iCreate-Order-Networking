package com.innygr.icreateordernetworking.client;

import com.innygr.icreateordernetworking.blocks.TerminalLoggerBlockEntity;
import com.innygr.icreateordernetworking.network.TerminalLoggerFaceActionPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Six-face routing panel for an individual Terminal Logger. */
public final class TerminalLoggerScreen extends Screen {
    private final BlockPos pos;

    public TerminalLoggerScreen(BlockPos pos) {
        super(Component.literal("Terminal Logger"));
        this.pos = pos.immutable();
    }

    @Override
    protected void init() {
        int left = (width - 250) / 2;
        int top = (height - 190) / 2;
        for (Direction direction : Direction.values()) {
            int column = direction.ordinal() % 2;
            int row = direction.ordinal() / 2;
            addRenderableWidget(Button.builder(label(direction, currentMode(direction)), button -> {
                int next = (currentMode(direction) + 1) % 3;
                PacketDistributor.sendToServer(new TerminalLoggerFaceActionPacket(pos, direction.ordinal(), next));
                button.setMessage(label(direction, next));
            }).bounds(left + column * 128, top + 42 + row * 31, 120, 24).build());
        }
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(left + 65, top + 145, 120, 22).build());
        PacketDistributor.sendToServer(new TerminalLoggerFaceActionPacket(pos, 0, -1));
    }

    private int currentMode(Direction direction) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof TerminalLoggerBlockEntity logger) {
            return logger.getFaceMode(direction);
        }
        return 0;
    }

    private static Component label(Direction direction, int mode) {
        String state = switch (mode) {
            case 1 -> "OUTPUT";
            case 2 -> "DISABLED";
            default -> "INPUT";
        };
        return Component.literal(direction.getName().toUpperCase(java.util.Locale.ROOT) + "  |  " + state);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = (width - 250) / 2;
        int top = (height - 190) / 2;
        graphics.fill(left, top, left + 250, top + 190, 0xFF0B1118);
        graphics.fill(left + 2, top + 2, left + 248, top + 32, 0xFF28333E);
        graphics.fill(left + 2, top + 32, left + 248, top + 34, 0xFFFFB347);
        graphics.drawCenteredString(font, "TERMINAL LOGGER // FACE ROUTING", width / 2, top + 11, 0xFFFFB347);
        graphics.drawCenteredString(font, "Click: INPUT → OUTPUT → DISABLED", width / 2, top + 174, 0xFFB7C0C9);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override public boolean isPauseScreen() { return false; }
}