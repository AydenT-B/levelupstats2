package com.levelupstats.client;

import com.levelupstats.LevelUpStats;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** Draws the Heartbeat darkness and outlines, and the Ore Glow outlines, on top of the finished world. */
@EventBusSubscriber(modid = LevelUpStats.MODID, value = Dist.CLIENT)
public final class SeeThroughRenderer {
    private SeeThroughRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        float darkness = HeartbeatClient.darkness(partial);
        boolean hasOres = !OreGlowState.glows().isEmpty();
        boolean hasMobs = darkness > 0.2F && !HeartbeatClient.tracked().isEmpty();
        if (darkness <= 0.0F && !hasOres) {
            return;
        }

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = Minecraft.getInstance().renderBuffers().bufferSource();
        Vec3 cam = event.getCamera().getPosition();

        if (darkness > 0.0F) {
            drawDarkness(poseStack, buffers, darkness * 0.94F);
        }
        if (hasOres || hasMobs) {
            VertexConsumer lines = buffers.getBuffer(ModRenderTypes.SEE_THROUGH_LINES);
            poseStack.pushPose();
            poseStack.translate(-cam.x, -cam.y, -cam.z);
            if (hasOres) {
                drawOres(poseStack, lines);
            }
            if (hasMobs) {
                drawMobs(poseStack, lines, partial);
            }
            poseStack.popPose();
            buffers.endBatch(ModRenderTypes.SEE_THROUGH_LINES);
        }
    }

    /** A box around the camera, so whatever way you look you see the fill. */
    private static void drawDarkness(PoseStack poseStack, MultiBufferSource.BufferSource buffers, float alpha) {
        VertexConsumer fill = buffers.getBuffer(ModRenderTypes.DARKNESS);
        Matrix4f m = poseStack.last().pose();
        int a = Mth.clamp((int) (alpha * 255.0F), 0, 255);
        float s = 1.0F;
        float[][] corners = {
                {-s, -s, -s}, {s, -s, -s}, {s, s, -s}, {-s, s, -s},
                {-s, -s, s}, {s, -s, s}, {s, s, s}, {-s, s, s}};
        int[][] faces = {{0, 1, 2, 3}, {4, 5, 6, 7}, {0, 1, 5, 4}, {3, 2, 6, 7}, {0, 3, 7, 4}, {1, 2, 6, 5}};
        for (int[] face : faces) {
            for (int index : face) {
                float[] c = corners[index];
                fill.addVertex(m, c[0], c[1], c[2]).setColor(0, 0, 0, a);
            }
        }
        buffers.endBatch(ModRenderTypes.DARKNESS);
    }

    private static void drawOres(PoseStack poseStack, VertexConsumer lines) {
        int left = OreGlowState.ticksLeft();
        float fadeOut = Math.min(1.0F, left / 20.0F);
        float pulse = 0.75F + 0.25F * Mth.sin(left * 0.3F);
        float alpha = fadeOut * pulse;
        for (OreGlowState.Glow glow : OreGlowState.glows()) {
            float r = ((glow.rgb() >> 16) & 0xFF) / 255.0F;
            float g = ((glow.rgb() >> 8) & 0xFF) / 255.0F;
            float b = (glow.rgb() & 0xFF) / 255.0F;
            LevelRenderer.renderLineBox(poseStack, lines, new AABB(glow.pos()).inflate(0.003), r, g, b, alpha);
        }
    }

    private static void drawMobs(PoseStack poseStack, VertexConsumer lines, float partial) {
        for (HeartbeatClient.Tracked tracked : HeartbeatClient.tracked()) {
            LivingEntity entity = tracked.entity();
            if (!entity.isAlive()) {
                continue;
            }
            Vec3 smooth = new Vec3(Mth.lerp(partial, entity.xo, entity.getX()),
                    Mth.lerp(partial, entity.yo, entity.getY()),
                    Mth.lerp(partial, entity.zo, entity.getZ()));
            AABB box = entity.getBoundingBox().move(smooth.subtract(entity.position()));
            float r = tracked.hostile() ? 1.0F : 0.2F;
            float g = tracked.hostile() ? 0.12F : 1.0F;
            float b = tracked.hostile() ? 0.12F : 0.3F;
            float alpha = tracked.near() ? 1.0F : 0.28F;
            LevelRenderer.renderLineBox(poseStack, lines, box, r, g, b, alpha);
        }
    }
}
