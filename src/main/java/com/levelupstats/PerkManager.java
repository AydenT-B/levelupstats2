package com.levelupstats;

import com.levelupstats.network.OreGlowPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Server-side actions for the prestige perks that are triggered by a keybind, a click or a timer.
 * Passive perks (water walking, vein mining, ...) live in {@link PerkEvents}.
 */
public final class PerkManager {
    /** Saved on entities so a crash or restart can never leave one frozen or leave a decoy behind. */
    public static final String FROZEN_TAG = "levelupstats_frozen";
    public static final String DECOY_TAG = "levelupstats_decoy";
    public static final String PROVOKED_TAG = "levelupstats_provoked";

    private static final ResourceLocation FREE_THE_END = ResourceLocation.fromNamespaceAndPath("minecraft", "end/kill_dragon");

    private static final Map<UUID, Map<PerkType, Long>> COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Frozen> FROZEN = new HashMap<>();
    private static final List<Decoy> DECOYS = new ArrayList<>();

    private static final class Frozen {
        ResourceKey<Level> dim;
        long until;
        boolean hadNoAi;
        @Nullable
        EnderDragonPhase<?> previousPhase;
    }

    private record Decoy(UUID stand, ResourceKey<Level> dim, long until) {
    }

    private PerkManager() {
    }

    // ---- shared helpers ----

    /** Game time used for all perk timers. The overworld clock is the same one the day cycle uses. */
    public static long now(MinecraftServer server) {
        return server.overworld().getGameTime();
    }

