package com.levelupstats.network;

import com.levelupstats.ModAttachments;
import com.levelupstats.PerkManager;
import com.levelupstats.PerkRules;
import com.levelupstats.PerkType;
import com.levelupstats.PlayerStats;
import com.levelupstats.StatManager;
import com.levelupstats.client.ClientPayloadHandlers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NetworkHandler {
    private NetworkHandler() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");

        registrar.playToServer(UpgradeStatPayload.TYPE, UpgradeStatPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                StatManager.tryUpgrade(player, payload.stat());
            }
        });

        registrar.playToServer(SetSliderPayload.TYPE, SetSliderPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                StatManager.setSlider(player, payload.stat(), payload.percent());
            }
        });

        registrar.playToServer(PrestigePayload.TYPE, PrestigePayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                StatManager.tryPrestige(player);
            }
        });

        registrar.playToServer(ClaimEggPayload.TYPE, ClaimEggPayload.CODEC, (payload, ctx) -> {
            if (ctx.player() instanceof ServerPlayer player) {
                PerkManager.claimEgg(player, payload.entityType());
            }
        });

        registrar.playToServer(ActionPayload.TYPE, ActionPayload.CODEC, (payload, ctx) -> {
            if (!(ctx.player() instanceof ServerPlayer player)) {
                return;
            }
            switch (payload.action()) {
                case ActionPayload.UNLOCK -> StatManager.unlockPerk(player, payload.arg());
                case ActionPayload.TOGGLE -> StatManager.togglePerk(player, payload.arg());
                case ActionPayload.STASIS -> PerkManager.stasis(player);
                case ActionPayload.DECOY -> PerkManager.decoy(player);
                case ActionPayload.MARK_SET -> PerkManager.setMarker(player);
                case ActionPayload.MARK_RECALL -> PerkManager.recall(player, false);
                case ActionPayload.MARK_CLEAR -> PerkManager.clearMarker(player);
                case ActionPayload.REQUEST_SYNC -> StatManager.sync(player);
                case ActionPayload.DOUBLE_JUMP -> {
                    PlayerStats stats = player.getData(ModAttachments.STATS.get());
                    if (stats.active(PerkType.DOUBLE_JUMP)) {
                        player.fallDistance = 0.0F;
                    }
                }
                default -> {
                }
            }
        });

        registrar.playToClient(SyncStatsPayload.TYPE, SyncStatsPayload.CODEC, (payload, ctx) -> {
            // On the client, ctx.player() is the local player.
            ctx.player().setData(ModAttachments.STATS.get(), payload.stats());
        });

        registrar.playToClient(OreGlowPayload.TYPE, OreGlowPayload.CODEC, ClientPayloadHandlers::handleOreGlow);
    }
}
