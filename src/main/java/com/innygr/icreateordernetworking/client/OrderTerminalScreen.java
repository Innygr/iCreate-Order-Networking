package com.innygr.icreateordernetworking.client;

import java.util.UUID;

import com.innygr.icreateordernetworking.network.AutomatedDeliveryPacket;
import com.innygr.icreateordernetworking.network.OrderTerminalActionPacket;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Code-drawn industrial control panel for the order network.
 * The client submits requests only; packet handlers remain responsible for
 * server-side authorization, range checks, order state, and cargo validation.
 */
public final class OrderTerminalScreen extends Screen {
    private static final int PANEL_WIDTH = 356;
    private static final int PANEL_HEIGHT = 300;
    private static final int AMBER = 0xFFFFB347;
    private static final int MUTED = 0xFFB7C0C9;
    private static final int PANEL = 0xF0182029;
    private static final int INSET = 0xFF111820;

    private final BlockPos terminalPos;
    private EditBox orderId;
    private EditBox itemId;
    private EditBox count;
    private EditBox reward;
    private EditBox source;
    private EditBox destination;
    private EditBox route;
    private String feedback = "";
    private boolean feedbackError;

    public OrderTerminalScreen(BlockPos terminalPos) {
        super(Component.literal("Order Terminal"));
        this.terminalPos = terminalPos.immutable();
    }

