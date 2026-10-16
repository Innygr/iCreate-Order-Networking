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
 * Lightweight terminal control panel. The client only submits intent; the
 * server packet handlers re-check reachability, ACLs, order state, and cargo.
 */
public final class OrderTerminalScreen extends Screen {
    private final BlockPos terminalPos;
    private EditBox orderId;
    private EditBox itemId;
    private EditBox count;
    private EditBox reward;
    private EditBox source;
    private EditBox destination;
    private EditBox route;
    private String feedback = "";

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

        addRenderableWidget(Button.builder(Component.literal("Create order"), button -> createOrder())
                .bounds(left, top + 205, 82, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Claim"), button -> sendOrderAction(
                        OrderTerminalActionPacket.CLAIM))
                .bounds(left + 86, top + 205, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Fulfil"), button -> fulfilOrder())
                .bounds(left + 168, top + 205, 78, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Cancel"), button -> sendOrderAction(
                        OrderTerminalActionPacket.CANCEL))
                .bounds(left + 250, top + 205, 90, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Dispatch shipment"), button -> dispatchShipment())
                .bounds(left, top + 232, 165, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(left + 175, top + 232, 165, 20).build());
        setInitialFocus(orderId);
    }

    private void createOrder() {
        Integer requestedCount = parseInteger(count.getValue(), 1,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT);
        Integer rewardPerItem = parseInteger(reward.getValue(), 0,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_REWARD_PER_ITEM);
        if (requestedCount == null || rewardPerItem == null || itemId.getValue().isBlank()) {
            feedback = "Enter a valid item ID, count, and reward.";
            return;
        }
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos,
                OrderTerminalActionPacket.CREATE, itemId.getValue().trim(), requestedCount,
                rewardPerItem, "", ""));
        feedback = "Order request sent to server.";
    }

    private void sendOrderAction(int action) {
        if (!validOrderId()) return;
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos, action,
                "", 0, 0, orderId.getValue().trim(), ""));
        feedback = "Order action sent to server.";
    }

    private void fulfilOrder() {
        if (!validOrderId()) return;
        Integer requestedCount = parseInteger(count.getValue(), 1,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT);
        if (requestedCount == null) {
            feedback = "Enter a valid fulfilment count.";
            return;
        }
        PacketDistributor.sendToServer(new OrderTerminalActionPacket(terminalPos,
                OrderTerminalActionPacket.FULFIL, "", requestedCount, 0,
                orderId.getValue().trim(), UUID.randomUUID().toString()));
        feedback = "Manual fulfilment request sent to server.";
    }

    private void dispatchShipment() {
        if (!validOrderId()) return;
        Integer requestedCount = parseInteger(count.getValue(), 1,
                com.innygr.icreateordernetworking.orders.OrderService.MAX_ORDER_COUNT);
        String routeName = route.getValue().trim().toUpperCase(java.util.Locale.ROOT);
        if (requestedCount == null || source.getValue().isBlank() || destination.getValue().isBlank()) {
            feedback = "Enter a valid count and both endpoint coordinates.";
            return;
        }
        String sourceReference = source.getValue().trim() + ">" + destination.getValue().trim();
        PacketDistributor.sendToServer(new AutomatedDeliveryPacket(terminalPos,
                orderId.getValue().trim(), requestedCount, routeName, sourceReference,
                UUID.randomUUID().toString()));
        feedback = "Automated shipment request sent to server.";
    }

    private boolean validOrderId() {
        try {
            UUID.fromString(orderId.getValue().trim());
            return true;
        } catch (IllegalArgumentException exception) {
            feedback = "Enter the full order UUID first.";
            return false;
        }
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
        int left = (width - 340) / 2;
        int top = (height - 282) / 2;
        graphics.drawCenteredString(font, title, width / 2, top + 7, 0xFFFFFF);
        graphics.drawString(font, "Order UUID (copy from the order-created message)", left, top + 23, 0xCFCFCF);
        graphics.drawString(font, "Item ID / count / reward per item", left, top + 50, 0xCFCFCF);
        graphics.drawString(font, "Source coordinates", left, top + 104, 0xCFCFCF);
        graphics.drawString(font, "Destination coordinates", left + 175, top + 104, 0xCFCFCF);
        graphics.drawString(font, "Transport route (TRAIN is currently implemented)", left, top + 158, 0xCFCFCF);
        if (!feedback.isBlank()) graphics.drawCenteredString(font, feedback, width / 2, top + 260, 0xFFFF55);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
