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
 *   <li><b>G</b> – Toggle the Iron/Steel push/pull power on or off (default; remappable
 *       in the Controls menu under the Mistborn category).</li>
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
     * Toggle the Iron/Steel push/pull power (effect) on or off.  Default: G
     * Iron and Steel continue burning regardless; only whether the push/pull effect
     * fires is toggled.  Fully remappable in the Controls menu under the Mistborn category.
     */
    public static final KeyMapping KEY_BURN = new KeyMapping(
            "key.mistborn.burn",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
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
