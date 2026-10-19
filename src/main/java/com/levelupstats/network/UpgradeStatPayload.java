package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: "spend levels to raise the stat with this ordinal". */
public record UpgradeStatPayload(int stat) implements CustomPacketPayload {
    public static final Type<UpgradeStatPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "upgrade_stat"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, UpgradeStatPayload> CODEC =
            ByteBufCodecs.VAR_INT.map(UpgradeStatPayload::new, UpgradeStatPayload::stat);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
