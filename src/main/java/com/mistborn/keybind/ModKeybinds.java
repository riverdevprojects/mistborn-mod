package com.mistborn.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * All keybind definitions for the Mistborn mod.
 * Registered during {@link RegisterKeyMappingsEvent} on the mod event bus.
 *
 * <p>Control scheme:</p>
 * <ul>
 *   <li><b>V (hold)</b> – Open the radial metal-selection wheel.
 *       While held, press 1-8 to immediately pick a slot, or move the mouse and
 *       release V to confirm the hovered segment.</li>
 *   <li><b>F</b> – Toggle burning of the currently selected metal on/off.
 *       Switching metals via V while burning automatically turns the toggle off.</li>
 *   <li>Iron/Steel group: <b>Right-click</b> = Pull, <b>Left-click</b> = Push
 *       (only when F toggle is active).</li>
 * </ul>
 */
public class ModKeybinds {

    public static final String CATEGORY = "key.categories.mistborn";

    /**
     * Hold to open the radial metal-selection wheel.  Default: V
     * While held, 1-8 instantly pick the nth slot.
     */
    public static final KeyMapping KEY_RADIAL = new KeyMapping(
            "key.mistborn.radial",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    /**
     * Toggle burning of the currently selected metal on/off.  Default: F
     * Pressing while a metal is burning stops it; pressing while idle starts it.
     * Switching metals via the radial wheel while burning also turns this off.
     */
    public static final KeyMapping KEY_BURN = new KeyMapping(
            "key.mistborn.burn",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_F,
            CATEGORY);

    /**
     * Called from the mod event bus handler to register all keys.
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(KEY_RADIAL);
        event.register(KEY_BURN);
    }

    private ModKeybinds() {}
}
