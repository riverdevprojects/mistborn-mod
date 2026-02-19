package com.mistborn.client;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.power.AllomanticMetal;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the Allomantic HUD overlay in the bottom-left corner of the screen.
 *
 * <p>Displays a grid of all unlocked metals with:
 * <ul>
 *   <li>The metal's abbreviated name</li>
 *   <li>A vertical fill bar showing reserve level (0–100)</li>
 *   <li>Grayed-out appearance when reserve is 0</li>
 *   <li>A glowing highlight border when that metal is currently burning</li>
 * </ul>
 *
 * <p>When Tin is burning, a sidebar on the right lists recent nearby sounds.</p>
 *
 * <p>Only rendered if the player has at least one metal unlocked.</p>
 */
public class HudRenderer {

    // Layout constants
    private static final int CELL_WIDTH    = 22;
    private static final int CELL_HEIGHT   = 52;
    private static final int BAR_WIDTH     = 10;
    private static final int BAR_HEIGHT    = 36;
    private static final int CELL_PADDING  = 2;
    private static final int MARGIN        = 5;
    private static final int LABEL_HEIGHT  = 10;

    // Colours (ARGB)
    private static final int COL_BACKGROUND  = 0xAA000000;
    private static final int COL_BAR_BG      = 0xFF222222;
    private static final int COL_EMPTY       = 0xFF555555;
    private static final int COL_BORDER_BURN = 0xFFFFD700; // gold border when burning
    private static final int COL_TEXT_NORMAL = 0xFFCCCCCC;
    private static final int COL_TEXT_DIM    = 0xFF666666;

    /**
     * Called each frame from {@link ClientEventHandler} during {@code RenderGuiEvent.Post}.
     */
    public static void render(GuiGraphics gfx, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return;

        AllomanticData data = mc.player.getData(ModAttachments.ALLOMANTIC_DATA.get());

        List<AllomanticMetal> unlocked = new ArrayList<>(data.getUnlockedMetals());
        if (unlocked.isEmpty()) return;

        // Sort by ordinal for consistent order
        unlocked.sort(java.util.Comparator.comparingInt(AllomanticMetal::ordinal));

        int screenH = mc.getWindow().getGuiScaledHeight();
        int totalW  = unlocked.size() * (CELL_WIDTH + CELL_PADDING) - CELL_PADDING;
        int startX  = MARGIN;
        int startY  = screenH - MARGIN - CELL_HEIGHT;

        // Draw overall background
        gfx.fill(startX - 2, startY - 2,
                 startX + totalW + 2, startY + CELL_HEIGHT + 2,
                 COL_BACKGROUND);

        AllomanticMetal burning = data.getCurrentlyBurning();

        for (int i = 0; i < unlocked.size(); i++) {
            AllomanticMetal metal = unlocked.get(i);
            int cellX = startX + i * (CELL_WIDTH + CELL_PADDING);

            float reserve  = data.getReserve(metal);
            boolean empty  = reserve <= 0f;
            boolean active = metal == burning;

            // Border highlight if currently burning
            if (active) {
                gfx.fill(cellX - 1, startY - 1,
                         cellX + CELL_WIDTH + 1, startY + CELL_HEIGHT + 1,
                         COL_BORDER_BURN);
            }

            // Cell background
            gfx.fill(cellX, startY, cellX + CELL_WIDTH, startY + CELL_HEIGHT, COL_BAR_BG);

            // Metal name label (first 2 chars, uppercase)
            String label = metal.name().substring(0, Math.min(2, metal.name().length()));
            int textCol = empty ? COL_TEXT_DIM : COL_TEXT_NORMAL;
            int labelX  = cellX + CELL_WIDTH / 2 - mc.font.width(label) / 2;
            gfx.drawString(mc.font, label, labelX, startY + 1, textCol, false);

            // Vertical fill bar
            int barX  = cellX + (CELL_WIDTH - BAR_WIDTH) / 2;
            int barY  = startY + LABEL_HEIGHT + 2;

            // Bar background
            gfx.fill(barX, barY, barX + BAR_WIDTH, barY + BAR_HEIGHT, 0xFF111111);

            // Fill portion – from the bottom upward
            if (!empty) {
                float pct     = reserve / 100f;
                int fillH     = Math.round(BAR_HEIGHT * pct);
                int fillStart = barY + BAR_HEIGHT - fillH;
                int barColour = 0xFF000000 | metal.getColour();
                gfx.fill(barX, fillStart, barX + BAR_WIDTH, barY + BAR_HEIGHT, barColour);
            }

            // Reserve percentage text at bottom of cell
            String pctLabel = String.valueOf((int) reserve);
            int pctX = cellX + CELL_WIDTH / 2 - mc.font.width(pctLabel) / 2;
            gfx.drawString(mc.font, pctLabel, pctX, startY + CELL_HEIGHT - LABEL_HEIGHT, textCol, false);
        }

        // ── Tin sound sidebar ──────────────────────────────────────────────────
        if (burning == AllomanticMetal.TIN) {
            renderTinSidebar(gfx, mc);
        }
    }

    private static void renderTinSidebar(GuiGraphics gfx, Minecraft mc) {
        List<String> lines = TinSoundTracker.getDisplayLines();
        if (lines.isEmpty()) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int lineH   = 10;
        int padding = 4;
        int boxW    = 160;
        int boxH    = lines.size() * lineH + padding * 2;
        int boxX    = screenW - boxW - MARGIN;
        int boxY    = MARGIN + 10; // below hotbar area

        gfx.fill(boxX - 2, boxY - 2, boxX + boxW + 2, boxY + boxH + 2, 0xAA000000);
        gfx.drawString(mc.font, "Tin – Nearby Sounds:", boxX, boxY - lineH, 0xFFBDBDBD, false);

        for (int i = 0; i < lines.size(); i++) {
            gfx.drawString(mc.font, lines.get(i), boxX + padding, boxY + padding + i * lineH, 0xFFCCCCCC, false);
        }
    }

    private HudRenderer() {}
}
