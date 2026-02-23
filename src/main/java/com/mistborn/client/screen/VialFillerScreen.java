package com.mistborn.client.screen;

import com.mistborn.block.menu.VialFillerMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import static com.mistborn.MistbornMod.MODID;

/**
 * Client-side screen for the Vial Filler block.
 *
 * <p>Renders a 176×166 pixel GUI background with:</p>
 * <ul>
 *   <li>A vial slot at the top-centre.</li>
 *   <li>A fuel slot (bottom-left) with a flame indicator.</li>
 *   <li>An ingredient slot (bottom-right) with a brew-arrow progress indicator.</li>
 *   <li>Standard player inventory grid below.</li>
 * </ul>
 */
public class VialFillerScreen extends AbstractContainerScreen<VialFillerMenu> {

    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(MODID, "textures/gui/vial_filler.png");

    // GUI background dimensions
    private static final int GUI_WIDTH  = 176;
    private static final int GUI_HEIGHT = 166;

    // Flame indicator (fuel) position relative to GUI top-left
    private static final int FLAME_X        = 56;
    private static final int FLAME_Y        = 36;
    private static final int FLAME_WIDTH    = 14;
    private static final int FLAME_HEIGHT   = 14;
    // UV in the texture for the flame
    private static final int FLAME_U        = 176;
    private static final int FLAME_V        = 0;
    private static final int FLAME_FULL_V   = 14; // the "full flame" indicator offset

    // Arrow indicator (brew progress) position relative to GUI top-left
    private static final int ARROW_X        = 79;
    private static final int ARROW_Y        = 34;
    private static final int ARROW_WIDTH    = 22;
    private static final int ARROW_HEIGHT   = 16;
    private static final int ARROW_U        = 176;
    private static final int ARROW_V        = 29;

    public VialFillerScreen(VialFillerMenu menu, Inventory playerInv, Component title) {
        super(menu, playerInv, title);
        this.imageWidth  = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    @Override
    protected void renderBg(GuiGraphics gfx, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        // Draw the main GUI background
        gfx.blit(TEXTURE, x, y, 0, 0, GUI_WIDTH, GUI_HEIGHT);

        // ── Fuel flame indicator ──────────────────────────────────────────────
        // The flame fills from bottom-up when fuel is present.
        if (menu.isFueled()) {
            float fuelFrac = menu.getFuelProgress();
            int flameH = Math.max(1, (int) (FLAME_HEIGHT * fuelFrac));
            int flameYOffset = FLAME_HEIGHT - flameH;
            gfx.blit(TEXTURE,
                    x + FLAME_X, y + FLAME_Y + flameYOffset,
                    FLAME_U, FLAME_V + flameYOffset,
                    FLAME_WIDTH, flameH);
        }

        // ── Brew progress arrow ───────────────────────────────────────────────
        float brewFrac = menu.getBrewProgress();
        int arrowW = (int) (ARROW_WIDTH * brewFrac);
        if (arrowW > 0) {
            gfx.blit(TEXTURE,
                    x + ARROW_X, y + ARROW_Y,
                    ARROW_U, ARROW_V,
                    arrowW, ARROW_HEIGHT);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        super.render(gfx, mouseX, mouseY, partialTick);
        renderTooltip(gfx, mouseX, mouseY);
    }
}
