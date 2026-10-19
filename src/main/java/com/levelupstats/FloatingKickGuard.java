package com.levelupstats;

import java.lang.reflect.Field;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;

/**
 * Dedicated servers kick players who hover for about four seconds ("Flying is not enabled on this server").
 * Standing on water looks like hovering to that check, so Water Walker resets its counter.
 * The counter is private, so it is reached by reflection; if that ever fails the perk still works and only
 * the kick protection is lost (single player is unaffected either way).
 */
public final class FloatingKickGuard {
    private static Field groundTicks;
    private static boolean failed;

    private FloatingKickGuard() {
    }

    public static void reset(ServerPlayer player) {
        if (failed || player.connection == null) {
            return;
        }
        try {
            if (groundTicks == null) {
                groundTicks = ServerGamePacketListenerImpl.class.getDeclaredField("aboveGroundTickCount");
                groundTicks.setAccessible(true);
            }
            groundTicks.setInt(player.connection, 0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            failed = true;
            LevelUpStats.LOGGER.warn("Water Walker could not reset the floating-kick counter; "
                    + "players may be kicked for flying on dedicated servers without allow-flight.", e);
        }
    }
}
