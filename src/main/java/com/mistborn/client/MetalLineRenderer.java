package com.mistborn.client;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.power.AllomanticMetal;
import com.mistborn.power.IronSteelHandler;
import com.mistborn.power.MetalSource;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Client-side renderer for Allomantic metal-detection lines.
 *
 * <p>When the player has Iron or Steel selected (as {@code currentlyBurning}),
 * thin lines are drawn from the player's eye position to every detected metal
 * source within range.  Iron lines are blue; Steel lines are red.</p>
 *
 * <p>The line toward the source closest to the player's crosshair is drawn
 * thicker and brighter to indicate the current target.</p>
 *
 * <p>Called from {@link ClientEventHandler} during
 * {@link RenderLevelStageEvent.Stage#AFTER_TRANSLUCENT_BLOCKS}.</p>
 */
public class MetalLineRenderer {

    /** Cached list of sources discovered this frame. */
    private static List<MetalSource> cachedSources = new ArrayList<>();

    /** Tick counter to rate-limit source scanning (every 3 client ticks). */
    private static int scanCooldown = 0;

    /**
     * Called each render frame from {@link ClientEventHandler}.
     */
    public static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;
        if (!player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return;

        AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());
        AllomanticMetal burning = data.getCurrentlyBurning();

        boolean showLines = (burning == AllomanticMetal.IRON || burning == AllomanticMetal.STEEL);
        if (!showLines) {
            cachedSources.clear();
            return;
        }

        // Rate-limit world scan
        if (scanCooldown <= 0) {
            cachedSources = IronSteelHandler.findSources(player, player.level());
            scanCooldown = 3;
        } else {
            scanCooldown--;
        }

        if (cachedSources.isEmpty()) return;

        // Find targeted source
        MetalSource target = IronSteelHandler.findTarget(player, cachedSources);

        Camera camera = event.getCamera();
        Vec3 camPos   = camera.getPosition();

        // Colour per metal
        float lineR = burning == AllomanticMetal.IRON ? 0.36f : 0.85f;
        float lineG = burning == AllomanticMetal.IRON ? 0.55f : 0.36f;
        float lineB = burning == AllomanticMetal.IRON ? 0.85f : 0.36f;

        Vec3 eyePos = player.getEyePosition(event.getPartialTick());

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // Translate by negative camera position so world coords work correctly
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);
        Matrix4f mat = poseStack.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.lineWidth(1.5f);

        Tesselator tess  = Tesselator.getInstance();

        // Draw normal lines
        BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);

        for (MetalSource src : cachedSources) {
            if (src == target) continue; // draw target separately

            float alpha = 0.55f;
            buf.addVertex(mat, (float) eyePos.x, (float) eyePos.y, (float) eyePos.z)
               .setColor(lineR, lineG, lineB, alpha);
            buf.addVertex(mat, (float) src.position.x, (float) src.position.y, (float) src.position.z)
               .setColor(lineR, lineG, lineB, alpha);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());

        // Draw targeted line (brighter, separate draw call for width change)
        if (target != null) {
            RenderSystem.lineWidth(3.0f);
            BufferBuilder tbuf = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
            tbuf.addVertex(mat, (float) eyePos.x, (float) eyePos.y, (float) eyePos.z)
                .setColor(lineR, lineG, lineB, 1.0f);
            tbuf.addVertex(mat, (float) target.position.x, (float) target.position.y, (float) target.position.z)
                .setColor(lineR, lineG, lineB, 1.0f);
            BufferUploader.drawWithShader(tbuf.buildOrThrow());
            RenderSystem.lineWidth(1.5f);
        }

        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();

        poseStack.popPose();
    }

    /** Returns the current cached metal sources (for server-side push/pull requests). */
    public static List<MetalSource> getCachedSources() {
        return cachedSources;
    }

    private MetalLineRenderer() {}
}
