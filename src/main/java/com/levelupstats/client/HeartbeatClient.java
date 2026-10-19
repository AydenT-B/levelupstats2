package com.levelupstats.client;

import com.levelupstats.ModAttachments;
import com.levelupstats.PerkRules;
import com.levelupstats.PerkType;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Heartbeat perk, client half. Sneak for 15 seconds and the world goes dark, but every mob within range
 * is outlined through the walls (red = hostile, green = everything else, faint beyond 30 blocks).
 * The drawing itself is in {@link SeeThroughRenderer}.
 */
public final class HeartbeatClient {
    public record Tracked(LivingEntity entity, boolean hostile, boolean near) {
    }

    private static int sneakTicks;
    private static float fade;
    private static float prevFade;
    private static int scanTimer;
    private static List<Tracked> tracked = List.of();

    private HeartbeatClient() {
    }

    public static void tick(Minecraft mc, LocalPlayer player) {
        boolean allowed = mc.level != null
                && player.getData(ModAttachments.STATS.get()).active(PerkType.HEARTBEAT)
                && !player.isSpectator();
        boolean sneaking = allowed && player.isShiftKeyDown();
        sneakTicks = sneaking ? sneakTicks + 1 : 0;
        boolean active = sneaking && sneakTicks >= PerkRules.HEARTBEAT_SNEAK_TICKS;

        prevFade = fade;
        fade = active ? Math.min(1.0F, fade + 0.04F) : Math.max(0.0F, fade - 0.15F);

        if (fade <= 0.0F) {
            tracked = List.of();
            scanTimer = 0;
            return;
        }
        if (scanTimer-- > 0) {
            return;
        }
        scanTimer = 4;
        double far = PerkRules.HEARTBEAT_FAR;
        double near = PerkRules.HEARTBEAT_NEAR;
        List<Tracked> found = new ArrayList<>();
        for (LivingEntity entity : mc.level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(far), e -> e != player && e instanceof Mob && e.isAlive())) {
            double distSq = entity.distanceToSqr(player);
            if (distSq <= far * far) {
                found.add(new Tracked(entity, entity instanceof Enemy, distSq <= near * near));
            }
        }
        tracked = found;
    }

    /** 0 to 1 darkness amount, smoothed between ticks. */
    public static float darkness(float partialTick) {
        return prevFade + (fade - prevFade) * partialTick;
    }

    public static List<Tracked> tracked() {
        return tracked;
    }
}
