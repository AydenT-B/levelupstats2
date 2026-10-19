package com.levelupstats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.enchanting.GetEnchantmentLevelEvent;
import net.neoforged.neoforge.event.entity.living.BabyEntitySpawnEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerXpEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;
import net.neoforged.neoforge.event.level.BlockDropsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/**
 * Game-bus events that make the stats actually do things. Every effect reads the stat's
 * <em>effective</em> level (real level x slider) via {@link StatManager#effective}.
 */
@EventBusSubscriber(modid = LevelUpStats.MODID)
public class StatEvents {
    /** Extra Fortune levels active while the current block break's drops are generated (server thread only). */
    private static final ThreadLocal<Integer> FORTUNE_BONUS = new ThreadLocal<>();
    /** Extra Looting levels active while the current mob death's drops are generated (server thread only). */
    private static final ThreadLocal<Integer> LOOTING_BONUS = new ThreadLocal<>();
    /** True between an XP orb touching a player and the XP being awarded. */
    private static boolean orbPickupInProgress = false;

    /** Furnaces a player has opened since the server started, so their output can be boosted by Wisdom. */
    private static final Map<GlobalPos, FurnaceTrack> FURNACES = new HashMap<>();

    private static final class FurnaceTrack {
        UUID owner;
        int lastCount = -1;
    }

