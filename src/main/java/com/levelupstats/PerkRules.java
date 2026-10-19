package com.levelupstats;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.Tags;

/**
 * Shared balance numbers and helpers for the prestige perks. Safe to use on both client and server.
 * Tick values are game ticks (20 per second); a Minecraft day is 24000 ticks.
 */
public final class PerkRules {
    // ---- Egg Conjurer ----
    /** Two Minecraft days between claimed eggs. */
    public static final long EGG_COOLDOWN_TICKS = 48000L;

    // ---- Marker Teleport ----
    /** Leaving this many blocks from the marker (same dimension) pulls you back to it. */
    public static final double MARK_RANGE = 50.0;

    // ---- Stasis Field ----
    public static final double STASIS_RADIUS = 10.0;
    /** Normal mobs stay frozen for 5 seconds, bosses for 3. */
    public static final int STASIS_TICKS = 100;
    public static final int STASIS_BOSS_TICKS = 60;
    public static final int STASIS_COOLDOWN = 2400;

    // ---- Decoy Clone ----
    public static final int DECOY_TICKS = 160;
    public static final int DECOY_COOLDOWN = 600;
    public static final double DECOY_AGGRO_RADIUS = 24.0;

    // ---- Lightning Caller ----
    public static final int LIGHTNING_COOLDOWN = 400;

    // ---- Ore Sight ----
    public static final int ORE_GLOW_RADIUS = 12;
    public static final int ORE_GLOW_MAX = 300;
    public static final int ORE_GLOW_TICKS = 300;
    public static final int ORE_GLOW_COOLDOWN = 40;

    // ---- Vein Miner / Tree Feller ----
    /** Maximum blocks broken per swing, including the one you hit. */
    public static final int CHAIN_LIMIT = 64;

    // ---- Pocket Dimension ----
    /** A mob can be stored if its current health is below this fraction of YOUR max health. */
    public static final double POCKET_HEALTH_FRACTION = 0.8;

    // ---- Heartbeat ----
    public static final int HEARTBEAT_SNEAK_TICKS = 300;
    public static final double HEARTBEAT_NEAR = 30.0;
    public static final double HEARTBEAT_FAR = 90.0;

    // ---- Peacemaker ----
    /** Set to true to let bosses (Wither, Ender Dragon, ...) keep attacking you. */
    public static final boolean PEACE_EXCLUDES_BOSSES = false;

    // ---- Second Wind ----
    /** Two Minecraft days between uses. */
    public static final long SECOND_WIND_COOLDOWN_TICKS = 48000L;
    /** Health restored, as a fraction of max health. */
    public static final float SECOND_WIND_HEALTH_FRACTION = 0.3F;
    /** Ticks of full damage immunity after it triggers. */
    public static final int SECOND_WIND_INVULN_TICKS = 60;

    // ---- Earthshaker ----
    public static final float EARTHSHAKER_MIN_FALL = 4.0F;

    private PerkRules() {
    }

    /** Bosses and other mobs that must never be spawned from eggs or stored in the pocket dimension. */
    public static boolean isBossLike(EntityType<?> type) {
        return type == EntityType.ENDER_DRAGON
                || type == EntityType.WITHER
                || type == EntityType.WARDEN
                || type.is(Tags.EntityTypes.BOSSES);
    }

    /** RGB colour (no alpha) that represents an ore, used by Ore Sight. */
    public static int oreColor(BlockState state) {
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        if (path.contains("ancient_debris")) return 0x8A5A44;
        if (path.contains("nether_gold")) return 0xFFC933;
        if (path.contains("gold")) return 0xFCEE4B;
        if (path.contains("iron")) return 0xD8AF93;
        if (path.contains("copper")) return 0xE77C56;
        if (path.contains("coal")) return 0x8C8C8C;
        if (path.contains("redstone")) return 0xFF2A2A;
        if (path.contains("lapis")) return 0x2F5BFF;
        if (path.contains("diamond")) return 0x4AEDD9;
        if (path.contains("emerald")) return 0x17DD62;
        if (path.contains("quartz")) return 0xF3EBDD;
        return 0xC04BFF; // modded or unknown ores
    }
}