    public static boolean hasFreedTheEnd(ServerPlayer player) {
        var holder = player.server.getAdvancements().get(FREE_THE_END);
        return holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static int eggCooldownRemaining(ServerPlayer player, PlayerStats stats) {
        if (stats.getLastEggClaim() < 0) {
            return 0;
        }
        long left = stats.getLastEggClaim() + PerkRules.EGG_COOLDOWN_TICKS - now(player.server);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, left));
    }

    /**
     * Starts a cooldown if the perk is ready. When it is not, tells the player how long is left.
     * @return true if the action may go ahead
     */
    private static boolean useCooldown(ServerPlayer player, PerkType type, int ticks) {
        long now = now(player.server);
        Map<PerkType, Long> mine = COOLDOWNS.computeIfAbsent(player.getUUID(), id -> new EnumMap<>(PerkType.class));
        long readyAt = mine.getOrDefault(type, 0L);
        if (now < readyAt) {
            int seconds = (int) Math.ceil((readyAt - now) / 20.0);
            player.displayClientMessage(Component.translatable("message.levelupstats.cooldown",
                    Component.translatable(type.nameKey()), seconds), true);
            return false;
        }
        mine.put(type, now + ticks);
        return true;
    }

    /** Cooldown check for passive perks that fire from events. Same rules, but never starts one by itself. */
    public static boolean tryUse(ServerPlayer player, PerkType type, int cooldownTicks) {
        return useCooldown(player, type, cooldownTicks);
    }

    public static boolean isProvoked(Entity mob) {
        return mob.getPersistentData().getBoolean(PROVOKED_TAG);
    }

    public static void markProvoked(Entity mob) {
        mob.getPersistentData().putBoolean(PROVOKED_TAG, true);
    }

    // ---- Egg Conjurer ----

    public static void claimEgg(ServerPlayer player, ResourceLocation entityTypeId) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.EGG)) {
            return;
        }
        if (!hasFreedTheEnd(player)) {
            player.displayClientMessage(Component.translatable("message.levelupstats.egg_locked"), true);
            return;
        }
        int left = eggCooldownRemaining(player, stats);
        if (left > 0) {
            player.displayClientMessage(Component.translatable("message.levelupstats.egg_cooldown", formatTicks(left)), true);
            return;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entityTypeId).orElse(null);
        SpawnEggItem egg = type == null ? null : SpawnEggItem.byId(type);
        if (egg == null || PerkRules.isBossLike(type)) {
            player.displayClientMessage(Component.translatable("message.levelupstats.egg_invalid"), true);
            return;
        }
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(egg));
        stats.setLastEggClaim(now(player.server));
        player.setData(ModAttachments.STATS.get(), stats);
        player.displayClientMessage(Component.translatable("message.levelupstats.egg_claimed", type.getDescription()), true);
        StatManager.sync(player);
    }

    /** Ticks as a short "1h 20m" style text for messages. */
    public static String formatTicks(int ticks) {
        int minutes = (int) Math.ceil(ticks / 1200.0);
        if (minutes >= 60) {
            return (minutes / 60) + "h " + (minutes % 60) + "m";
        }
        return minutes + "m";
    }

    // ---- Marker Teleport ----

    public static void setMarker(ServerPlayer player) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.MARK_TP)) {
            return;
        }
        stats.setMarker(new PlayerStats.Marker(player.level().dimension().location().toString(),
                player.getX(), player.getY(), player.getZ()));
        player.setData(ModAttachments.STATS.get(), stats);
        player.displayClientMessage(Component.translatable("message.levelupstats.marker_set", (int) PerkRules.MARK_RANGE), true);
        StatManager.sync(player);
    }

    public static void clearMarker(ServerPlayer player) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.MARK_TP) || stats.getMarker() == null) {
            return;
        }
        stats.setMarker(null);
        player.setData(ModAttachments.STATS.get(), stats);
        player.displayClientMessage(Component.translatable("message.levelupstats.marker_cleared"), true);
        StatManager.sync(player);
    }

    /** Teleports the player to their marker, from any dimension. {@code auto} is true for the range pull-back. */
    public static void recall(ServerPlayer player, boolean auto) {
        PlayerStats stats = StatManager.stats(player);
        PlayerStats.Marker marker = stats.getMarker();
        if (!stats.active(PerkType.MARK_TP)) {
            return;
        }
        if (marker == null) {
            if (!auto) {
                player.displayClientMessage(Component.translatable("message.levelupstats.no_marker"), true);
            }
            return;
        }
        ResourceLocation dimId = ResourceLocation.tryParse(marker.dim());
        ServerLevel target = dimId == null ? null
                : player.server.getLevel(ResourceKey.create(Registries.DIMENSION, dimId));
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.levelupstats.marker_missing"), true);
            return;
        }
        player.stopRiding();
        player.teleportTo(target, marker.x(), marker.y(), marker.z(), player.getYRot(), player.getXRot());
        player.fallDistance = 0.0F;
        player.displayClientMessage(Component.translatable(auto
                ? "message.levelupstats.marker_pulled" : "message.levelupstats.marker_recalled"), true);
    }

    // ---- Ore Sight ----

    public static void oreGlow(ServerPlayer player, BlockPos center) {
        if (!useCooldown(player, PerkType.ORE_GLOW, PerkRules.ORE_GLOW_COOLDOWN)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        int r = PerkRules.ORE_GLOW_RADIUS;
        List<BlockPos> found = new ArrayList<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }
                    if (level.getBlockState(cursor).is(Tags.Blocks.ORES)) {
                        found.add(cursor.immutable());
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(pos -> pos.distSqr(center)));
        if (found.size() > PerkRules.ORE_GLOW_MAX) {
            found = new ArrayList<>(found.subList(0, PerkRules.ORE_GLOW_MAX));
        }

        List<Long> positions = new ArrayList<>(found.size());
        List<Integer> colors = new ArrayList<>(found.size());
        for (BlockPos pos : found) {
            positions.add(pos.asLong());
            colors.add(PerkRules.oreColor(level.getBlockState(pos)));
        }
        PacketDistributor.sendToPlayer(player, new OreGlowPayload(positions, colors));
        player.displayClientMessage(Component.translatable("message.levelupstats.ore_glow", found.size()), true);
    }

    // ---- Stasis Field ----

    public static void stasis(ServerPlayer player) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.STASIS) || !useCooldown(player, PerkType.STASIS, PerkRules.STASIS_COOLDOWN)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        double radius = PerkRules.STASIS_RADIUS;
        long start = now(player.server);
        int count = 0;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(radius),
                m -> m.isAlive() && m.distanceToSqr(player) <= radius * radius
                        && !(m instanceof net.minecraft.world.entity.TamableAnimal pet && pet.isOwnedBy(player)))) {
            int ticks = PerkRules.isBossLike(mob.getType()) ? PerkRules.STASIS_BOSS_TICKS : PerkRules.STASIS_TICKS;
            freeze(mob, level, start + ticks);
            count++;
        }
        level.playSound(null, player.blockPosition(), SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.PLAYERS, 1.5F, 0.5F);
        player.displayClientMessage(Component.translatable("message.levelupstats.stasis", count), true);
    }

    private static void freeze(Mob mob, ServerLevel level, long until) {
        Frozen existing = FROZEN.get(mob.getUUID());
        if (existing != null) {
            existing.until = Math.max(existing.until, until);
            return;
        }
        Frozen frozen = new Frozen();
        frozen.dim = level.dimension();
        frozen.until = until;
        frozen.hadNoAi = mob.isNoAi();
        mob.setNoAi(true);
        CompoundTag data = mob.getPersistentData();
        data.putBoolean(FROZEN_TAG, true);
        data.putBoolean(FROZEN_TAG + "_noai", frozen.hadNoAi);
        if (mob instanceof EnderDragon dragon) {
            // The dragon keeps flying even with its AI off, so park it in its "hover" phase instead.
            frozen.previousPhase = dragon.getPhaseManager().getCurrentPhase().getPhase();
            dragon.getPhaseManager().setPhase(EnderDragonPhase.HOVERING);
        }
        FROZEN.put(mob.getUUID(), frozen);
    }

    private static void unfreeze(Mob mob, @Nullable Frozen frozen) {
        CompoundTag data = mob.getPersistentData();
        boolean hadNoAi = frozen != null ? frozen.hadNoAi : data.getBoolean(FROZEN_TAG + "_noai");
        mob.setNoAi(hadNoAi);
        data.remove(FROZEN_TAG);
        data.remove(FROZEN_TAG + "_noai");
        if (frozen != null && frozen.previousPhase != null && mob instanceof EnderDragon dragon
                && frozen.previousPhase != EnderDragonPhase.HOVERING) {
            dragon.getPhaseManager().setPhase(frozen.previousPhase);
        }
    }

    /** Called when a mob loads from disk: undoes a freeze that was interrupted by a restart or chunk unload. */
    public static void restoreIfFrozen(Entity entity) {
        if (entity instanceof Mob mob && mob.getPersistentData().getBoolean(FROZEN_TAG)) {
            unfreeze(mob, null);
        }
    }

    // ---- Decoy Clone ----

    public static void decoy(ServerPlayer player) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.DECOY) || !useCooldown(player, PerkType.DECOY, PerkRules.DECOY_COOLDOWN)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        ArmorStand stand = new ArmorStand(level, player.getX(), player.getY(), player.getZ());
        stand.setYRot(player.getYRot());

        for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack worn = player.getItemBySlot(slot);
            if (!worn.isEmpty()) {
                stand.setItemSlot(slot, worn.copy());
            }
        }
        if (stand.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) {
            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            head.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            stand.setItemSlot(EquipmentSlot.HEAD, head);
        }
        // Arms and the base plate can only be set through its saved data.
        CompoundTag tag = new CompoundTag();
        stand.saveWithoutId(tag);
        tag.putBoolean("ShowArms", true);
        tag.putBoolean("NoBasePlate", true);
        tag.putInt("DisabledSlots", 4144959); // every slot locked: the copied gear cannot be taken off it
        stand.load(tag);

        stand.setInvulnerable(true);
        stand.getPersistentData().putBoolean(DECOY_TAG, true);
        level.addFreshEntity(stand);
        DECOYS.add(new Decoy(stand.getUUID(), level.dimension(), now(player.server) + PerkRules.DECOY_TICKS));

        player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, PerkRules.DECOY_TICKS, 0, false, false, true));
        pullHostiles(level, stand);
        player.displayClientMessage(Component.translatable("message.levelupstats.decoy"), true);
    }

    private static void pullHostiles(ServerLevel level, LivingEntity stand) {
        double radius = PerkRules.DECOY_AGGRO_RADIUS;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, stand.getBoundingBox().inflate(radius),
                m -> m instanceof Enemy && m.isAlive())) {
            mob.setTarget(stand);
        }
    }

    private static void releaseHostiles(ServerLevel level, LivingEntity stand) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, stand.getBoundingBox().inflate(64.0),
                m -> m.getTarget() == stand)) {
            mob.setTarget(null);
        }
    }

    /** True while this entity is a live decoy created by the Decoy Clone perk. */
    public static boolean isActiveDecoy(@Nullable Entity entity) {
        return entity != null && entity.isAlive() && entity.getPersistentData().getBoolean(DECOY_TAG);
    }

    // ---- Second Wind ----

    private static final Map<UUID, Long> INVULNERABLE_UNTIL = new HashMap<>();

    public static int secondWindCooldownRemaining(ServerPlayer player, PlayerStats stats) {
        if (stats.getLastSecondWind() < 0) {
            return 0;
        }
        long left = stats.getLastSecondWind() + PerkRules.SECOND_WIND_COOLDOWN_TICKS - now(player.server);
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, left));
    }

    /** True while the brief immunity after Second Wind is running. */
    public static boolean isSecondWindProtected(ServerPlayer player) {
        Long until = INVULNERABLE_UNTIL.get(player.getUUID());
        if (until == null) {
            return false;
        }
        if (now(player.server) >= until) {
            INVULNERABLE_UNTIL.remove(player.getUUID());
            return false;
        }
        return true;
    }

    /**
     * Called when the player would die. Returns true if Second Wind saved them, in which case the death
     * must be cancelled.
     */
    public static boolean trySecondWind(ServerPlayer player) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.SECOND_WIND) || secondWindCooldownRemaining(player, stats) > 0) {
            return false;
        }
        long now = now(player.server);
        stats.setLastSecondWind(now);
        player.setData(ModAttachments.STATS.get(), stats);
        INVULNERABLE_UNTIL.put(player.getUUID(), now + PerkRules.SECOND_WIND_INVULN_TICKS);

        player.setHealth(Math.max(1.0F, player.getMaxHealth() * PerkRules.SECOND_WIND_HEALTH_FRACTION));
        player.clearFire();
        player.fallDistance = 0.0F;
        ServerLevel level = player.serverLevel();
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.TOTEM_OF_UNDYING,
                player.getX(), player.getY() + 1.0, player.getZ(), 40, 0.4, 0.8, 0.4, 0.3);
        level.playSound(null, player.blockPosition(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.PLAYERS, 1.0F, 1.2F);
        player.displayClientMessage(Component.translatable("message.levelupstats.second_wind"), true);
        StatManager.sync(player);
        return true;
    }

    // ---- Pocket Dimension ----

    public static boolean capture(ServerPlayer player, LivingEntity target) {
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.POCKET)) {
            return false;
        }
        if (stats.getPocket() != null) {
            player.displayClientMessage(Component.translatable("message.levelupstats.pocket_full"), true);
            return false;
        }
        if (target instanceof Player || !target.isAlive() || PerkRules.isBossLike(target.getType())
                || target.isPassenger() || target.isVehicle()) {
            player.displayClientMessage(Component.translatable("message.levelupstats.pocket_invalid"), true);
            return false;
        }
        double limit = PerkRules.POCKET_HEALTH_FRACTION * player.getMaxHealth();
        if (target.getHealth() >= limit) {
            player.displayClientMessage(Component.translatable("message.levelupstats.pocket_too_strong",
                    String.format(java.util.Locale.ROOT, "%.1f", target.getHealth()),
                    String.format(java.util.Locale.ROOT, "%.1f", limit)), true);
            return false;
        }
        CompoundTag tag = new CompoundTag();
        if (!target.save(tag)) {
            player.displayClientMessage(Component.translatable("message.levelupstats.pocket_invalid"), true);
            return false;
        }
        if (target instanceof Mob mob && mob.isLeashed()) {
            mob.dropLeash(true, true);
        }
        String name = target.getDisplayName().getString();
        stats.setPocket(new PlayerStats.Pocket(name, tag));
        player.setData(ModAttachments.STATS.get(), stats);
        target.discard();
        player.serverLevel().playSound(null, player.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 1.4F);
        player.displayClientMessage(Component.translatable("message.levelupstats.pocket_stored", name), true);
        StatManager.sync(player);
        return true;
    }

    public static boolean release(ServerPlayer player, BlockPos clicked, Direction face) {
        PlayerStats stats = StatManager.stats(player);
        PlayerStats.Pocket pocket = stats.getPocket();
        if (!stats.active(PerkType.POCKET) || pocket == null) {
            return false;
        }
        ServerLevel level = player.serverLevel();
        BlockPos spot = clicked.relative(face);
        Entity entity = EntityType.loadEntityRecursive(pocket.tag().copy(), level, loaded -> {
            loaded.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, player.getYRot() + 180.0F, 0.0F);
            loaded.setUUID(UUID.randomUUID());
            loaded.fallDistance = 0.0F;
            return loaded;
        });
        if (entity == null) {
            player.displayClientMessage(Component.translatable("message.levelupstats.pocket_failed"), true);
            return false;
        }
        level.addFreshEntityWithPassengers(entity);
        stats.setPocket(null);
        player.setData(ModAttachments.STATS.get(), stats);
        level.playSound(null, spot, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.7F, 0.8F);
        player.displayClientMessage(Component.translatable("message.levelupstats.pocket_released", pocket.name()), true);
        StatManager.sync(player);
        return true;
    }

    // ---- timers ----

    /** Runs once per server tick: ends freezes and decoys and keeps decoys attracting monsters. */
    public static void tickServer(MinecraftServer server) {
        if (FROZEN.isEmpty() && DECOYS.isEmpty()) {
            return;
        }
        long now = now(server);

        Iterator<Map.Entry<UUID, Frozen>> frozenIt = FROZEN.entrySet().iterator();
        while (frozenIt.hasNext()) {
            Map.Entry<UUID, Frozen> entry = frozenIt.next();
            Frozen frozen = entry.getValue();
            ServerLevel level = server.getLevel(frozen.dim);
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (!(entity instanceof Mob mob)) {
                // Unloaded or gone. If it was only unloaded, restoreIfFrozen fixes it when it loads again.
                frozenIt.remove();
                continue;
            }
            if (now >= frozen.until || !mob.isAlive()) {
                unfreeze(mob, frozen);
                frozenIt.remove();
            } else {
                mob.setDeltaMovement(Vec3.ZERO);
            }
        }

        Iterator<Decoy> decoyIt = DECOYS.iterator();
        while (decoyIt.hasNext()) {
            Decoy decoy = decoyIt.next();
            ServerLevel level = server.getLevel(decoy.dim());
            Entity entity = level == null ? null : level.getEntity(decoy.stand());
            if (!(entity instanceof ArmorStand stand) || !stand.isAlive() || now >= decoy.until()) {
                if (entity instanceof LivingEntity living && level != null) {
                    releaseHostiles(level, living);
                }
                if (entity != null) {
                    entity.discard();
                }
                decoyIt.remove();
                continue;
            }
            if (now % 5 == 0) {
                pullHostiles(level, stand);
            }
        }
    }

    /** Server is stopping: put everything back the way it was. */
    public static void shutdown(MinecraftServer server) {
        for (Map.Entry<UUID, Frozen> entry : FROZEN.entrySet()) {
            ServerLevel level = server.getLevel(entry.getValue().dim);
            Entity entity = level == null ? null : level.getEntity(entry.getKey());
            if (entity instanceof Mob mob) {
                unfreeze(mob, entry.getValue());
            }
        }
        FROZEN.clear();
        for (Decoy decoy : DECOYS) {
            ServerLevel level = server.getLevel(decoy.dim());
            Entity entity = level == null ? null : level.getEntity(decoy.stand());
            if (entity != null) {
                entity.discard();
            }
        }
        DECOYS.clear();
        COOLDOWNS.clear();
        INVULNERABLE_UNTIL.clear();
    }

    /** True for blocks Ore Sight can be used on. */
    public static boolean isStoneWall(BlockState state) {
        return state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD)
                || state.is(net.minecraft.tags.BlockTags.BASE_STONE_NETHER);
    }
}
