package com.levelupstats.client;

import com.levelupstats.ModAttachments;
import com.levelupstats.PerkType;
import com.levelupstats.PlayerStats;
import com.levelupstats.network.ActionPayload;
import com.levelupstats.network.PrestigePayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

/** The Prestige Perks tab: spend prestige points, toggle perks, and prestige. */
public class PerksScreen extends Screen {
    private static final int PANEL_WIDTH = 380;
    private static final int HEADER_HEIGHT = 50;
    private static final int ROW_HEIGHT = 30;
    private static final int PER_PAGE = 5;
    private static final int FOOTER_HEIGHT = 58;
    private static final int TICKS_PER_DAY = 24000;

    private static int page;

    private final List<Button> actionButtons = new ArrayList<>();
    private final List<Button> pickButtons = new ArrayList<>();
    private Button prevButton;
    private Button nextButton;
    private Button prestigeButton;
    private int left;
    private int top;
    private int panelHeight;

    public PerksScreen() {
        super(Component.translatable("screen.levelupstats.perks_title"));
    }

    private PlayerStats stats() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? new PlayerStats() : mc.player.getData(ModAttachments.STATS.get());
    }

    private static int pageCount() {
        return (PerkType.values().length + PER_PAGE - 1) / PER_PAGE;
    }

    private PerkType perkAt(int row) {
        int index = page * PER_PAGE + row;
        return index < PerkType.values().length ? PerkType.values()[index] : null;
    }

    @Override
    protected void init() {
        actionButtons.clear();
        pickButtons.clear();
        panelHeight = HEADER_HEIGHT + PER_PAGE * ROW_HEIGHT + FOOTER_HEIGHT;
        left = (width - PANEL_WIDTH) / 2;
        top = (height - panelHeight) / 2;

        // Ask for fresh data (cooldowns, advancement state) whenever the tab opens.
        PacketDistributor.sendToServer(ActionPayload.of(ActionPayload.REQUEST_SYNC));

        for (int row = 0; row < PER_PAGE; row++) {
            final int r = row;
            int y = top + HEADER_HEIGHT + row * ROW_HEIGHT;
            actionButtons.add(addRenderableWidget(Button.builder(Component.empty(), b -> onAction(r))
                    .bounds(left + PANEL_WIDTH - 92, y + 3, 82, 20).build()));
            pickButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.pick"),
                            b -> Minecraft.getInstance().setScreen(new EggScreen(this)))
                    .bounds(left + PANEL_WIDTH - 136, y + 3, 40, 20).build()));
        }

        int pageY = top + panelHeight - FOOTER_HEIGHT + 4;
        prevButton = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1))
                .bounds(left + 10, pageY, 20, 20).build());
        nextButton = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1))
                .bounds(left + PANEL_WIDTH - 30, pageY, 20, 20).build());
        prestigeButton = addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.prestige"),
                        b -> confirmPrestige())
                .bounds(left + 40, pageY, PANEL_WIDTH - 80, 20).build());

        addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.tab_stats"),
                        b -> Minecraft.getInstance().setScreen(new StatsScreen()))
                .bounds(left, top - 24, PANEL_WIDTH / 2 - 2, 20).build());
        Button perksTab = addRenderableWidget(Button.builder(Component.translatable("screen.levelupstats.tab_perks"),
                b -> { }).bounds(left + PANEL_WIDTH / 2 + 2, top - 24, PANEL_WIDTH / 2 - 2, 20).build());
        perksTab.active = false;
    }

    private void changePage(int delta) {
        page = Math.max(0, Math.min(pageCount() - 1, page + delta));
    }

    private void onAction(int row) {
        PerkType type = perkAt(row);
        if (type == null) {
            return;
        }
        PlayerStats stats = stats();
        int action = stats.isUnlocked(type) ? ActionPayload.TOGGLE : ActionPayload.UNLOCK;
        PacketDistributor.sendToServer(new ActionPayload(action, type.ordinal()));
    }

    private void confirmPrestige() {
        PlayerStats stats = stats();
        Minecraft mc = Minecraft.getInstance();
        Component text = Component.translatable("screen.levelupstats.prestige_confirm",
                stats.nextPrestigePoints(), (long) Math.pow(2, Math.min(stats.getPrestige() + 1, PlayerStats.MAX_XP_PRESTIGE)));
        mc.setScreen(new ConfirmScreen(yes -> {
            if (yes) {
                PacketDistributor.sendToServer(PrestigePayload.INSTANCE);
            }
            mc.setScreen(new PerksScreen());
        }, Component.translatable("screen.levelupstats.prestige"), text));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        changePage(scrollY < 0 ? 1 : -1);
        return true;
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

        for (int row = 0; row < PER_PAGE; row++) {
            PerkType type = perkAt(row);
            Button action = actionButtons.get(row);
            Button pick = pickButtons.get(row);
            action.visible = type != null;
            pick.visible = false;
            if (type == null) {
                continue;
            }
            if (!stats.isUnlocked(type)) {
                action.setMessage(Component.translatable("screen.levelupstats.unlock", type.cost()));
                action.active = stats.getPoints() >= type.cost();
            } else {
                action.setMessage(Component.translatable(stats.isEnabled(type)
                        ? "screen.levelupstats.on" : "screen.levelupstats.off"));
                action.active = true;
                pick.visible = type == PerkType.EGG && stats.isEnabled(type);
            }
        }
        prevButton.active = page > 0;
        nextButton.active = page < pageCount() - 1;
        prestigeButton.active = stats.allMaxed();

        super.render(g, mouseX, mouseY, partialTick);

        g.drawCenteredString(font, title, left + PANEL_WIDTH / 2, top + 8, 0xFFFFFFFF);
        g.drawString(font, Component.translatable("screen.levelupstats.points", stats.getPoints()),
                left + 10, top + 26, 0xFF80FF20);
        Component prestigeText = Component.translatable("screen.levelupstats.prestige_info",
                stats.getPrestige(), formatMultiplier(stats.prestigeXpMultiplier()));
        g.drawString(font, prestigeText, left + PANEL_WIDTH - 10 - font.width(prestigeText), top + 26, 0xFFFFD700);

        List<Component> tooltip = null;
        for (int row = 0; row < PER_PAGE; row++) {
            PerkType type = perkAt(row);
            if (type == null) {
                continue;
            }
            int y = top + HEADER_HEIGHT + row * ROW_HEIGHT;
            boolean unlocked = stats.isUnlocked(type);
            int color = !unlocked ? 0xFFAAAAAA : stats.isEnabled(type) ? 0xFF80FF20 : 0xFFFFFFFF;
            g.drawString(font, Component.translatable(type.nameKey()), left + 10, y + 4, color);
            Component status = status(type, stats);
            if (status != null) {
                g.drawString(font, status, left + 10, y + 16, 0xFF9999CC);
            }
            if (mouseX >= left + 10 && mouseX < left + PANEL_WIDTH - 140 && mouseY >= y && mouseY < y + ROW_HEIGHT - 2) {
                tooltip = new ArrayList<>();
                tooltip.add(Component.translatable(type.nameKey()));
                tooltip.add(Component.translatable(type.descKey()));
                tooltip.add(Component.translatable("screen.levelupstats.cost_points", type.cost()));
            }
        }
        g.drawCenteredString(font, Component.translatable("screen.levelupstats.page", page + 1, pageCount()),
                left + PANEL_WIDTH / 2, top + panelHeight - 24, 0xFFAAAAAA);
        if (!stats.allMaxed()) {
            g.drawCenteredString(font, Component.translatable("screen.levelupstats.prestige_hint", stats.cap()),
                    left + PANEL_WIDTH / 2, top + panelHeight - 11, 0xFFFF8888);
        }
        if (tooltip != null) {
            g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private static String formatMultiplier(double value) {
        return value >= 1000000 ? String.format("%.1e", value) : String.valueOf((long) value);
    }

    private static Component status(PerkType type, PlayerStats stats) {
        if (!stats.isUnlocked(type)) {
            return null;
        }
        switch (type) {
            case EGG:
                if (!stats.isEndFreed()) {
                    return Component.translatable("screen.levelupstats.status.egg_locked");
                }
                if (stats.getEggCooldownTicks() > 0) {
                    return Component.translatable("screen.levelupstats.status.egg_wait", time(stats.getEggCooldownTicks()));
                }
                return Component.translatable("screen.levelupstats.status.egg_ready");
            case SECOND_WIND:
                return stats.getSecondWindCooldownTicks() > 0
                        ? Component.translatable("screen.levelupstats.status.egg_wait", time(stats.getSecondWindCooldownTicks()))
                        : Component.translatable("screen.levelupstats.status.ready");
            case POCKET:
                return stats.getPocket() == null
                        ? Component.translatable("screen.levelupstats.status.pocket_empty")
                        : Component.translatable("screen.levelupstats.status.pocket_full", stats.getPocket().name());
            case MARK_TP:
                return stats.getMarker() == null
                        ? Component.translatable("screen.levelupstats.status.no_marker")
                        : Component.translatable("screen.levelupstats.status.marker_set");
            default:
                return null;
        }
    }

    private static String time(int ticks) {
        int minutes = ticks / 1200;
        if (ticks >= TICKS_PER_DAY) {
            return String.format("%.1f MC days", ticks / (double) TICKS_PER_DAY);
        }
        return minutes + " min";
    }
}
