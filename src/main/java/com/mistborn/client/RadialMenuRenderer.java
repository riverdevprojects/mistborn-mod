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
 * Renders the radial metal-selection menu while {@link ModKeybinds#KEY_RADIAL} (V) is held.
 *
 * <h2>Iron / Steel grouping</h2>
 * If the player has Iron and/or Steel unlocked, they appear as a <em>single</em> combined
 * segment labelled "Iron/Steel".  Selecting it sets the selected metal to
 * {@link AllomanticMetal#IRON} (the representative for the group).  The server then
 * allows both push (left-click) and pull (right-click) whenever that group is active.
 *
 * <h2>Slot selection</h2>
 * <ul>
 *   <li>Mouse hover – the segment nearest the cursor is highlighted.</li>
 *   <li>Number keys 1-8 while V is held – call {@link #forceHoverIndex(int)} from
 *       {@link ClientEventHandler}; the slot is immediately committed without
 *       waiting for V to be released.</li>
 *   <li>Release V – commits the currently hovered slot (mouse or forced).</li>
 * </ul>
 */
public class RadialMenuRenderer {

    private static final float INNER_RADIUS    = 40f;
    private static final float OUTER_RADIUS    = 90f;
    private static final int   SEGMENTS_PER_ARC = 24;

    /** Ordinal 0 = IRON; represents the combined Iron/Steel slot. */
    private static final AllomanticMetal IRON_STEEL_REPRESENTATIVE = AllomanticMetal.IRON;

    // ── Slot model ────────────────────────────────────────────────────────────

    /**
     * A single segment on the radial wheel.
     *
     * @param displayName    Text shown in the segment centre.
     * @param argbColor      Packed ARGB color for the segment.
     * @param representative The metal sent to the server when this slot is selected (toggled).
     * @param available      Whether this slot can be selected (has reserve).
     * @param isSet          Whether this metal is currently queued (set via a previous radial pick).
     */
    private record RadialSlot(String displayName, int argbColor,
                               AllomanticMetal representative, boolean available,
                               boolean isSet) {}

    /** Computed slot list rebuilt each render frame. */
    private static List<RadialSlot> currentSlots = new ArrayList<>();

    /** Index into {@link #currentSlots} for the highlighted segment; -1 = none. */
    private static int hoveredIndex = -1;

    /**
     * Hover index pinned by a number-key press; takes priority over mouse hover
     * each render frame until {@link #resetHover()} is called.  -1 = no pin.
     */
    private static int forcedHoverIndex = -1;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Force-hover a specific slot index (0-based) without needing the mouse to
     * be over it.  Called from {@link ClientEventHandler} when a number key is pressed.
     * The caller is responsible for immediately committing the selection.
     *
     * @param index 0-based slot index; clamped to the valid range, -1 to clear.
     */
    public static void forceHoverIndex(int index) {
        if (index < 0) {
            forcedHoverIndex = -1;
            hoveredIndex     = -1;
        } else {
            int clamped = currentSlots.isEmpty() ? 0 : Math.min(index, currentSlots.size() - 1);
            forcedHoverIndex = clamped;
            hoveredIndex     = clamped;
        }
    }

    /**
     * Returns the metal that the currently hovered slot represents, or {@code null}
     * if nothing is hovered or the hovered slot is unavailable.
     */
    public static AllomanticMetal getHoveredMetal() {
        if (hoveredIndex < 0 || hoveredIndex >= currentSlots.size()) return null;
        RadialSlot slot = currentSlots.get(hoveredIndex);
        return slot.available() ? slot.representative() : null;
    }

    /** Returns the number of visible slots (used for number-key range checking). */
    public static int getSlotCount() {
        return currentSlots.size();
    }

    /** Clear the hover state (called after V is released). */
    public static void resetHover() {
        hoveredIndex      = -1;
        forcedHoverIndex  = -1;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

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
        currentSlots = buildSlots(data);
        if (currentSlots.isEmpty()) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        float centreX = screenW / 2f;
        float centreY = screenH / 2f;

        // Mouse position in GUI coordinates
        double scale  = mc.getWindow().getGuiScale();
        float mouseX  = (float)(mc.mouseHandler.xpos() / scale);
        float mouseY  = (float)(mc.mouseHandler.ypos() / scale);

        // Update mouse-based hover (only if hover hasn't been forced by a number key)
        int n = currentSlots.size();
        float segAngle = 360f / n;

        float dx = mouseX - centreX;
        float dy = mouseY - centreY;
        float angle = (float) Math.toDegrees(Math.atan2(dy, dx));
        if (angle < 0) angle += 360f;

        int mouseHover = (int) (angle / segAngle);
        if (mouseHover >= n) mouseHover = n - 1;

        float mouseDistSq = dx * dx + dy * dy;
        if (mouseDistSq < INNER_RADIUS * INNER_RADIUS) mouseHover = -1;

        // Forced index (from number keys) takes priority over mouse hover;
        // mouse hover is used only when no slot has been pinned this cycle.
        if (forcedHoverIndex >= 0) {
            hoveredIndex = forcedHoverIndex;
        } else {
            hoveredIndex = mouseHover;
        }

        // Validate hover: mark unavailable slots as not hoverable
        if (hoveredIndex >= 0
                && hoveredIndex < currentSlots.size()
                && !currentSlots.get(hoveredIndex).available()) {
            hoveredIndex = -1;
        }

        // Draw dark background disc
        PoseStack pose = gfx.pose();
        pose.pushPose();
        pose.translate(centreX, centreY, 0);

        Matrix4f mat = pose.last().pose();
        drawDisc(mat, 0, 360, OUTER_RADIUS + 4, 0x88000000);

        // Draw each segment
        for (int i = 0; i < n; i++) {
            RadialSlot slot = currentSlots.get(i);
            float startAngle = i * segAngle;
            float endAngle   = startAngle + segAngle;

            boolean hovered = (i == hoveredIndex);
            int baseCol;
            if (!slot.available()) {
                baseCol = 0xAA444444;
            } else if (hovered) {
                // Full brightness when the cursor is over this slot
                baseCol = 0xFF000000 | (slot.argbColor() & 0x00FFFFFF);
            } else if (slot.isSet()) {
                // Already queued: show at 75% so it stands out from unset slots
                int r = (int)(((slot.argbColor() >> 16) & 0xFF) * 0.75f);
                int g = (int)(((slot.argbColor() >>  8) & 0xFF) * 0.75f);
                int b = (int)(( slot.argbColor()        & 0xFF) * 0.75f);
                baseCol = 0xFF000000 | (r << 16) | (g << 8) | b;
            } else {
                // Unselected, not hovered: dim
                int r = (int)(((slot.argbColor() >> 16) & 0xFF) * 0.55f);
                int g = (int)(((slot.argbColor() >>  8) & 0xFF) * 0.55f);
                int b = (int)(( slot.argbColor()        & 0xFF) * 0.55f);
                baseCol = 0xCC000000 | (r << 16) | (g << 8) | b;
            }

            drawAnnulusSector(mat, startAngle + 1f, endAngle - 1f,
                              INNER_RADIUS, OUTER_RADIUS, baseCol);
        }

        // Draw slot number hint (1, 2, 3...) at the outer edge of each segment
        for (int i = 0; i < n; i++) {
            float midAngle = (float) Math.toRadians((i + 0.5f) * segAngle);
            float numR     = OUTER_RADIUS + 10f;
            float nx = (float) Math.cos(midAngle) * numR;
            float ny = (float) Math.sin(midAngle) * numR;
            String numLabel = String.valueOf(i + 1);
            int numW = mc.font.width(numLabel);
            pose.pushPose();
            pose.translate(nx - numW / 2f, ny - 4, 0);
            mc.font.drawInBatch(numLabel, 0, 0, 0xFFAAAAAA, true,
                    pose.last().pose(), gfx.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            pose.popPose();
        }

        // Draw labels
        for (int i = 0; i < n; i++) {
            RadialSlot slot = currentSlots.get(i);
            float midAngle = (float) Math.toRadians((i + 0.5f) * segAngle);
            float labelR   = (INNER_RADIUS + OUTER_RADIUS) / 2f;
            float lx = (float) Math.cos(midAngle) * labelR;
            float ly = (float) Math.sin(midAngle) * labelR;

            boolean hovered = (i == hoveredIndex);
            int textCol = !slot.available() ? 0xAAAAAA
                    : (hovered ? 0xFFFFFF : (slot.isSet() ? 0xFFFFDD : 0xDDDDDD));
            String name = slot.displayName();
            int nameW   = mc.font.width(name);
            pose.pushPose();
            pose.translate(lx - nameW / 2f, ly - 4, 0);
            mc.font.drawInBatch(name, 0, 0, textCol, true,
                    pose.last().pose(), gfx.bufferSource(),
                    net.minecraft.client.gui.Font.DisplayMode.NORMAL, 0, 15728880);
            pose.popPose();
        }

        pose.popPose();
        gfx.flush();
    }

    // ── Slot building ─────────────────────────────────────────────────────────

    /**
     * Builds the ordered list of radial slots from the player's unlocked metals.
     * Iron and Steel are collapsed into one "Iron/Steel" slot when either is unlocked.
     */
    private static List<RadialSlot> buildSlots(AllomanticData data) {
        List<AllomanticMetal> unlocked = new ArrayList<>(data.getUnlockedMetals());
        if (unlocked.isEmpty()) return List.of();
        unlocked.sort(java.util.Comparator.comparingInt(AllomanticMetal::ordinal));

        List<RadialSlot> slots = new ArrayList<>();
        boolean ironSteelAdded = false;

        for (AllomanticMetal metal : unlocked) {
            if (metal == AllomanticMetal.IRON || metal == AllomanticMetal.STEEL) {
                if (!ironSteelAdded) {
                    // Combined Iron/Steel slot
                    float ironReserve  = data.getReserve(AllomanticMetal.IRON);
                    float steelReserve = data.getReserve(AllomanticMetal.STEEL);
                    float groupReserve = Math.max(ironReserve, steelReserve);
                    boolean groupAvail = groupReserve > 0f
                            && (data.isUnlocked(AllomanticMetal.IRON) || data.isUnlocked(AllomanticMetal.STEEL));
                    boolean groupSet   = data.isMetalSet(AllomanticMetal.IRON)
                            || data.isMetalSet(AllomanticMetal.STEEL);

                    // Blended purple-ish colour (mix of iron-blue and steel-red)
                    int ironCol  = AllomanticMetal.IRON.getColour();
                    int steelCol = AllomanticMetal.STEEL.getColour();
                    int blendR   = (((ironCol >> 16) & 0xFF) + ((steelCol >> 16) & 0xFF)) / 2;
                    int blendG   = (((ironCol >>  8) & 0xFF) + ((steelCol >>  8) & 0xFF)) / 2;
                    int blendB   = (( ironCol        & 0xFF) + ( steelCol        & 0xFF)) / 2;
                    int blendCol = 0xFF000000 | (blendR << 16) | (blendG << 8) | blendB;

                    slots.add(new RadialSlot("Iron/Steel", blendCol,
                                             IRON_STEEL_REPRESENTATIVE, groupAvail, groupSet));
                    ironSteelAdded = true;
                }
                // Skip STEEL separately since both are in one slot
            } else {
                float reserve = data.getReserve(metal);
                int color     = 0xFF000000 | metal.getColour();
                boolean isSet = data.isMetalSet(metal);
                slots.add(new RadialSlot(metal.getDisplayName(), color, metal, reserve > 0f, isSet));
            }
        }

        return slots;
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    private static void drawDisc(Matrix4f mat, float startDeg, float endDeg, float radius, int argb) {
        drawAnnulusSector(mat, startDeg, endDeg, 0, radius, argb);
    }

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

        Tesselator tess  = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.TRIANGLE_STRIP, DefaultVertexFormat.POSITION_COLOR);

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
