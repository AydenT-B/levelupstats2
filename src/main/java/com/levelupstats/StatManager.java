package com.levelupstats;

import com.levelupstats.network.SyncStatsPayload;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Server-side logic: spending levels, applying attribute modifiers and syncing to the client.
 */
public final class StatManager {
    /** Modifier ids written by version 1 of the mod. They are saved with the player, so they must be removed. */
    private static final List<ResourceLocation> LEGACY_IDS = List.of(
            ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "speed"),
            ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "jump"),
            ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "strength"),
            ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "luck"),
            ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "constitution"),
            ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "intelligence"));

    private static final List<Holder<Attribute>> LEGACY_ATTRIBUTES = List.of(
            Attributes.MOVEMENT_SPEED, Attributes.JUMP_STRENGTH, Attributes.SAFE_FALL_DISTANCE,
            Attributes.ATTACK_DAMAGE, Attributes.LUCK, Attributes.MAX_HEALTH);

    private StatManager() {
    }

    public static PlayerStats stats(ServerPlayer player) {
        return player.getData(ModAttachments.STATS.get());
    }

    /** The stat's effective level (level x slider) for this player. */
    public static double effective(ServerPlayer player, StatType type) {
        return stats(player).effective(type);
    }

    public static void tryUpgrade(ServerPlayer player, int statIndex) {
        StatType[] types = StatType.values();
        if (statIndex < 0 || statIndex >= types.length) {
            return;
        }
        StatType type = types[statIndex];
        PlayerStats stats = stats(player);

        if (stats.isMaxed(type)) {
            player.displayClientMessage(Component.translatable("message.levelupstats.maxed"), true);
            return;
        }

        int cost = stats.costFor(type);
        if (player.experienceLevel < cost) {
            player.displayClientMessage(Component.translatable("message.levelupstats.not_enough", cost), true);
            return;
        }

        player.giveExperienceLevels(-cost);
        stats.set(type, stats.get(type) + 1);
        player.setData(ModAttachments.STATS.get(), stats);

        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.3F);
        applyAndSync(player);
    }

    public static void setSlider(ServerPlayer player, int statIndex, int percent) {
        StatType[] types = StatType.values();
        if (statIndex < 0 || statIndex >= types.length) {
            return;
        }
        PlayerStats stats = stats(player);
        stats.setSlider(types[statIndex], percent);
        player.setData(ModAttachments.STATS.get(), stats);
        applyAndSync(player);
    }

    public static void tryPrestige(ServerPlayer player) {
        PlayerStats stats = stats(player);
        if (!stats.allMaxed()) {
            player.displayClientMessage(Component.translatable("message.levelupstats.prestige_locked", stats.cap()), true);
            return;
        }
        int gained = stats.nextPrestigePoints();
        stats.prestige(); // resets every stat, raises the cap and pays out the points
        player.setData(ModAttachments.STATS.get(), stats);
        player.level().playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 0.8F, 1.0F);
        player.sendSystemMessage(Component.translatable("message.levelupstats.prestiged",
                stats.getPrestige(), gained, (long) stats.prestigeXpMultiplier(), stats.cap()));
        applyAndSync(player);
    }

    public static void unlockPerk(ServerPlayer player, int perkIndex) {
        PerkType[] types = PerkType.values();
        if (perkIndex < 0 || perkIndex >= types.length) {
            return;
        }
        PerkType type = types[perkIndex];
        PlayerStats stats = stats(player);
        if (stats.isUnlocked(type)) {
            return;
        }
        if (stats.getPoints() < type.cost()) {
            player.displayClientMessage(Component.translatable("message.levelupstats.not_enough_points", type.cost()), true);
            return;
        }
        stats.addPoints(-type.cost());
        stats.unlock(type);
        player.setData(ModAttachments.STATS.get(), stats);
        player.level().playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 0.8F);
        player.displayClientMessage(Component.translatable("message.levelupstats.perk_unlocked", Component.translatable(type.nameKey())), true);
        sync(player);
    }

    public static void togglePerk(ServerPlayer player, int perkIndex) {
        PerkType[] types = PerkType.values();
        if (perkIndex < 0 || perkIndex >= types.length) {
            return;
        }
        PerkType type = types[perkIndex];
        PlayerStats stats = stats(player);
        if (!stats.isUnlocked(type)) {
            return;
        }
        stats.setEnabled(type, !stats.isEnabled(type));
        player.setData(ModAttachments.STATS.get(), stats);
        sync(player);
    }

    /** Pays out XP levels owed from the stat rework (old saves only). */
    public static void payRefund(ServerPlayer player) {
        PlayerStats stats = stats(player);
        int owed = stats.takeRefund();
        if (owed > 0) {
            player.setData(ModAttachments.STATS.get(), stats);
            player.giveExperienceLevels(owed);
            player.sendSystemMessage(Component.translatable("message.levelupstats.refund", owed));
        }
    }

    public static void applyAndSync(ServerPlayer player) {
        applyAll(player);
        sync(player);
    }

    public static void sync(ServerPlayer player) {
        PlayerStats stats = stats(player);
        stats.setEndFreed(PerkManager.hasFreedTheEnd(player));
        stats.setEggCooldownTicks(PerkManager.eggCooldownRemaining(player, stats));
        stats.setSecondWindCooldownTicks(PerkManager.secondWindCooldownRemaining(player, stats));
        PacketDistributor.sendToPlayer(player, new SyncStatsPayload(stats.copy()));
    }

    /** (Re)applies every attribute modifier from the player's current stats. Safe to call repeatedly. */
    public static void applyAll(ServerPlayer player) {
        PlayerStats s = stats(player);
        float oldMaxHealth = player.getMaxHealth();

        removeLegacyModifiers(player);

        double str = s.effective(StatType.STRENGTH);
        setModifier(player, Attributes.ATTACK_DAMAGE, StatType.STRENGTH.modifierId("damage"),
                StatEffects.damage(str), Operation.ADD_VALUE);
        setModifier(player, Attributes.ATTACK_KNOCKBACK, StatType.STRENGTH.modifierId("knockback"),
                StatEffects.knockback(str), Operation.ADD_VALUE);
        setModifier(player, Attributes.ENTITY_INTERACTION_RANGE, StatType.STRENGTH.modifierId("entity_reach"),
                StatEffects.reach(str), Operation.ADD_VALUE);
        setModifier(player, Attributes.BLOCK_INTERACTION_RANGE, StatType.STRENGTH.modifierId("block_reach"),
                StatEffects.reach(str), Operation.ADD_VALUE);

        double dex = s.effective(StatType.DEXTERITY);
        setModifier(player, Attributes.MOVEMENT_SPEED, StatType.DEXTERITY.modifierId("speed"),
                StatEffects.speed(dex), Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.JUMP_STRENGTH, StatType.DEXTERITY.modifierId("jump"),
                StatEffects.jump(dex), Operation.ADD_MULTIPLIED_BASE);
        setModifier(player, Attributes.SAFE_FALL_DISTANCE, StatType.DEXTERITY.modifierId("safe_fall"),
                StatEffects.safeFall(dex), Operation.ADD_VALUE);
        setModifier(player, Attributes.ATTACK_SPEED, StatType.DEXTERITY.modifierId("attack_speed"),
                StatEffects.attackSpeed(dex), Operation.ADD_MULTIPLIED_BASE);

        double con = s.effective(StatType.CONSTITUTION);
        setModifier(player, Attributes.MAX_HEALTH, StatType.CONSTITUTION.modifierId("health"),
                StatEffects.maxHealth(con), Operation.ADD_VALUE);

        double intel = s.effective(StatType.INTELLIGENCE);
        setModifier(player, Attributes.LUCK, StatType.INTELLIGENCE.modifierId("luck"),
                StatEffects.luckAttribute(intel), Operation.ADD_VALUE);

        float newMaxHealth = player.getMaxHealth();
        if (newMaxHealth > oldMaxHealth) {
            player.heal(newMaxHealth - oldMaxHealth);
        } else if (player.getHealth() > newMaxHealth) {
            player.setHealth(newMaxHealth);
        }
    }

    private static void removeLegacyModifiers(ServerPlayer player) {
        for (Holder<Attribute> attribute : LEGACY_ATTRIBUTES) {
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance != null) {
                for (ResourceLocation id : LEGACY_IDS) {
                    instance.removeModifier(id);
                }
            }
        }
    }

    private static void setModifier(ServerPlayer player, Holder<Attribute> attribute, ResourceLocation id,
                                    double amount, Operation operation) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        instance.removeModifier(id);
        if (amount != 0) {
            instance.addPermanentModifier(new AttributeModifier(id, amount, operation));
        }
    }
}
