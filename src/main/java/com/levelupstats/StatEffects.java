package com.levelupstats;

import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;

/**
 * All balance numbers live here so they are easy to tweak.
 *
 * <p>Every method takes the stat's <em>effective</em> level (real level x slider), which is a double.
 */
public final class StatEffects {
    // ---- Strength ----
    /** Melee damage added per level (1.0 = half a heart). */
    public static final double DAMAGE_PER_LEVEL = 0.5;
    /** Attack knockback attribute added per level (1.0 is about one level of the Knockback enchantment). */
    public static final double KNOCKBACK_PER_LEVEL = 0.1;
    /** Reach grows with the square of the level so it is barely noticeable early on. */
    public static final double REACH_QUAD = 0.012;
    /** Hard limit on bonus reach in blocks, so it can never get silly. */
    public static final double REACH_MAX = 3.0;

    // ---- Dexterity (bonus = LIN * level + QUAD * level^2, as a fraction of the base value) ----
    public static final double SPEED_LIN = 0.01;
    public static final double SPEED_QUAD = 0.0015;
    public static final double JUMP_LIN = 0.01;
    public static final double JUMP_QUAD = 0.002;
    public static final double ATTACK_SPEED_LIN = 0.008;
    public static final double ATTACK_SPEED_QUAD = 0.0012;
    /** Extra blocks you can fall without damage per level, so higher jumps don't hurt. */
    public static final double SAFE_FALL_PER_LEVEL = 0.3;

    // ---- Constitution ----
    /** Max health added per level (2.0 = one heart). */
    public static final double HEALTH_PER_LEVEL = 2.0;
    /** Extra health regenerated per second, per level (0.03 -> level 10 = 0.3 HP/s). */
    public static final double REGEN_PER_SECOND_PER_LEVEL = 0.03;

    // ---- Intelligence ----
    /** Value added to the Luck attribute per level. */
    public static final double LUCK_ATTRIBUTE_PER_LEVEL = 0.5;
    /** Extra crop yield per level (0.1 -> level 10 = double crops). */
    public static final double CROP_BONUS_PER_LEVEL = 0.1;

    // ---- Wisdom ----
    /** XP orb multiplier added per level (0.5 -> level 2 = x2.0). */
    public static final double XP_PER_LEVEL = 0.5;
    /** Chance of twin babies per level (0.08 -> level 10 = 80%). */
    public static final double TWIN_CHANCE_PER_LEVEL = 0.08;

    // ---- Charisma ----
    /** Villager price discount per level (0.04 -> level 10 = 40%). */
    public static final double DISCOUNT_PER_LEVEL = 0.04;
    public static final double DISCOUNT_MAX = 0.75;

    /** Every this many effective levels adds one extra copy of smelted / traded items. */
    public static final int EXTRA_ITEM_INTERVAL = 5;

    private StatEffects() {
    }

    // ---- Strength ----
    public static double damage(double e) {
        return e * DAMAGE_PER_LEVEL;
    }

    public static double knockback(double e) {
        return e * KNOCKBACK_PER_LEVEL;
    }

    public static double reach(double e) {
        return Math.min(REACH_MAX, REACH_QUAD * e * e);
    }

    // ---- Dexterity ----
    public static double speed(double e) {
        return SPEED_LIN * e + SPEED_QUAD * e * e;
    }

    public static double jump(double e) {
        return JUMP_LIN * e + JUMP_QUAD * e * e;
    }

    public static double attackSpeed(double e) {
        return ATTACK_SPEED_LIN * e + ATTACK_SPEED_QUAD * e * e;
    }

    public static double safeFall(double e) {
        return e * SAFE_FALL_PER_LEVEL;
    }

    // ---- Constitution ----
    public static double maxHealth(double e) {
        return e * HEALTH_PER_LEVEL;
    }

    public static double regenPerSecond(double e) {
        return e * REGEN_PER_SECOND_PER_LEVEL;
    }

    // ---- Intelligence ----
    /** Extra Fortune / Looting levels: +1 for every 2 effective levels (rounded up once you have any). */
    public static int fortuneLootingBonus(double e) {
        return (int) Math.floor((e + 1.0) / 2.0);
    }

    public static double luckAttribute(double e) {
        return e * LUCK_ATTRIBUTE_PER_LEVEL;
    }

    public static double cropMultiplier(double e) {
        return 1.0 + e * CROP_BONUS_PER_LEVEL;
    }

    // ---- Wisdom ----
    public static double xpMultiplier(double e) {
        return 1.0 + e * XP_PER_LEVEL;
    }

    public static double twinChance(double e) {
        return Math.min(1.0, e * TWIN_CHANCE_PER_LEVEL);
    }

    // ---- Charisma ----
    public static double discount(double e) {
        return Math.min(DISCOUNT_MAX, e * DISCOUNT_PER_LEVEL);
    }

    /** Extra copies of smelted / traded items: 1 at level 5 (double), 2 at level 10 (triple), and so on. */
    public static int extraItemCopies(double e) {
        return (int) Math.floor(e / EXTRA_ITEM_INTERVAL + 1.0e-9);
    }

    /** One line per effect, describing what the stat gives at the given effective level. */
    public static List<Component> describe(StatType type, double e) {
        return switch (type) {
            case STRENGTH -> List.of(
                    Component.translatable("effect.levelupstats.damage", fmt(damage(e))),
                    Component.translatable("effect.levelupstats.knockback", fmt(knockback(e))),
                    Component.translatable("effect.levelupstats.reach", fmt(reach(e))));
            case DEXTERITY -> List.of(
                    Component.translatable("effect.levelupstats.speed", fmt(speed(e) * 100)),
                    Component.translatable("effect.levelupstats.jump", fmt(jump(e) * 100)),
                    Component.translatable("effect.levelupstats.attack_speed", fmt(attackSpeed(e) * 100)));
            case CONSTITUTION -> List.of(
                    Component.translatable("effect.levelupstats.health", fmt(maxHealth(e) / 2.0)),
                    Component.translatable("effect.levelupstats.regen", fmt(regenPerSecond(e))));
            case INTELLIGENCE -> List.of(
                    Component.translatable("effect.levelupstats.fortune", fortuneLootingBonus(e)),
                    Component.translatable("effect.levelupstats.luck", fmt(luckAttribute(e))),
                    Component.translatable("effect.levelupstats.crops", fmt(cropMultiplier(e))));
            case WISDOM -> List.of(
                    Component.translatable("effect.levelupstats.xp", fmt(xpMultiplier(e))),
                    Component.translatable("effect.levelupstats.twins", fmt(twinChance(e) * 100)),
                    Component.translatable("effect.levelupstats.smelting", 1 + extraItemCopies(e)));
            case CHARISMA -> List.of(
                    Component.translatable("effect.levelupstats.discount", fmt(discount(e) * 100)),
                    Component.translatable("effect.levelupstats.trade_items", 1 + extraItemCopies(e)));
        };
    }

    private static String fmt(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.005) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
