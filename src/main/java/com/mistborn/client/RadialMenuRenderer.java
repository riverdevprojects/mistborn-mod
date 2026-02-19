package com.mistborn.client;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.keybind.ModKeybinds;
import com.mistborn.power.AllomanticMetal;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the radial metal-selection menu while {@link ModKeybinds#KEY_RADIAL} is held.
 *
 * <ul>
 *   <li>Only unlocked metals are shown as segments.</li>
 *   <li>Segments with a zero reserve are grayed out and cannot be selected.</li>
 *   <li>The segment nearest the mouse cursor is highlighted.</li>
 *   <li>When the key is released, the hovered segment (if usable) becomes
 *       {@code currentlyBurning} via a client→server packet (handled in
 *       {@link ClientEventHandler}).</li>
 * </ul>
 */
public class RadialMenuRenderer {

    private static final float INNER_RADIUS = 40f;
    private static final float OUTER_RADIUS = 90f;
    private static final int   SEGMENTS_PER_ARC = 24; // tessellation steps per metal segment

    /** Currently hovered metal index within the current metal list; -1 if none. */
    private static int hoveredIndex = -1;

    /** Last computed list of unlocked metals for layout purposes. */
    private static List<AllomanticMetal> currentMetals = new ArrayList<>();

    /**
     * Renders the radial menu overlay.
     * Called from {@link ClientEventHandler} during {@code RenderGuiEvent.Post}
     * while KEY_RADIAL is held.
     */
    public static void render(GuiGraphics gfx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return;

        AllomanticData data = mc.player.getData(ModAttachments.ALLOMANTIC_DATA.get());
        currentMetals = new ArrayList<>(data.getUnlockedMetals());
        if (currentMetals.isEmpty()) return;
        currentMetals.sort(java.util.Comparator.comparingInt(AllomanticMetal::ordinal));

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float centreX = screenW / 2f;
        float centreY = screenH / 2f;

        // Mouse position in GUI coordinates
        double scale   = mc.getWindow().getGuiScale();
        float mouseX   = (float)(mc.mouseHandler.xpos() / scale);
        float mouseY   = (float)(mc.mouseHandler.ypos() / scale);

        // Determine hovered segment by angle
        float dx = mouseX - centreX;
        float dy = mouseY - centreY;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx)); // -180 to 180
        if (angle < 0) angle += 360f;

        int n = currentMetals.size();
        float segAngle = 360f / n;

        hoveredIndex = (int) (angle / segAngle);
        if (hoveredIndex >= n) hoveredIndex = n - 1;

        // If mouse is within inner radius, no hover
        float mouseDistSq = dx * dx + dy * dy;
        if (mouseDistSq < INNER_RADIUS * INNER_RADIUS) hoveredIndex = -1;

        // If hovered metal has no reserve, mark as invalid hover
        if (hoveredIndex >= 0) {
            AllomanticMetal hovered = currentMetals.get(hoveredIndex);
            if (data.getReserve(hovered) <= 0f) {
                hoveredIndex = -1;
            }
        }

        // Draw dark background disc
        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(centreX, centreY, 0);

        Matrix4f mat = pose.last().pose();
        drawDisc(mat, 0, 360, OUTER_RADIUS + 4, 0x88000000);

        // Draw each segment
        for (int i = 0; i < n; i++) {
            AllomanticMetal metal = currentMetals.get(i);
            float startAngle = i * segAngle;
            float endAngle   = startAngle + segAngle;

            float reserve  = data.getReserve(metal);
            boolean empty  = reserve <= 0f;
            boolean hovered = (i == hoveredIndex);

            // Base colour
            int baseCol;
            if (empty) {
                baseCol = 0xAA444444; // gray for empty
            } else if (hovered) {
                baseCol = 0xFF000000 | metal.getColour(); // full bright when hovered
            } else {
                // Dim version
                int r = (int)((metal.getRed()   * 0.55f) * 255);
                int g = (int)((metal.getGreen() * 0.55f) * 255);
                int b = (int)((metal.getBlue()  * 0.55f) * 255);
                baseCol = 0xCC000000 | (r << 16) | (g << 8) | b;
            }

            // Gap between segments (1 degree on each side)
            drawAnnulusSector(mat, startAngle + 1f, endAngle - 1f, INNER_RADIUS, OUTER_RADIUS, baseCol);
        }

        // Draw labels
        for (int i = 0; i < n; i++) {
            AllomanticMetal metal = currentMetals.get(i);
            float midAngle = (float) Math.toRadians((i + 0.5f) * segAngle);
            float labelR   = (INNER_RADIUS + OUTER_RADIUS) / 2f;
            float lx = (float) Math.cos(midAngle) * labelR;
            float ly = (float) Math.sin(midAngle) * labelR;

            float reserve = data.getReserve(metal);
            boolean empty = reserve <= 0f;

            String name = metal.getDisplayName();
            int nameW   = Minecraft.getInstance().font.width(name);
            int textCol = empty ? 0xAAAAAA : (i == hoveredIndex ? 0xFFFFFF : 0xDDDDDD);

            pose.pushPose();
            pose.translate(lx - nameW / 2f, ly - 4, 0);
            Minecraft.getInstance().font.drawInBatch(name, 0, 0, textCol, true,
                    pose.last().pose(), gfx.bufferSource(), net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            pose.popPose();
        }

        pose.popPose();

        // Flush the batched font rendering
        gfx.flush();
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** Returns the currently hovered metal, or null if none / outside ring. */
    public static AllomanticMetal getHoveredMetal() {
        if (hoveredIndex < 0 || hoveredIndex >= currentMetals.size()) return null;
        return currentMetals.get(hoveredIndex);
    }

    public static void resetHover() {
        hoveredIndex = -1;
    }

    /** Draws a filled disc from startDeg to endDeg at the given radius. */
    private static void drawDisc(Matrix4f mat, float startDeg, float endDeg, float radius, int argb) {
        drawAnnulusSector(mat, startDeg, endDeg, 0, radius, argb);
    }

    /** Draws an annulus sector (donut slice) from inner to outer radius. */
    private static void drawAnnulusSector(Matrix4f mat,
                                          float startDeg, float endDeg,
                                          float inner, float outer, int argb) {
        float a = ((argb >> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >>  8) & 0xFF) / 255f;
        float b = ( argb        & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess   = Tesselator.getInstance();
        BufferBuilder buf  = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

        float range = endDeg - startDeg;
        for (int i = 0; i <= SEGMENTS_PER_ARC; i++) {
            float frac  = (float) i / SEGMENTS_PER_ARC;
            float angle = (float) Math.toRadians(startDeg + frac * range);
            float cos   = (float) Math.cos(angle);
            float sin   = (float) Math.sin(angle);

            buf.addVertex(mat, cos * outer, sin * outer, 0).setColor(r, g, b, a);
            buf.addVertex(mat, cos * inner, sin * inner, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private RadialMenuRenderer() {}
}
