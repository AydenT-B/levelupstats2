package com.levelupstats;

import java.util.function.Supplier;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, LevelUpStats.MODID);

    public static final Supplier<AttachmentType<PlayerStats>> STATS = ATTACHMENTS.register("stats",
            () -> AttachmentType.builder(() -> new PlayerStats())
                    .serialize(PlayerStats.CODEC)
                    .copyOnDeath()
                    .build());
}
