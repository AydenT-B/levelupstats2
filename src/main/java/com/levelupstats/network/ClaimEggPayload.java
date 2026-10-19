package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "give me the spawn egg for this entity type" (Egg Conjurer perk). */
public record ClaimEggPayload(ResourceLocation entityType) implements CustomPacketPayload {
    public static final Type<ClaimEggPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "claim_egg"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ClaimEggPayload> CODEC =
            ResourceLocation.STREAM_CODEC.map(ClaimEggPayload::new, ClaimEggPayload::entityType);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
