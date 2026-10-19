package com.levelupstats;

import net.minecraft.resources.ResourceLocation;

public enum StatType {
    STRENGTH("strength", 0xFFFF5555),
    DEXTERITY("dexterity", 0xFF55FF55),
    CONSTITUTION("constitution", 0xFFFF55FF),
    INTELLIGENCE("intelligence", 0xFF55AAFF),
    WISDOM("wisdom", 0xFFFFAA00),
    CHARISMA("charisma", 0xFFFFFF55);

    private final String id;
    private final int color;

    StatType(String id, int color) {
        this.id = id;
        this.color = color;
    }

    public String id() {
        return id;
    }

    public int color() {
        return color;
    }

    public String nameKey() {
        return "stat." + LevelUpStats.MODID + "." + id;
    }

    /** Id for an attribute modifier belonging to this stat, e.g. "levelupstats:strength_damage". */
    public ResourceLocation modifierId(String effect) {
        return ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, id + "_" + effect);
    }
}
