package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "raise my stat cap" (only works when every stat is at the current cap). */
public record PrestigePayload() implements CustomPacketPayload {
    public static final PrestigePayload INSTANCE = new PrestigePayload();

    public static final Type<PrestigePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "prestige"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, PrestigePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
