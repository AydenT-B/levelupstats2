package com.levelupstats;

import org.jetbrains.annotations.Nullable;

/**
 * Prestige perks. Each is bought once with prestige points, then can be switched on and off.
 * The enum order is the order they appear in the menu, and the ordinal is used in network packets.
 */
public enum PerkType {
    WATER_WALK("water_walk", 1),
    DOUBLE_JUMP("double_jump", 1),
    ORE_GLOW("ore_glow", 1),
    LIGHTNING("lightning", 1),
    VEIN_MINE("vein_mine", 2),
    TREE_CHOP("tree_chop", 2),
    LAVA_SWIM("lava_swim", 2),
    MARK_TP("mark_tp", 2),
    HEARTBEAT("heartbeat", 2),
    STASIS("stasis", 2),
    DECOY("decoy", 2),
    EARTHSHAKER("earthshaker", 2),
    SECOND_WIND("second_wind", 2),
    MOB_PEACE("mob_peace", 3),
    EGG("egg", 3),
    POCKET("pocket", 3);

    private final String id;
    private final int cost;

    PerkType(String id, int cost) {
        this.id = id;
        this.cost = cost;
    }

    public String id() {
        return id;
    }

    /** Prestige points needed to unlock this perk. */
    public int cost() {
        return cost;
    }

    public String nameKey() {
        return "perk." + LevelUpStats.MODID + "." + id;
    }

    public String descKey() {
        return "perk." + LevelUpStats.MODID + "." + id + ".desc";
    }

    @Nullable
    public static PerkType byId(String id) {
        for (PerkType type : values()) {
            if (type.id.equals(id)) {
                return type;
            }
        }
        return null;
    }
}
