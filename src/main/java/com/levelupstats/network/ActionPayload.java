package com.levelupstats.network;

import com.levelupstats.LevelUpStats;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client -> server: one small request. {@code action} is one of the constants below; {@code arg} is its parameter. */
public record ActionPayload(int action, int arg) implements CustomPacketPayload {
    /** arg = perk ordinal */
    public static final int UNLOCK = 0;
    /** arg = perk ordinal */
    public static final int TOGGLE = 1;
    public static final int STASIS = 2;
    public static final int DECOY = 3;
    public static final int MARK_SET = 4;
    public static final int MARK_RECALL = 5;
    public static final int MARK_CLEAR = 6;
    /** Sent when the player double jumps so the server forgets their fall distance. */
    public static final int DOUBLE_JUMP = 7;
    /** Asks the server to send fresh stats (used when the perk menu opens). */
    public static final int REQUEST_SYNC = 8;

    public static final Type<ActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(LevelUpStats.MODID, "action"));

    public static final StreamCodec<io.netty.buffer.ByteBuf, ActionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ActionPayload::action,
            ByteBufCodecs.VAR_INT, ActionPayload::arg,
            ActionPayload::new);

    public static ActionPayload of(int action) {
        return new ActionPayload(action, 0);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
