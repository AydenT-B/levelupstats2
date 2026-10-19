package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import com.levelupstats.PlayerStats;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the player's full stat data. */
public record SyncStatsPayload(PlayerStats stats) implements CustomPacketPayload {
    public static final Type<SyncStatsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "sync_stats"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, SyncStatsPayload> CODEC = StreamCodec.of(
            (buf, payload) -> payload.stats().write(buf),
            buf -> new SyncStatsPayload(PlayerStats.read(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
