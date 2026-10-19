package com.levelupstats.client;

import com.levelupstats.ModAttachments;
import com.levelupstats.PlayerStats;
import com.levelupstats.StatType;
import com.levelupstats.network.SetSliderPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sub-menu with one slider per stat that turns that stat's effects up or down (0-100%). */
public class SettingsScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int HEADER_HEIGHT = 40;
    private static final int ROW_HEIGHT = 26;
    private static final int FOOTER_HEIGHT = 36;
    /** Sliders move in steps of this many percent, so dragging doesn't flood the server with packets. */
    private static final int STEP = 5;

    private final Screen parent;
    private int left;
    private int top;
    private int panelHeight;

    public SettingsScreen(Screen parent) {
        super(Component.translatable("screen.levelupstats.settings_title"));
        this.parent = parent;
    }

    private PlayerStats stats() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? new PlayerStats() : mc.player.getData(ModAttachments.STATS.get());
    }

    @Override
    protected void init() {
        StatType[] types = StatType.values();
        PlayerStats stats = stats();
        panelHeight = HEADER_HEIGHT + types.length * ROW_HEIGHT + FOOTER_HEIGHT;
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;

        for (int i = 0; i < types.length; i++) {
            StatType type = types[i];
            int y = top + HEADER_HEIGHT + i * ROW_HEIGHT;
            addRenderableWidget(new StatSlider(left + 110, y, PANEL_WIDTH - 120, 20, type, stats.getSlider(type)));
        }

        int buttonY = top + panelHeight - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.reset_sliders"), b -> {
            PlayerStats local = stats();
            for (StatType type : types) {
                local.setSlider(type, 100); // update the client copy now; the server confirms with a sync
                PacketDistributor.sendToServer(new SetSliderPayload(type.ordinal(), 100));
            }
            // Rebuild the widgets so the sliders jump back to 100% straight away.
            Minecraft.getInstance().setScreen(new SettingsScreen(parent));
        }).bounds(left + 10, buttonY, 140, 20).build());

        addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, b -> onClose())
                .bounds(left + PANEL_WIDTH - 150, buttonY, 140, 20).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.OPEN_MENU.matches(keyCode, scanCode)) {
            Minecraft.getInstance().setScreen(null);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fill(left - 2, top - 2, left + PANEL_WIDTH + 2, top + panelHeight + 2, 0xFF000000);
        g.fill(left, top, left + PANEL_WIDTH, top + panelHeight, 0xF0202028);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 8, 0xFFFFFFFF);
        g.drawCenteredString(font, Component.translatable("screen.levelupstats.settings_hint"),
                left + PANEL_WIDTH / 2, top + 22, 0xFFAAAAAA);

        StatType[] types = StatType.values();
        for (int i = 0; i < types.length; i++) {
            int y = top + HEADER_HEIGHT + i * ROW_HEIGHT;
            g.drawString(font, Component.translatable(types[i].nameKey()), left + 10, y + 6, types[i].color());
        }
    }

    /** A 0-100% slider for one stat. Sends the new value to the server whenever it changes. */
    private static class StatSlider extends AbstractSliderButton {
        private final StatType type;
        private int percent;

        StatSlider(int x, int y, int width, int height, StatType type, int percent) {
            super(x, y, width, height, Component.empty(), percent / 100.0);
            this.type = type;
            this.percent = percent;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(Component.translatable("screen.levelupstats.effect_strength", (int) Math.round(value * 100)));
        }

        @Override
        protected void applyValue() {
            int snapped = (int) (Math.round(value * 100.0 / STEP) * STEP);
            value = snapped / 100.0; // snap the handle to the step
            if (snapped != percent) {
                percent = snapped;
                PacketDistributor.sendToServer(new SetSliderPayload(type.ordinal(), snapped));
            }
        }
    }
}
