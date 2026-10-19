package com.levelupstats.client;

import com.levelupstats.network.OreGlowPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-side packet handlers. Deliberately free of Minecraft client classes so a server can load it safely. */
public final class ClientPayloadHandlers {
    private ClientPayloadHandlers() {
    }

    public static void handleOreGlow(OreGlowPayload payload, IPayloadContext context) {
        OreGlowState.show(payload.positions(), payload.colors());
    }
}
