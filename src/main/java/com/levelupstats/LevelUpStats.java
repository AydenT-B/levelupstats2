package com.levelupstats;

import com.levelupstats.network.NetworkHandler;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(LevelUpStats.MODID)
public class LevelUpStats {
    public static final String MODID = "levelupstats";
    public static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();

    public LevelUpStats(IEventBus modEventBus) {
        ModAttachments.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(NetworkHandler::register);
    }
}