    // ---- keeping attributes + client in sync ----

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatManager.payRefund(player);
            StatManager.applyAndSync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatManager.applyAndSync(player);
        }
    }

    @SubscribeEvent
    public static void onDimensionChange(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            StatManager.applyAndSync(player);
        }
    }

    // ---- Constitution: extra regeneration ----

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player && player.tickCount % 20 == 0) {
            double regen = StatEffects.regenPerSecond(StatManager.effective(player, StatType.CONSTITUTION));
            if (regen > 0 && player.isAlive() && player.getHealth() < player.getMaxHealth()
                    && player.getFoodData().getFoodLevel() >= 6) {
                player.heal((float) regen);
            }
        }
    }

    // ---- Intelligence: Fortune ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            int bonus = StatEffects.fortuneLootingBonus(StatManager.effective(player, StatType.INTELLIGENCE));
            if (bonus > 0) {
                FORTUNE_BONUS.set(bonus);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBlockDrops(BlockDropsEvent event) {
        // Drops have already been rolled by the time this fires, so the Fortune bonus is finished.
        FORTUNE_BONUS.remove();

        if (event.getBreaker() instanceof ServerPlayer player) {
            multiplyCropDrops(event, player);
        }
    }

    // ---- Intelligence: crop harvest multiplier ----

    private static void multiplyCropDrops(BlockDropsEvent event, ServerPlayer player) {
        BlockState state = event.getState();
        if (!isMatureCrop(state)) {
            return;
        }
        double multiplier = StatEffects.cropMultiplier(StatManager.effective(player, StatType.INTELLIGENCE));
        if (multiplier <= 1.0) {
            return;
        }
        ServerLevel level = event.getLevel();
        List<ItemEntity> extras = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            ItemStack stack = drop.getItem();
            double bonus = stack.getCount() * (multiplier - 1.0);
            int extra = (int) bonus;
            if (player.getRandom().nextDouble() < bonus - extra) {
                extra++;
            }
            while (extra > 0) {
                int amount = Math.min(extra, stack.getMaxStackSize());
                extras.add(new ItemEntity(level, drop.getX(), drop.getY(), drop.getZ(), stack.copyWithCount(amount)));
                extra -= amount;
            }
        }
        event.getDrops().addAll(extras);
    }

    /** True for fully grown crops. Works for vanilla crops and any modded crop that extends CropBlock. */
    private static boolean isMatureCrop(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        if (block == Blocks.NETHER_WART) {
            return state.getValue(NetherWartBlock.AGE) >= 3;
        }
        if (state.is(BlockTags.CROPS)) {
            for (Property<?> property : state.getProperties()) {
                if (property instanceof IntegerProperty age && age.getName().equals("age")) {
                    return state.getValue(age) >= Collections.max(age.getPossibleValues());
                }
            }
            return true;
        }
        return false;
    }

    // ---- Intelligence: Looting ----

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player && !(event.getEntity() instanceof Player)) {
            int bonus = StatEffects.fortuneLootingBonus(StatManager.effective(player, StatType.INTELLIGENCE));
            if (bonus > 0) {
                LOOTING_BONUS.set(bonus);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        LOOTING_BONUS.remove();

        // Vanilla only reads Looting from a held item, so with an empty hand the enchantment hook below
        // has nothing to attach to. In that case apply a simplified bonus directly to stackable drops.
        if (event.getSource().getEntity() instanceof ServerPlayer player
                && event.getEntity() instanceof Mob
                && player.getMainHandItem().isEmpty()) {
            int bonus = StatEffects.fortuneLootingBonus(StatManager.effective(player, StatType.INTELLIGENCE));
            if (bonus > 0) {
                for (ItemEntity drop : event.getDrops()) {
                    ItemStack stack = drop.getItem();
                    int room = stack.getMaxStackSize() - stack.getCount();
                    if (stack.getMaxStackSize() > 1 && room > 0) {
                        stack.grow(Math.min(room, player.getRandom().nextInt(bonus + 1)));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onEnchantmentLevel(GetEnchantmentLevelEvent event) {
        Integer fortune = FORTUNE_BONUS.get();
        Integer looting = LOOTING_BONUS.get();
        if (fortune == null && looting == null) {
            return;
        }
        ItemEnchantments.Mutable enchantments = event.getEnchantments();
        if (fortune != null) {
            event.getHolder(Enchantments.FORTUNE).ifPresent(holder -> {
                if (event.isTargetting(holder)) {
                    enchantments.set(holder, enchantments.getLevel(holder) + fortune);
                }
            });
        }
        if (looting != null) {
            event.getHolder(Enchantments.LOOTING).ifPresent(holder -> {
                if (event.isTargetting(holder)) {
                    enchantments.set(holder, enchantments.getLevel(holder) + looting);
                }
            });
        }
    }

    // ---- Wisdom: XP orb multiplier ----

    @SubscribeEvent
    public static void onOrbPickup(PlayerXpEvent.PickupXp event) {
        if (event.getEntity() instanceof ServerPlayer) {
            orbPickupInProgress = true;
        }
    }

    @SubscribeEvent
    public static void onXpChange(PlayerXpEvent.XpChange event) {
        boolean fromOrb = orbPickupInProgress;
        orbPickupInProgress = false;
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getAmount() <= 0) {
            return;
        }
        // Prestige multiplier (x2, x4, x8 ...) applies to every XP gain; Wisdom only to orbs.
        double multiplier = StatManager.stats(player).prestigeXpMultiplier();
        if (fromOrb) {
            multiplier *= StatEffects.xpMultiplier(StatManager.effective(player, StatType.WISDOM));
        }
        if (multiplier > 1.0) {
            double scaled = Math.min(1.0E9, event.getAmount() * multiplier);
            int whole = (int) scaled;
            if (player.getRandom().nextDouble() < scaled - whole) {
                whole++;
            }
            event.setAmount(whole);
        }
    }

    // ---- Wisdom: twin babies ----

    @SubscribeEvent
    public static void onBabySpawn(BabyEntitySpawnEvent event) {
        if (!(event.getCausedByPlayer() instanceof ServerPlayer player)) {
            return;
        }
        if (!(event.getParentA().level() instanceof ServerLevel level)) {
            return;
        }
        if (!(event.getParentA() instanceof AgeableMob parentA) || !(event.getParentB() instanceof AgeableMob parentB)) {
            return;
        }
        double chance = StatEffects.twinChance(StatManager.effective(player, StatType.WISDOM));
        if (chance <= 0 || player.getRandom().nextDouble() >= chance) {
            return;
        }
        AgeableMob twin = parentA.getBreedOffspring(level, parentB);
        if (twin != null) {
            twin.setBaby(true);
            twin.moveTo(parentA.getX(), parentA.getY(), parentA.getZ(), 0.0F, 0.0F);
            level.addFreshEntityWithPassengers(twin);
        }
    }

    // ---- Wisdom: furnace output ----
    // Vanilla gives no clean "item finished smelting" hook, and the collect-from-furnace event reports an
    // empty stack on shift-click. So instead we remember who last opened each furnace and watch its output slot.

    @SubscribeEvent
    public static void onFurnaceOpened(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        if (player.isSecondaryUseActive() && !event.getItemStack().isEmpty()) {
            return; // sneaking with an item in hand does not open the furnace
        }
        BlockPos pos = event.getPos();
        if (player.level().getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
            FurnaceTrack track = FURNACES.computeIfAbsent(GlobalPos.of(player.level().dimension(), pos), key -> new FurnaceTrack());
            track.owner = player.getUUID();
            track.lastCount = -1; // re-read the output slot on the next tick
        }
    }

    private static void tickFurnaces(MinecraftServer server) {
        Iterator<Map.Entry<GlobalPos, FurnaceTrack>> iterator = FURNACES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<GlobalPos, FurnaceTrack> entry = iterator.next();
            GlobalPos key = entry.getKey();
            FurnaceTrack track = entry.getValue();

            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (!level.isLoaded(key.pos())) {
                continue;
            }
            if (!(level.getBlockEntity(key.pos()) instanceof AbstractFurnaceBlockEntity furnace)) {
                iterator.remove();
                continue;
            }

            ItemStack output = furnace.getItem(2);
            int now = output.isEmpty() ? 0 : output.getCount();

            ServerPlayer owner = server.getPlayerList().getPlayer(track.owner);
            if (owner != null && track.lastCount >= 0 && now > track.lastCount) {
                int copies = StatEffects.extraItemCopies(StatManager.effective(owner, StatType.WISDOM));
                if (copies > 0) {
                    int extra = (now - track.lastCount) * copies;
                    int room = output.getMaxStackSize() - now;
                    int add = Math.min(extra, room);
                    if (add > 0) {
                        output.grow(add);
                        furnace.setChanged();
                        now += add;
                    }
                    if (extra > add) {
                        Block.popResource(level, key.pos().above(), output.copyWithCount(extra - add));
                    }
                }
            }
            track.lastCount = now;
        }
    }

    // ---- Charisma: villager discount and double trade output ----

    @SubscribeEvent
    public static void onTradeScreenOpened(PlayerContainerEvent.Open event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof MerchantMenu menu)) {
            return;
        }
        double discount = StatEffects.discount(StatManager.effective(player, StatType.CHARISMA));
        if (discount <= 0) {
            return;
        }
        // Only villagers: vanilla resets their special prices when a trade ends, so our discount cannot pile up.
        List<Villager> villagers = player.level().getEntitiesOfClass(Villager.class,
                player.getBoundingBox().inflate(10.0), villager -> villager.getTradingPlayer() == player);
        if (villagers.isEmpty()) {
            return;
        }
        for (MerchantOffer offer : menu.getOffers()) {
            int cut = (int) Math.round(offer.getCostA().getCount() * discount);
            if (cut > 0) {
                offer.addToSpecialPriceDiff(-cut);
            }
        }
    }

    @SubscribeEvent
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int copies = StatEffects.extraItemCopies(StatManager.effective(player, StatType.CHARISMA));
        ItemStack result = event.getMerchantOffer().getResult();
        if (copies > 0 && !result.isEmpty()) {
            give(player, result, result.getCount() * copies);
        }
    }

    private static void give(ServerPlayer player, ItemStack template, int count) {
        while (count > 0) {
            int amount = Math.min(count, template.getMaxStackSize());
            ItemHandlerHelper.giveItemToPlayer(player, template.copyWithCount(amount));
            count -= amount;
        }
    }

    // ---- per-tick upkeep, and a safety net so a temporary flag is never left set ----

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        FORTUNE_BONUS.remove();
        LOOTING_BONUS.remove();
        orbPickupInProgress = false;
        tickFurnaces(event.getServer());
    }
}
