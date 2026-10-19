package com.levelupstats.client;

import com.levelupstats.ModAttachments;
import com.levelupstats.PlayerStats;
import com.levelupstats.StatEffects;
import com.levelupstats.StatType;
import com.levelupstats.network.UpgradeStatPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.network.chat.Style;
import net.neoforged.neoforge.network.PacketDistributor;

/** The stat upgrade menu, opened with the keybind. */
public class StatsScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int HEADER_HEIGHT = 50;
    private static final int ROW_HEIGHT = 28;
    private static final int FOOTER_HEIGHT = 36;

    private final List<Button> upgradeButtons = new ArrayList<>();
    private int left;
    private int top;
    private int panelHeight;

    public StatsScreen() {
        super(Component.translatable("screen.levelupstats.title"));
    }

    private PlayerStats stats() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? new PlayerStats() : mc.player.getData(ModAttachments.STATS.get());
    }

    private int playerLevels() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : mc.player.experienceLevel;
    }

    @Override
    protected void init() {
        upgradeButtons.clear();
        StatType[] types = StatType.values();
        panelHeight = HEADER_HEIGHT + types.length * ROW_HEIGHT + FOOTER_HEIGHT;
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;

        for (int i = 0; i < types.length; i++) {
            final StatType type = types[i];
            int y = top + HEADER_HEIGHT + i * ROW_HEIGHT;
            Button button = Button.builder(Component.literal("+"),
                            b -> PacketDistributor.sendToServer(new UpgradeStatPayload(type.ordinal())))
                    .bounds(left + PANEL_WIDTH - 30, y + 2, 20, 20)
                    .build();
            upgradeButtons.add(addRenderableWidget(button));
        }

        int buttonY = top + panelHeight - 28;
        addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.sliders"),
                        b -> Minecraft.getInstance().setScreen(new SettingsScreen(this)))
                .bounds(left + 10, buttonY, PANEL_WIDTH - 20, 20)
                .build());

        // Tabs above the panel
        Button statsTab = addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.tab_stats"),
                b -> { }).bounds(left, top - 24, PANEL_WIDTH / 2 - 2, 20).build());
        statsTab.active = false;
        addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.tab_perks"),
                        b -> Minecraft.getInstance().setScreen(new PerksScreen()))
                .bounds(left + PANEL_WIDTH / 2 + 2, top - 24, PANEL_WIDTH / 2 - 2, 20).build());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (ClientModEvents.OPEN_MENU.matches(keyCode, scanCode)) {
            onClose();
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
        PlayerStats stats = stats();
        int levels = playerLevels();
        StatType[] types = StatType.values();

        // Update button states before they are drawn.
        for (int i = 0; i < types.length; i++) {
            StatType type = types[i];
            upgradeButtons.get(i).active = !stats.isMaxed(type) && levels >= stats.costFor(type);
        }

        super.render(g, mouseX, mouseY, partialTick);

        // Header
        g.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 8, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("screen.levelupstats.levels", levels), left + 10, top + 26, 0xFF80FF20);
        Component capText = Component.translatable("screen.levelupstats.cap", stats.cap(), stats.getPrestige());
        g.drawString(font, capText, left + PANEL_WIDTH - 10 - font.width(capText), top + 26, 0xFFFFD700);

        // Rows
        List<Component> tooltip = null;
        for (int i = 0; i < types.length; i++) {
            StatType type = types[i];
            int y = top + HEADER_HEIGHT + i * ROW_HEIGHT;
            int level = stats.get(type);

            g.drawString(font, Component.translatable(type.nameKey()), left + 10, y + 2, type.color());

            Component levelText = Component.translatable("screen.levelupstats.level", level, stats.cap());
            g.drawString(font, levelText, left + 100, y + 2, 0xFFFFFFFF);

            // Progress bar toward the current cap
            int barX = left + 160;
            int barY = y + 3;
            int barW = 70;
            g.fill(barX, barY, barX + barW, barY + 6, 0xFF000000);
            int filled = (int) (barW * (level / (float) stats.cap()));
            g.fill(barX, barY, barX + filled, barY + 6, type.color());

            // Short effect summary on the second line; hover the row for the full list.
            List<Component> lines = StatEffects.describe(type, stats.effective(type));
            Component summary = ComponentUtils.formatList(lines, Component.literal(" | "));
            String shortText = font.plainSubstrByWidth(summary.getString(), PANEL_WIDTH - 60);
            g.drawString(font, shortText, left + 10, y + 14, 0xFFAAAAAA);

            Component right = stats.isMaxed(type)
                    ? Component.translatable("screen.levelupstats.max")
                    : Component.translatable("screen.levelupstats.cost", stats.costFor(type));
            int rightColor = stats.isMaxed(type) ? 0xFFFFD700 : (levels >= stats.costFor(type) ? 0xFF80FF20 : 0xFFFF5555);
            g.drawString(font, right, left + PANEL_WIDTH - 36 - font.width(right), y + 2, rightColor);

            if (mouseX >= left + 10 && mouseX < left + PANEL_WIDTH - 40 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(type.nameKey())
                        .withStyle(Style.EMPTY.withColor(type.color() & 0xFFFFFF)));
                if (stats.getSlider(type) < 100) {
                    tooltip.add(Component.translatable("screen.levelupstats.scaled", stats.getSlider(type)));
                }
                tooltip.addAll(lines);
            }
        }

        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }
}
