package com.levelupstats.client;

import com.levelupstats.PerkRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Ores currently glowing for the local player. Plain data, so it is safe to load anywhere. */
public final class OreGlowState {
    public record Glow(BlockPos pos, int rgb) {
    }

    private static List<Glow> glows = List.of();
    private static int ticksLeft;

    private OreGlowState() {
    }

    public static void show(List<Long> positions, List<Integer> colors) {
        List<Glow> list = new ArrayList<>();
        for (int i = 0; i < positions.size() && i < colors.size(); i++) {
            list.add(new Glow(BlockPos.of(positions.get(i)), colors.get(i)));
        }
        glows = list;
        ticksLeft = PerkRules.ORE_GLOW_TICKS;
    }

    public static void tick() {
        if (ticksLeft > 0 && --ticksLeft == 0) {
            glows = List.of();
        }
    }

    public static List<Glow> glows() {
        return glows;
    }

    public static int ticksLeft() {
        return ticksLeft;
    }
}
