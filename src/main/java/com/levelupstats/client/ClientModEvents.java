package com.levelupstats.client;

import com.levelupstats.LevelUpStats;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Mod-bus client events: registers the keybinds. */
@EventBusSubscriber(modid = LevelUpStats.MODID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {
    private static final String CATEGORY = "key.categories." + LevelUpStats.MODID;

    public static final KeyMapping OPEN_MENU = key("open", GLFW.GLFW_KEY_K);
    /** Places the Mark TP marker (hold sneak while pressing to remove it instead). */
    public static final KeyMapping MARK_SET = key("mark_set", GLFW.GLFW_KEY_V);
    public static final KeyMapping MARK_RECALL = key("mark_recall", GLFW.GLFW_KEY_B);
    public static final KeyMapping STASIS = key("stasis", GLFW.GLFW_KEY_Z);
    public static final KeyMapping DECOY = key("decoy", GLFW.GLFW_KEY_X);

    private static KeyMapping key(String name, int glfwKey) {
        return new KeyMapping("key." + LevelUpStats.MODID + "." + name, KeyConflictContext.IN_GAME,
                InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_MENU);
        event.register(MARK_SET);
        event.register(MARK_RECALL);
        event.register(STASIS);
        event.register(DECOY);
    }
}
