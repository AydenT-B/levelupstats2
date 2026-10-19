package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: ore positions (as packed block positions) and the RGB colour each one should glow. */
public record OreGlowPayload(List<Long> positions, List<Integer> colors) implements CustomPacketPayload {
    public static final Type<OreGlowPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "ore_glow"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, OreGlowPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG.apply(ByteBufCodecs.list()), OreGlowPayload::positions,
            ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list()), OreGlowPayload::colors,
            OreGlowPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