    @Override
    protected void init() {
        int left = (width - 340) / 2;
        int top = (height - 282) / 2;

        orderId = addRenderableWidget(new EditBox(font, left, top + 35, 340, 18,
                Component.literal("Order UUID")));
        orderId.setMaxLength(36);

        itemId = addRenderableWidget(new EditBox(font, left, top + 62, 185, 18,
                Component.literal("Item ID")));
        itemId.setMaxLength(256);
        itemId.setValue("minecraft:stone");

        count = addRenderableWidget(new EditBox(font, left + 192, top + 62, 65, 18,
                Component.literal("Count")));
        count.setMaxLength(5);
        count.setValue("1");

        reward = addRenderableWidget(new EditBox(font, left + 264, top + 62, 76, 18,
                Component.literal("Reward")));
        reward.setMaxLength(7);
        reward.setValue("0");

        source = addRenderableWidget(new EditBox(font, left, top + 116, 165, 18,
                Component.literal("Source x,y,z")));
        source.setMaxLength(64);
        source.setValue("0,64,0");

        destination = addRenderableWidget(new EditBox(font, left + 175, top + 116, 165, 18,
                Component.literal("Destination x,y,z")));
        destination.setMaxLength(64);
        destination.setValue("10,64,10");

        route = addRenderableWidget(new EditBox(font, left, top + 170, 165, 18,
                Component.literal("Route")));
        route.setMaxLength(32);
        route.setValue("TRAIN");

        addRenderableWidget(Button.builder(Component.literal("CREATE"), button -> createOrder())
                .bounds(left, top + 205, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLAIM"), button -> sendOrderAction(
                        OrderTerminalActionPacket.CLAIM))
                .bounds(left + 86, top + 205, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("FULFIL"), button -> fulfilOrder())
                .bounds(left + 168, top + 205, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CANCEL"), button -> sendOrderAction(
                        OrderTerminalActionPacket.CANCEL))
                .bounds(left + 250, top + 205, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("DISPATCH SHIPMENT"), button -> dispatchShipment())
                .bounds(left, top + 232, 165, 20).build());
        addRenderableWidget(Button.builder(Component.literal("CLOSE"), button -> onClose())
                .bounds(left + 175, top + 232, 165, 20).build());
        setInitialFocus(orderId);
        // Fetch the server-authoritative order snapshot when the terminal opens.
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos,
                OrderTerminalActionPacket.OPEN, "", 0, 0, "", ""));
    }

    private void createOrder() {
        Integer requestedCount = parseInteger(count.getValue(), 1,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT);
        Integer rewardPerItem = parseInteger(reward.getValue(), 0,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_REWARD_PER_ITEM);
        if (requestedCount == null || rewardPerItem == null || itemId.getValue().isBlank()) {
            report("Invalid item ID, count, or reward.", true);
            return;
        }
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos,
                OrderTerminalActionPacket.CREATE, itemId.getValue().trim(), requestedCount,
                rewardPerItem, "", ""));
        report("Create request sent to server.", false);
    }

    private void sendOrderAction(int action) {
        if (!validOrderId()) return;
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos, action,
                "", 0, 0, orderId.getValue().trim(), ""));
        report("Order action sent to server.", false);
    }

    private void fulfilOrder() {
        if (!validOrderId()) return;
        Integer requestedCount = parseInteger(count.getValue(), 1,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT);
        if (requestedCount == null) {
            report("Enter a valid fulfilment count.", true);
            return;
        }
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos,
                OrderTerminalActionPacket.FULFIL, "", requestedCount, 0,
                orderId.getValue().trim(), UUID.randomUUID().toString()));
        report("Fulfilment request sent to server.", false);
    }

    private void dispatchShipment() {
        if (!validOrderId()) return;
        Integer requestedCount = parseInteger(count.getValue(), 1,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT);
        String routeName = route.getValue().trim().toUpperCase(java.util.Locale.ROOT);
        if (requestedCount == null || source.getValue().isBlank() || destination.getValue().isBlank()) {
            report("Enter a valid count and both endpoint coordinates.", true);
            return;
        }
        String sourceReference = source.getValue().trim() + ">" + destination.getValue().trim();
        PacketDistributor.sendToServer(new AutomatedDeliveryPacket(terminalPos,
                orderId.getValue().trim(), requestedCount, routeName, sourceReference,
                UUID.randomUUID().toString()));
        report("Shipment request sent to server.", false);
    }

    private boolean validOrderId() {
        try {
            UUID.fromString(orderId.getValue().trim());
            return true;
        } catch (IllegalArgumentException exception) {
            report("Enter the full order UUID first.", true);
            return false;
        }
    }

    private void report(String message, boolean error) {
        feedback = message;
        feedbackError = error;
    }

    private static Integer parseInteger(String value, int minimum, int maximum) {
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed >= minimum && parsed <= maximum ? parsed : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);

        int panelLeft = (width - PANEL_WIDTH) / 2;
        int panelTop = (height - PANEL_HEIGHT) / 2;
        int left = (width - 340) / 2;
        int top = (height - 282) / 2;

        // Industrial frame, inset panel, header strip, section separators, and field wells.
        graphics.fill(panelLeft, panelTop, panelLeft + PANEL_WIDTH, panelTop + PANEL_HEIGHT, 0xFF05080C);
        graphics.fill(panelLeft + 2, panelTop + 2, panelLeft + PANEL_WIDTH - 2,
                panelTop + PANEL_HEIGHT - 2, PANEL);
        graphics.fill(panelLeft + 3, panelTop + 3, panelLeft + PANEL_WIDTH - 3,
                panelTop + 31, 0xFF28333E);
        graphics.fill(panelLeft + 3, panelTop + 31, panelLeft + PANEL_WIDTH - 3,
                panelTop + 33, AMBER);
        graphics.fill(panelLeft + 10, panelTop + 10, panelLeft + 14, panelTop + 24, AMBER);
        graphics.fill(panelLeft + 12, panelTop + 91, panelLeft + PANEL_WIDTH - 12,
                panelTop + 92, 0xFF46515D);
        graphics.fill(panelLeft + 12, panelTop + 145, panelLeft + PANEL_WIDTH - 12,
                panelTop + 146, 0xFF46515D);
        graphics.fill(panelLeft + 12, panelTop + 198, panelLeft + PANEL_WIDTH - 12,
                panelTop + 199, 0xFF46515D);
        graphics.fill(panelLeft + 3, panelTop + PANEL_HEIGHT - 25,
                panelLeft + PANEL_WIDTH - 3, panelTop + PANEL_HEIGHT - 3, INSET);
        graphics.fill(panelLeft + 3, panelTop + PANEL_HEIGHT - 25,
                panelLeft + PANEL_WIDTH - 3, panelTop + PANEL_HEIGHT - 24, AMBER);

        // Recessed wells behind the standard Minecraft edit controls.
        fieldWell(graphics, left, top + 35, 340, 18);
        fieldWell(graphics, left, top + 62, 185, 18);
        fieldWell(graphics, left + 192, top + 62, 65, 18);
        fieldWell(graphics, left + 264, top + 62, 76, 18);
        fieldWell(graphics, left, top + 116, 165, 18);
        fieldWell(graphics, left + 175, top + 116, 165, 18);
        fieldWell(graphics, left, top + 170, 165, 18);

        // Subtle routing rail and terminal telemetry ticks.
        graphics.fill(left, top + 185, left + 340, top + 187, 0xFF35424F);
        graphics.fill(left, top + 185, left + 62, top + 187, AMBER);
        for (int tick = 0; tick < 12; tick++) {
            int tickX = panelLeft + PANEL_WIDTH - 18 - tick * 5;
            graphics.fill(tickX, panelTop + 12, tickX + 2, panelTop + 17,
                    tick < 8 ? AMBER : 0xFF56616D);
        }

        graphics.drawString(font, "ICREATE // ORDER NETWORK", panelLeft + 22, panelTop + 8, AMBER);
        graphics.drawString(font, "LOGISTICS CONTROL TERMINAL", panelLeft + 22, panelTop + 19, 0xFFE3E8ED);
        graphics.drawString(font, "01  ORDER DETAILS", left, top + 23, AMBER);
        graphics.drawString(font, "02  RESOURCE REQUEST", left, top + 50, AMBER);
        graphics.drawString(font, "03  SHIPMENT ENDPOINTS", left, top + 104, AMBER);
        graphics.drawString(font, "04  ROUTE & ACTIONS", left, top + 158, AMBER);
        graphics.fill(left, top + 185, left + 340, top + 187, 0xFF35424F);
        graphics.fill(left, top + 185, left + 62, top + 187, AMBER);
        graphics.drawString(font, "TRAIN / AIRSHIP depend on installed integrations", left + 174, top + 174, MUTED);

        if (!feedback.isBlank()) {
            int feedbackColor = feedbackError ? 0xFFFF7777 : 0xFFB8E7B8;
            graphics.drawString(font, feedback, panelLeft + 12, panelTop + PANEL_HEIGHT - 17, feedbackColor);
        } else {
            graphics.drawString(font, "READY  //  SERVER VALIDATES ALL REQUESTS",
                    panelLeft + 12, panelTop + PANEL_HEIGHT - 17, MUTED);
        }

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private static void fieldWell(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x - 1, y - 1, x + width + 1, y + height + 1, 0xFF070B10);
        graphics.fill(x, y, x + width, y + height, 0xFF26313B);
        graphics.fill(x, y, x + width, y + 1, 0xFF52606D);
        graphics.fill(x, y + height - 1, x + width, y + height, 0xFF111820);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
