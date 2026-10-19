package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "set the effect slider of this stat (by ordinal) to this percentage (0-100)". */
public record SetSliderPayload(int stat, int percent) implements CustomPacketPayload {
    public static final Type<SetSliderPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "set_slider"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SetSliderPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, SetSliderPayload::stat,
            ByteBufCodecs.VAR_INT, SetSliderPayload::percent,
            SetSliderPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
