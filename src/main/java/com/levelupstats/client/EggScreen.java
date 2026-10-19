package com.levelupstats.client;

import com.levelupstats.PerkRules;
import com.levelupstats.network.ClaimEggPayload;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.neoforged.neoforge.network.PacketDistributor;

/** Picker for the Egg Conjurer perk: a searchable grid of every non-boss spawn egg. */
public class EggScreen extends Screen {
    private static final int COLS = 9;
    private static final int ROWS = 5;
    private static final int CELL = 22;

    private record Entry(EntityType<?> type, ItemStack stack, ResourceLocation id, String name) {
    }

    private final Screen parent;
    private final List<Entry> all = new ArrayList<>();
    private List<Entry> shown = new ArrayList<>();
    private EditBox search;
    private int page;
    private int gridLeft;
    private int gridTop;

    public EggScreen(Screen parent) {
        super(Component.translatable("screen.levelupstats.egg_title"));
        this.parent = parent;
        for (var item : BuiltInRegistries.ITEM) {
            if (item instanceof SpawnEggItem egg) {
                ItemStack stack = item.getDefaultInstance();
                EntityType<?> type = egg.getType(stack);
                if (!PerkRules.isBossLike(type)) {
                    all.add(new Entry(type, stack, BuiltInRegistries.ENTITY_TYPE.getKey(type),
                            type.getDescription().getString()));
                }
            }
        }
        all.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        shown = all;
    }

    @Override
    protected void init() {
        gridLeft = (width - COLS * CELL) / 2;
        gridTop = 52;
        search = addRenderableWidget(new EditBox(font, width / 2 - 80, 24, 160, 18,
                Component.translatable("screen.levelupstats.search")));
        search.setResponder(text -> {
            String q = text.toLowerCase();
            shown = new ArrayList<>();
            for (Entry e : all) {
                if (e.name().toLowerCase().contains(q) || e.id().getPath().contains(q)) {
                    shown.add(e);
                }
            }
            page = 0;
        });
        int navY = gridTop + ROWS * CELL + 8;
        addRenderableWidget(Button.builder(Component.literal("<"), b -> page = Math.max(0, page - 1))
                .bounds(width / 2 - 70, navY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.literal(">"), b -> page = Math.min(pages() - 1, page + 1))
                .bounds(width / 2 + 50, navY, 20, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.back"), b -> onClose())
                .bounds(width / 2 - 40, navY, 80, 20).build());
        setInitialFocus(search);
    }

    private int pages() {
        return Math.max(1, (shown.size() + COLS * ROWS - 1) / (COLS * ROWS));
    }

    private Entry entryAt(double mx, double my) {
        int col = (int) Math.floor((mx - gridLeft) / CELL);
        int row = (int) Math.floor((my - gridTop) / CELL);
        if (mx < gridLeft || my < gridTop || col < 0 || col >= COLS || row < 0 || row >= ROWS) {
            return null;
        }
        int index = page * COLS * ROWS + row * COLS + col;
        return index < shown.size() ? shown.get(index) : null;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        Entry entry = entryAt(mx, my);
        if (entry != null) {
            PacketDistributor.sendToServer(new ClaimEggPayload(entry.id()));
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        page = Math.max(0, Math.min(pages() - 1, page + (sy < 0 ? 1 : -1)));
        return true;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 10, 0xFFFFFFFF);
        int start = page * COLS * ROWS;
        for (int i = 0; i < COLS * ROWS && start + i < shown.size(); i++) {
            int x = gridLeft + (i % COLS) * CELL + 3;
            int y = gridTop + (i / COLS) * CELL + 3;
            g.renderItem(shown.get(start + i).stack(), x, y);
        }
        Entry hover = entryAt(mouseX, mouseY);
        if (hover != null) {
            g.renderTooltip(font, Component.literal(hover.name()), mouseX, mouseY);
        }
        g.drawCenteredString(font, Component.translatable("screen.levelupstats.page", page + 1, pages()),
                width / 2, gridTop + ROWS * CELL + 32, 0xFFAAAAAA);
    }
}
