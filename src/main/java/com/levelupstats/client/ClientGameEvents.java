package com.levelupstats.client;

import com.levelupstats.LevelUpStats;
import com.levelupstats.ModAttachments;
import com.levelupstats.PerkType;
import com.levelupstats.network.ActionPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Game-bus client events: menu key, perk keys, double jump, and the per-tick visual state. */
@EventBusSubscriber(modid = LevelUpStats.MODID, value = Dist.CLIENT)
public class ClientGameEvents {
    private static boolean jumpWasDown;
    private static boolean usedDoubleJump;

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null) {
            jumpWasDown = false;
            return;
        }

        while (ClientModEvents.OPEN_MENU.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new StatsScreen());
            }
        }
        while (ClientModEvents.MARK_SET.consumeClick()) {
            send(player.isShiftKeyDown() ? ActionPayload.MARK_CLEAR : ActionPayload.MARK_SET);
        }
        while (ClientModEvents.MARK_RECALL.consumeClick()) {
            send(ActionPayload.MARK_RECALL);
        }
        while (ClientModEvents.STASIS.consumeClick()) {
            send(ActionPayload.STASIS);
        }
        while (ClientModEvents.DECOY.consumeClick()) {
            send(ActionPayload.DECOY);
        }

        OreGlowState.tick();
        HeartbeatClient.tick(mc, player);
        tickDoubleJump(player);
    }

    private static void send(int action) {
        PacketDistributor.sendToServer(ActionPayload.of(action));
    }

    /** Pressing jump again while in the air gives one extra jump, reset when you touch ground, water or a ladder. */
    private static void tickDoubleJump(LocalPlayer player) {
        boolean down = player.input.jumping;
        boolean grounded = player.onGround() || player.isInWater() || player.isInLava()
                || player.onClimbable() || player.getAbilities().flying || player.isPassenger();
        if (grounded) {
            usedDoubleJump = false;
        } else if (down && !jumpWasDown && !usedDoubleJump && !player.isFallFlying()
                && player.getData(ModAttachments.STATS.get()).active(PerkType.DOUBLE_JUMP)) {
            usedDoubleJump = true;
            Vec3 motion = player.getDeltaMovement();
            player.setDeltaMovement(motion.x, player.getAttributeValue(Attributes.JUMP_STRENGTH), motion.z);
            player.fallDistance = 0.0F;
            send(ActionPayload.DOUBLE_JUMP);
        }
        jumpWasDown = down;
    }
}
