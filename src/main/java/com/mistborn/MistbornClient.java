package com.mistborn;

import com.mistborn.client.ClientEventHandler;
import com.mistborn.keybind.ModKeybinds;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Client-only initialisation for the Mistborn mod.
 *
 * <p>Only instantiated on the physical client side.  Registers keybinds
 * and client-side event listeners (HUD, radial menu, line renderer, etc.).</p>
 */
@Mod(value = MistbornMod.MODID, dist = Dist.CLIENT)
public class MistbornClient {

    public MistbornClient(IEventBus modEventBus, ModContainer modContainer) {
        // Keybind registration (mod bus)
        modEventBus.addListener(this::onRegisterKeyMappings);

        // Client-side game event listeners
        NeoForge.EVENT_BUS.register(new ClientEventHandler());
    }

    private void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ModKeybinds.register(event);
    }
}
