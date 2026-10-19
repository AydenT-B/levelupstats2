package com.levelupstats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Game-bus events behind the passive prestige perks. Every check goes through
 * {@link PlayerStats#active}, so a perk that is locked or switched off does nothing.
 */
@EventBusSubscriber(modid = LevelUpStats.MODID)
public class PerkEvents {
    /** Blocks queued by Vein Miner / Tree Feller, broken at the end of the tick the first block broke. */
    private record Chain(UUID player, ResourceKey<Level> dim, List<BlockPos> positions, Block block, boolean tree) {
    }

    private static final Deque<Chain> CHAINS = new ArrayDeque<>();
    /** True while a chain is being broken, so those breaks do not start new chains. */
    private static boolean chaining = false;

    // ---- per-player tick ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        PlayerStats stats = player.getData(ModAttachments.STATS.get());

        // Runs on both sides: the client moves the player, the server must agree with where they end up.
        if (stats.active(PerkType.WATER_WALK)) {
            waterWalk(player);
        }

        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (stats.active(PerkType.LAVA_SWIM) && serverPlayer.isInLava()) {
            serverPlayer.setRemainingFireTicks(0);
        }
        if (serverPlayer.tickCount % 10 == 0) {
            markerLeash(serverPlayer, stats);
            if (stats.active(PerkType.MOB_PEACE)) {
                peaceSweep(serverPlayer);
            }
        }
    }

    private static void waterWalk(Player player) {
        if (player.isShiftKeyDown() || player.getAbilities().flying || player.isSpectator()
                || player.isPassenger() || player.isFallFlying() || player.isInLava()) {
            return;
        }
        Level level = player.level();
        BlockPos below = BlockPos.containing(player.getX(), player.getY() - 0.05, player.getZ());
        FluidState fluid = level.getFluidState(below);
        if (!fluid.is(FluidTags.WATER) || !level.getFluidState(below.above()).isEmpty()) {
            return; // not water, or too deep to stand on the surface
        }
        double surface = below.getY() + fluid.getHeight(level, below);
        double y = player.getY();
        if (y > surface + 0.15 || y < surface - 0.6) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        if (motion.y < 0.0) {
            player.setDeltaMovement(motion.x, 0.0, motion.z);
        }
        if (y < surface) {
            player.setPos(player.getX(), surface, player.getZ());
        }
        player.setOnGround(true);
        player.fallDistance = 0.0F;
        if (player instanceof ServerPlayer serverPlayer) {
            FloatingKickGuard.reset(serverPlayer);
        }
    }

    private static void markerLeash(ServerPlayer player, PlayerStats stats) {
        PlayerStats.Marker marker = stats.getMarker();
        if (marker == null || !stats.active(PerkType.MARK_TP)) {
            return;
        }
        if (!player.level().dimension().location().toString().equals(marker.dim())) {
            return; // the pull-back only applies in the marker's own dimension
        }
        double range = PerkRules.MARK_RANGE;
        if (player.distanceToSqr(marker.x(), marker.y(), marker.z()) > range * range) {
            PerkManager.recall(player, true);
        }
    }

    // ---- Peacemaker ----

    private static boolean peaceApplies(ServerPlayer player, Mob mob) {
        if (!StatManager.stats(player).active(PerkType.MOB_PEACE)) {
            return false;
        }
        if (!(mob instanceof Enemy) || PerkManager.isProvoked(mob)) {
            return false;
        }
        return !(PerkRules.PEACE_EXCLUDES_BOSSES && PerkRules.isBossLike(mob.getType()));
    }

    /** Mobs that were already hunting you when you switched the perk on forget about you. */
    private static void peaceSweep(ServerPlayer player) {
        for (Mob mob : player.serverLevel().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(32.0),
                m -> m.getTarget() == player && peaceApplies(player, m))) {
            mob.setTarget(null);
        }
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        // A mob locked onto a live decoy keeps it: vanilla target goals would otherwise drop it straight away,
        // because they ignore invulnerable targets.
        if (event.getEntity() instanceof Mob locked) {
            LivingEntity current = locked.getTarget();
            if (current != null && current != event.getNewAboutToBeSetTarget() && PerkManager.isActiveDecoy(current)) {
                event.setCanceled(true);
                return;
            }
        }
        if (event.getNewAboutToBeSetTarget() instanceof ServerPlayer player
                && event.getEntity() instanceof Mob mob
                && peaceApplies(player, mob)) {
            event.setNewAboutToBeSetTarget(null);
        }
    }

    // ---- damage: Lavaproof, Peacemaker, own lightning ----

    @SubscribeEvent
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        Entity attacker = source.getEntity();

        // Hurting a mob is what "provokes" it, so Peacemaker stops protecting you from that mob.
        if (victim instanceof Mob && attacker instanceof ServerPlayer) {
            PerkManager.markProvoked(victim);
        }
        if (!(victim instanceof ServerPlayer player)) {
            return;
        }
        PlayerStats stats = StatManager.stats(player);

        if (PerkManager.isSecondWindProtected(player) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
            return;
        }
        if (stats.active(PerkType.LAVA_SWIM)
                && (source.is(DamageTypes.LAVA)
                || (player.isInLava() && (source.is(DamageTypes.IN_FIRE) || source.is(DamageTypes.ON_FIRE))))) {
            event.setCanceled(true);
            return;
        }
        if (attacker instanceof Mob mob && peaceApplies(player, mob)) {
            event.setCanceled(true);
        }
    }

    /** The player who called a bolt with Lightning Caller is not struck by it (no damage, no fire). */
    @SubscribeEvent
    public static void onStruck(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLightning().getCause() == player) {
            event.setCanceled(true);
        }
    }

    // ---- Second Wind ----

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && PerkManager.trySecondWind(player)) {
            event.setCanceled(true);
        }
    }

    // ---- Lightning Caller ----

    @SubscribeEvent
    public static void onAttack(AttackEntityEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Entity target = event.getTarget();
        if (target instanceof Mob) {
            PerkManager.markProvoked(target);
        }
        PlayerStats stats = StatManager.stats(player);
        if (stats.active(PerkType.LIGHTNING)
                && player.isShiftKeyDown()
                && player.getMainHandItem().getItem() instanceof AxeItem
                && target instanceof LivingEntity living
                && !(target instanceof ArmorStand)
                && PerkManager.tryUse(player, PerkType.LIGHTNING, PerkRules.LIGHTNING_COOLDOWN)) {
            ServerLevel level = player.serverLevel();
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(living.blockPosition()));
                bolt.setCause(player);
                level.addFreshEntity(bolt);
            }
        }
    }

    // ---- Earthshaker ----

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerStats stats = StatManager.stats(player);
        if (!stats.active(PerkType.EARTHSHAKER) || event.getDistance() < PerkRules.EARTHSHAKER_MIN_FALL) {
            return;
        }
        event.setDamageMultiplier(0.0F);
        shockwave(player, event.getDistance());
    }

    private static void shockwave(ServerPlayer player, float fallen) {
        ServerLevel level = player.serverLevel();
        double strength = StatManager.effective(player, StatType.STRENGTH);
        double knockStat = StatEffects.knockback(strength); // the Strength knockback bonus
        double power = 0.8 + knockStat * 1.5;
        double radius = Math.min(10.0, 3.5 + fallen * 0.25);
        float damage = (float) Math.min(30.0, 2.0 + fallen * 0.5 + StatEffects.damage(strength) * 0.5);
        Vec3 center = player.position();

        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius, 2.0, radius),
                e -> e != player && e.isAlive() && !(e instanceof Player) && !(e instanceof ArmorStand)
                        && !(e instanceof TamableAnimal pet && pet.isOwnedBy(player)));
        for (LivingEntity target : targets) {
            double distance = target.distanceTo(player);
            if (distance > radius) {
                continue;
            }
            double falloff = Math.max(0.25, 1.0 - distance / radius);
            target.hurt(level.damageSources().playerAttack(player), (float) (damage * falloff));

            double awayX = center.x - target.getX();
            double awayZ = center.z - target.getZ();
            if (awayX * awayX + awayZ * awayZ < 1.0E-4) {
                awayX = level.random.nextDouble() - 0.5;
                awayZ = level.random.nextDouble() - 0.5;
            }
            target.knockback(power * falloff, awayX, awayZ);
            target.setDeltaMovement(target.getDeltaMovement().add(0.0, Math.min(0.9, 0.2 + knockStat * 0.3) * falloff, 0.0));
            target.hurtMarked = true;
        }

        level.sendParticles(ParticleTypes.EXPLOSION, center.x, center.y + 0.2, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        level.sendParticles(ParticleTypes.POOF, center.x, center.y + 0.1, center.z, 40, radius / 3.0, 0.1, radius / 3.0, 0.05);
        level.playSound(null, player.blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0F, 0.6F);
    }

    // ---- Vein Miner / Tree Feller ----

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (chaining || !(event.getPlayer() instanceof ServerPlayer player) || !player.isShiftKeyDown()) {
            return;
        }
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        PlayerStats stats = StatManager.stats(player);
        BlockState state = event.getState();
        ItemStack tool = player.getMainHandItem();

        boolean tree;
        if (stats.active(PerkType.TREE_CHOP) && state.is(BlockTags.LOGS) && tool.getItem() instanceof AxeItem) {
            tree = true;
        } else if (stats.active(PerkType.VEIN_MINE) && state.is(Tags.Blocks.ORES) && tool.isCorrectToolForDrops(state)) {
            tree = false;
        } else {
            return;
        }
        List<BlockPos> blocks = flood(level, event.getPos(), state.getBlock(), tree);
        if (!blocks.isEmpty()) {
            CHAINS.add(new Chain(player.getUUID(), level.dimension(), blocks, state.getBlock(), tree));
        }
    }

    /** Finds the connected blocks (including diagonals), nearest first, up to the per-swing limit. */
    private static List<BlockPos> flood(ServerLevel level, BlockPos origin, Block block, boolean tree) {
        List<BlockPos> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        int limit = PerkRules.CHAIN_LIMIT - 1; // the block that was hit counts too
        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) {
                            continue;
                        }
                        BlockPos next = current.offset(dx, dy, dz);
                        if (!seen.add(next) || !level.hasChunkAt(next)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(next);
                        boolean match = tree ? state.is(BlockTags.LOGS) : state.getBlock() == block;
                        if (match) {
                            result.add(next);
                            if (result.size() >= limit) {
                                return result;
                            }
                            queue.add(next);
                        }
                    }
                }
            }
        }
        return result;
    }

    private static void runChains(MinecraftServer server) {
        if (CHAINS.isEmpty()) {
            return;
        }
        chaining = true;
        try {
            Chain chain;
            while ((chain = CHAINS.poll()) != null) {
                ServerPlayer player = server.getPlayerList().getPlayer(chain.player());
                if (player == null || player.serverLevel().dimension() != chain.dim()) {
                    continue;
                }
                ServerLevel level = player.serverLevel();
                for (BlockPos pos : chain.positions()) {
                    ItemStack tool = player.getMainHandItem();
                    BlockState state = level.getBlockState(pos);
                    if (chain.tree()) {
                        if (!(tool.getItem() instanceof AxeItem)) {
                            break; // axe swapped or broken
                        }
                        if (!state.is(BlockTags.LOGS)) {
                            continue;
                        }
                    } else {
                        if (state.getBlock() != chain.block()) {
                            continue;
                        }
                        if (!tool.isCorrectToolForDrops(state)) {
                            break; // tool swapped or broken
                        }
                    }
                    player.gameMode.destroyBlock(pos);
                }
            }
        } finally {
            chaining = false;
        }
    }

    // ---- Ore Sight and Pocket Dimension (release) ----

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerStats stats = StatManager.stats(player);
        ItemStack held = player.getMainHandItem();

        if (held.isEmpty() && player.isShiftKeyDown() && stats.active(PerkType.POCKET) && stats.getPocket() != null) {
            if (PerkManager.release(player, event.getPos(), event.getFace())) {
                event.setCanceled(true);
                event.setCancellationResult(InteractionResult.SUCCESS);
            }
            return;
        }
        if (stats.active(PerkType.ORE_GLOW) && held.getItem() instanceof PickaxeItem
                && PerkManager.isStoneWall(player.level().getBlockState(event.getPos()))) {
            PerkManager.oreGlow(player, event.getPos());
        }
    }

    // ---- Pocket Dimension (capture) ----

    @SubscribeEvent
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (!player.isShiftKeyDown() || !player.getMainHandItem().isEmpty()
                || !(event.getTarget() instanceof Mob target)) {
            return;
        }
        if (!StatManager.stats(player).active(PerkType.POCKET)) {
            return;
        }
        PerkManager.capture(player, target);
        // Success or not, this click belongs to the perk (the player gets a message either way).
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
    }

    // ---- Decoy protection ----

    @SubscribeEvent
    public static void onDecoyInteract(PlayerInteractEvent.EntityInteractSpecific event) {
        if (event.getTarget().getPersistentData().getBoolean(PerkManager.DECOY_TAG)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.FAIL);
        }
    }

    // ---- housekeeping ----

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !event.loadedFromDisk()) {
            return;
        }
        Entity entity = event.getEntity();
        if (entity.getPersistentData().getBoolean(PerkManager.DECOY_TAG)) {
            event.setCanceled(true); // a decoy left over from a crash or restart
            return;
        }
        PerkManager.restoreIfFrozen(entity);
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && event.getAdvancement().id().getPath().equals("end/kill_dragon")) {
            StatManager.sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        runChains(event.getServer());
        PerkManager.tickServer(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        CHAINS.clear();
        PerkManager.shutdown(event.getServer());
    }
}
