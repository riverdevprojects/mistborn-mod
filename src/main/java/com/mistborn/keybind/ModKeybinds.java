package com.mistborn.keybind;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import org.lwjgl.glfw.GLFW;

/**
 * All keybind definitions for the Mistborn mod.
 * Registered during {@link RegisterKeyMappingsEvent} on the mod event bus.
 */
public class ModKeybinds {

    public static final String CATEGORY = "key.categories.mistborn";

    /**
     * Hold to open the radial metal-selection wheel.  Default: R
     */
    public static final KeyMapping KEY_RADIAL = new KeyMapping(
            "key.mistborn.radial",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_R,
            CATEGORY);

    /**
     * Hold to burn the currently selected metal.  Default: G
     */
    public static final KeyMapping KEY_BURN = new KeyMapping(
            "key.mistborn.burn",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_G,
            CATEGORY);

    /**
     * Hold to activate Steel Push on targeted metal.  Default: V  (rebindable to mouse4)
     */
    public static final KeyMapping KEY_PUSH = new KeyMapping(
            "key.mistborn.push",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_V,
            CATEGORY);

    /**
     * Hold to activate Iron Pull on targeted metal.  Default: C  (rebindable to mouse5)
     */
    public static final KeyMapping KEY_PULL = new KeyMapping(
            "key.mistborn.pull",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_C,
            CATEGORY);

    /**
     * Called from the mod event bus handler to register all keys.
     */
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(KEY_RADIAL);
        event.register(KEY_BURN);
        event.register(KEY_PUSH);
        event.register(KEY_PULL);
    }

    private ModKeybinds() {}
}
