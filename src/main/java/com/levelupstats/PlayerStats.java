package com.levelupstats;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.codec.ByteBufCodecs;
import org.jetbrains.annotations.Nullable;

/**
 * Everything the mod remembers about a player: stat levels, effect sliders, prestige, perks, marker and
 * stored mob. Stored as a data attachment on the player.
 */
public class PlayerStats {
    /** Starting maximum level for every stat. */
    public static final int BASE_CAP = 10;
    /** How much the cap rises per prestige. */
    public static final int CAP_PER_PRESTIGE = 5;
    /** Level every stat drops to when you prestige. */
    public static final int PRESTIGE_RESET_LEVEL = 1;
    /** The orb XP multiplier from prestige stops doubling after this many prestiges (2^20). */
    public static final int MAX_XP_PRESTIGE = 20;
    /** Save format of the stats: 1 = original six stats, 2 = Strength...Charisma. */
    public static final int DATA_VERSION = 2;
    /** Save format of the perk data: 0 = none (pre-perks), 1 = perks. */
    public static final int PERK_DATA_VERSION = 1;

    /** A teleport marker. {@code dim} is a dimension id like "minecraft:overworld". */
    public record Marker(String dim, double x, double y, double z) {
        public static final Codec<Marker> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("dim").forGetter(Marker::dim),
                Codec.DOUBLE.fieldOf("x").forGetter(Marker::x),
                Codec.DOUBLE.fieldOf("y").forGetter(Marker::y),
                Codec.DOUBLE.fieldOf("z").forGetter(Marker::z)
        ).apply(instance, Marker::new));
    }

    /** A mob stored in the pocket dimension. On the client only {@code name} is filled in. */
    public record Pocket(String name, CompoundTag tag) {
        public static final Codec<Pocket> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Pocket::name),
                CompoundTag.CODEC.fieldOf("tag").forGetter(Pocket::tag)
        ).apply(instance, Pocket::new));
    }

    public static final Codec<PlayerStats> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.listOf().optionalFieldOf("levels", List.of()).forGetter(PlayerStats::levelList),
            Codec.INT.optionalFieldOf("prestige", 0).forGetter(PlayerStats::getPrestige),
            Codec.INT.listOf().optionalFieldOf("sliders", List.of()).forGetter(PlayerStats::sliderList),
            Codec.INT.optionalFieldOf("version", 1).forGetter(stats -> DATA_VERSION),
            Codec.INT.optionalFieldOf("refund", 0).forGetter(PlayerStats::getPendingRefund),
            Codec.INT.optionalFieldOf("points", 0).forGetter(PlayerStats::getPoints),
            Codec.STRING.listOf().optionalFieldOf("unlocked", List.of()).forGetter(stats -> perkIds(stats.unlocked)),
            Codec.STRING.listOf().optionalFieldOf("enabled", List.of()).forGetter(stats -> perkIds(stats.enabled)),
            Codec.LONG.optionalFieldOf("last_egg", -1L).forGetter(PlayerStats::getLastEggClaim),
            Marker.CODEC.optionalFieldOf("marker").forGetter(stats -> Optional.ofNullable(stats.marker)),
            Pocket.CODEC.optionalFieldOf("pocket").forGetter(stats -> Optional.ofNullable(stats.pocket)),
            Codec.INT.optionalFieldOf("perk_version", 0).forGetter(stats -> PERK_DATA_VERSION),
            Codec.LONG.optionalFieldOf("last_second_wind", -1L).forGetter(PlayerStats::getLastSecondWind)
    ).apply(instance, PlayerStats::fromSaved));

    private final int[] levels = new int[StatType.values().length];
    /** How much of each stat's effect is active, 0-100. */
    private final int[] sliders = new int[StatType.values().length];
    private int prestige;
    /** XP levels still owed to the player from the stat rework. */
    private int pendingRefund;

    private int points;
    private final EnumSet<PerkType> unlocked = EnumSet.noneOf(PerkType.class);
    private final EnumSet<PerkType> enabled = EnumSet.noneOf(PerkType.class);
    /** World game time (overworld) when the last egg was claimed, or -1 for never. */
    private long lastEggClaim = -1L;
    /** World game time when Second Wind last saved the player, or -1 for never. */
    private long lastSecondWind = -1L;
    @Nullable
    private Marker marker;
    @Nullable
    private Pocket pocket;

    // Filled in by the server just before a sync; never saved.
    private boolean endFreed;
    private int eggCooldownTicks;
    private int secondWindCooldownTicks;

    public PlayerStats() {
        java.util.Arrays.fill(sliders, 100);
    }

    private static PlayerStats fromSaved(List<Integer> savedLevels, int prestige, List<Integer> savedSliders,
                                         int version, int refund, int points, List<String> unlockedIds,
                                         List<String> enabledIds, long lastEgg, Optional<Marker> marker,
                                         Optional<Pocket> pocket, int perkVersion, long lastSecondWind) {
        PlayerStats stats = new PlayerStats();
        stats.prestige = Math.max(0, prestige);
        stats.pendingRefund = Math.max(0, refund);
        stats.points = Math.max(0, points);
        stats.lastEggClaim = lastEgg;
        stats.lastSecondWind = lastSecondWind;
        stats.marker = marker.orElse(null);
        stats.pocket = pocket.orElse(null);

        for (String id : unlockedIds) {
            PerkType type = PerkType.byId(id);
            if (type != null) {
                stats.unlocked.add(type);
            }
        }
        for (String id : enabledIds) {
            PerkType type = PerkType.byId(id);
            if (type != null && stats.unlocked.contains(type)) {
                stats.enabled.add(type);
            }
        }

        // Saves from before perks existed: prestiging used to be free, so give the points that
        // the new system would have paid out (1 + 2 + ... + prestige).
        if (perkVersion < PERK_DATA_VERSION) {
            long retro = (long) stats.prestige * (stats.prestige + 1) / 2;
            stats.points = (int) Math.min(Integer.MAX_VALUE, stats.points + retro);
        }

        if (version < DATA_VERSION) {
            // Old save: the stats were different, so reset them and owe the player every level they spent.
            // The Nth point cost N levels, so a stat at level L cost 1 + 2 + ... + L = L(L+1)/2.
            long owed = stats.pendingRefund;
            for (int level : savedLevels) {
                long l = Math.max(0, level);
                owed += l * (l + 1) / 2;
            }
            stats.pendingRefund = (int) Math.min(Integer.MAX_VALUE, owed);
        } else {
            for (int i = 0; i < stats.levels.length && i < savedLevels.size(); i++) {
                stats.levels[i] = Math.max(0, savedLevels.get(i));
            }
            for (int i = 0; i < stats.sliders.length && i < savedSliders.size(); i++) {
                stats.sliders[i] = Math.max(0, Math.min(100, savedSliders.get(i)));
            }
        }
        return stats;
    }

    private static List<String> perkIds(EnumSet<PerkType> set) {
        List<String> ids = new ArrayList<>(set.size());
        for (PerkType type : set) {
            ids.add(type.id());
        }
        return ids;
    }

    private List<Integer> levelList() {
        List<Integer> list = new ArrayList<>(levels.length);
        for (int level : levels) {
            list.add(level);
        }
        return list;
    }

    private List<Integer> sliderList() {
        List<Integer> list = new ArrayList<>(sliders.length);
        for (int value : sliders) {
            list.add(value);
        }
        return list;
    }

    // ---- stats ----

    public int get(StatType type) {
        return levels[type.ordinal()];
    }

    public void set(StatType type, int level) {
        levels[type.ordinal()] = level;
    }

    /** Slider value for a stat, 0-100. */
    public int getSlider(StatType type) {
        return sliders[type.ordinal()];
    }

    public void setSlider(StatType type, int percent) {
        sliders[type.ordinal()] = Math.max(0, Math.min(100, percent));
    }

    /**
     * The level the stat actually acts at: its real level scaled by the slider.
     * Every effect in the mod uses this, never the raw level.
     */
    public double effective(StatType type) {
        return get(type) * getSlider(type) / 100.0;
    }

    public int getPendingRefund() {
        return pendingRefund;
    }

    /** Returns the owed refund and clears it. */
    public int takeRefund() {
        int owed = pendingRefund;
        pendingRefund = 0;
        return owed;
    }

    /** Current maximum level for each stat. */
    public int cap() {
        return BASE_CAP + prestige * CAP_PER_PRESTIGE;
    }

    /** XP levels needed to raise this stat by one: the Nth point costs N levels. */
    public int costFor(StatType type) {
        return get(type) + 1;
    }

    public boolean isMaxed(StatType type) {
        return get(type) >= cap();
    }

    public boolean allMaxed() {
        for (StatType type : StatType.values()) {
            if (!isMaxed(type)) {
                return false;
            }
        }
        return true;
    }

    // ---- prestige ----

    public int getPrestige() {
        return prestige;
    }

    /** XP orb multiplier from prestige alone: x1, then x2, x4, x8 ... */
    public double prestigeXpMultiplier() {
        return Math.pow(2.0, Math.min(prestige, MAX_XP_PRESTIGE));
    }

    /** Points the next prestige will pay out: 1 for the first, 2 for the second, and so on. */
    public int nextPrestigePoints() {
        return prestige + 1;
    }

    /** Prestige: reset every stat, raise the cap and pay out points. The caller checks {@link #allMaxed()}. */
    public void prestige() {
        prestige++;
        points += prestige;
        for (StatType type : StatType.values()) {
            set(type, PRESTIGE_RESET_LEVEL);
        }
    }

    // ---- perks ----

    public int getPoints() {
        return points;
    }

    public void addPoints(int amount) {
        points = Math.max(0, points + amount);
    }

    public boolean isUnlocked(PerkType type) {
        return unlocked.contains(type);
    }

    public boolean isEnabled(PerkType type) {
        return enabled.contains(type);
    }

    /** True when the perk is unlocked and switched on. Gameplay code should always check this. */
    public boolean active(PerkType type) {
        return unlocked.contains(type) && enabled.contains(type);
    }

    /** Unlocking also switches the perk on. */
    public void unlock(PerkType type) {
        unlocked.add(type);
        enabled.add(type);
    }

    public void setEnabled(PerkType type, boolean on) {
        if (on && unlocked.contains(type)) {
            enabled.add(type);
        } else {
            enabled.remove(type);
        }
    }

    public long getLastEggClaim() {
        return lastEggClaim;
    }

    public void setLastEggClaim(long gameTime) {
        lastEggClaim = gameTime;
    }

    public long getLastSecondWind() {
        return lastSecondWind;
    }

    public void setLastSecondWind(long gameTime) {
        lastSecondWind = gameTime;
    }

    /** Ticks until Second Wind is ready again, as of the last sync. 0 means ready. */
    public int getSecondWindCooldownTicks() {
        return secondWindCooldownTicks;
    }

    public void setSecondWindCooldownTicks(int ticks) {
        this.secondWindCooldownTicks = Math.max(0, ticks);
    }

    @Nullable
    public Marker getMarker() {
        return marker;
    }

    public void setMarker(@Nullable Marker marker) {
        this.marker = marker;
    }

    @Nullable
    public Pocket getPocket() {
        return pocket;
    }

    public void setPocket(@Nullable Pocket pocket) {
        this.pocket = pocket;
    }

    public boolean isEndFreed() {
        return endFreed;
    }

    public void setEndFreed(boolean endFreed) {
        this.endFreed = endFreed;
    }

    /** Ticks until the next egg can be claimed, as of the last sync. 0 means ready. */
    public int getEggCooldownTicks() {
        return eggCooldownTicks;
    }

    public void setEggCooldownTicks(int ticks) {
        this.eggCooldownTicks = Math.max(0, ticks);
    }

    // ---- copying and networking ----

    public PlayerStats copy() {
        PlayerStats copy = new PlayerStats();
        System.arraycopy(levels, 0, copy.levels, 0, levels.length);
        System.arraycopy(sliders, 0, copy.sliders, 0, sliders.length);
        copy.prestige = prestige;
        copy.pendingRefund = pendingRefund;
        copy.points = points;
        copy.unlocked.addAll(unlocked);
        copy.enabled.addAll(enabled);
        copy.lastEggClaim = lastEggClaim;
        copy.marker = marker;
        copy.pocket = pocket;
        copy.endFreed = endFreed;
        copy.eggCooldownTicks = eggCooldownTicks;
        copy.lastSecondWind = lastSecondWind;
        copy.secondWindCooldownTicks = secondWindCooldownTicks;
        return copy;
    }

    private static int mask(EnumSet<PerkType> set) {
        int mask = 0;
        for (PerkType type : set) {
            mask |= 1 << type.ordinal();
        }
        return mask;
    }

    private static void fromMask(EnumSet<PerkType> set, int mask) {
        for (PerkType type : PerkType.values()) {
            if ((mask & (1 << type.ordinal())) != 0) {
                set.add(type);
            }
        }
    }

    public void write(ByteBuf buf) {
        buf.writeInt(prestige);
        for (int level : levels) {
            buf.writeInt(level);
        }
        for (int value : sliders) {
            buf.writeInt(value);
        }
        buf.writeInt(points);
        buf.writeInt(mask(unlocked));
        buf.writeInt(mask(enabled));
        buf.writeBoolean(endFreed);
        buf.writeInt(eggCooldownTicks);
        buf.writeInt(secondWindCooldownTicks);
        buf.writeBoolean(marker != null);
        if (marker != null) {
            ByteBufCodecs.STRING_UTF8.encode(buf, marker.dim());
            buf.writeDouble(marker.x());
            buf.writeDouble(marker.y());
            buf.writeDouble(marker.z());
        }
        buf.writeBoolean(pocket != null);
        if (pocket != null) {
            ByteBufCodecs.STRING_UTF8.encode(buf, pocket.name());
        }
    }

    public static PlayerStats read(ByteBuf buf) {
        PlayerStats stats = new PlayerStats();
        stats.prestige = buf.readInt();
        for (int i = 0; i < stats.levels.length; i++) {
            stats.levels[i] = buf.readInt();
        }
        for (int i = 0; i < stats.sliders.length; i++) {
            stats.sliders[i] = buf.readInt();
        }
        stats.points = buf.readInt();
        fromMask(stats.unlocked, buf.readInt());
        fromMask(stats.enabled, buf.readInt());
        stats.endFreed = buf.readBoolean();
        stats.eggCooldownTicks = buf.readInt();
        stats.secondWindCooldownTicks = buf.readInt();
        if (buf.readBoolean()) {
            String dim = ByteBufCodecs.STRING_UTF8.decode(buf);
            stats.marker = new Marker(dim, buf.readDouble(), buf.readDouble(), buf.readDouble());
        }
        if (buf.readBoolean()) {
            stats.pocket = new Pocket(ByteBufCodecs.STRING_UTF8.decode(buf), new CompoundTag());
        }
        return stats;
    }
}
