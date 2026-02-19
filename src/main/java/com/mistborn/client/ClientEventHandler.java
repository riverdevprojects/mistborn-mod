package com.mistborn.client;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.keybind.ModKeybinds;
import com.mistborn.network.ClientActionPacket;
import com.mistborn.power.AllomanticMetal;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.sound.PlaySoundEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import static com.mistborn.MistbornMod.MODID;

/**
 * Handles all client-side NeoForge events for the Mistborn mod.
 *
 * <p>Registered on the NeoForge event bus (not the mod bus) in
 * {@link com.mistborn.MistbornClient} for client-only events.</p>
 */
public class ClientEventHandler {

    // ── State ─────────────────────────────────────────────────────────────────

    /** True while the radial menu key was held last frame (for release detection). */
    private static boolean wasRadialHeld = false;

    /** True while the burn key was held last frame. */
    private static boolean wasBurnHeld = false;

    /** True while the push key was held last frame. */
    private static boolean wasPushHeld = false;

    /** True while the pull key was held last frame. */
    private static boolean wasPullHeld = false;

    /** Tick counter for rate-limiting push/pull packet sends. */
    private static int pushPullCooldown = 0;

    // ── GUI rendering ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.renderDebug) return; // hide during F3 debug

        // HUD overlay
        HudRenderer.render(event.getGuiGraphics(), event.getPartialTick());

        // Radial menu (only when key is held)
        if (ModKeybinds.KEY_RADIAL.isDown()) {
            RadialMenuRenderer.render(event.getGuiGraphics(), event.getPartialTick());
        }
    }

    // ── 3D world rendering ────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;
        MetalLineRenderer.render(event);
    }

    // ── Client tick (input polling + Tin tracker) ─────────────────────────────

    @SubscribeEvent
    public void onClientTick(LevelTickEvent.Pre event) {
        // Only run for client-side levels
        if (!event.getLevel().isClientSide()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // Prune expired Tin sound entries
        TinSoundTracker.tick();

        // Decrement push/pull cooldown
        if (pushPullCooldown > 0) pushPullCooldown--;

        handleRadialKey(mc);
        handleBurnKey(mc);
        handlePushKey(mc);
        handlePullKey(mc);
    }

    // ── Keybind handlers ──────────────────────────────────────────────────────

    private static void handleRadialKey(Minecraft mc) {
        boolean held = ModKeybinds.KEY_RADIAL.isDown();

        if (!held && wasRadialHeld) {
            // Key released – commit the hovered metal selection
            AllomanticMetal hovered = RadialMenuRenderer.getHoveredMetal();
            if (hovered != null) {
                PacketDistributor.sendToServer(ClientActionPacket.selectMetal(hovered));
            } else {
                // Auto-select if only one metal unlocked
                if (mc.player != null && mc.player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) {
                    AllomanticData data = mc.player.getData(ModAttachments.ALLOMANTIC_DATA.get());
                    var unlocked = data.getUnlockedMetals();
                    if (unlocked.size() == 1) {
                        AllomanticMetal sole = unlocked.iterator().next();
                        if (data.getReserve(sole) > 0f) {
                            PacketDistributor.sendToServer(ClientActionPacket.selectMetal(sole));
                        }
                    }
                }
            }
            RadialMenuRenderer.resetHover();
        }

        wasRadialHeld = held;
    }

    private static void handleBurnKey(Minecraft mc) {
        boolean held = ModKeybinds.KEY_BURN.isDown();

        if (!held && wasBurnHeld) {
            // Key released – stop burning
            PacketDistributor.sendToServer(ClientActionPacket.stopBurn());
        }

        wasBurnHeld = held;
    }

    private static void handlePushKey(Minecraft mc) {
        boolean held = ModKeybinds.KEY_PUSH.isDown();

        if (held && pushPullCooldown <= 0) {
            // Rate-limit to every 2 ticks while held
            PacketDistributor.sendToServer(ClientActionPacket.requestPush());
            pushPullCooldown = 2;
        }

        wasPushHeld = held;
    }

    private static void handlePullKey(Minecraft mc) {
        boolean held = ModKeybinds.KEY_PULL.isDown();

        if (held && pushPullCooldown <= 0) {
            PacketDistributor.sendToServer(ClientActionPacket.requestPull());
            pushPullCooldown = 2;
        }

        wasPullHeld = held;
    }

    // ── Sound interception for Tin ────────────────────────────────────────────

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return;

        AllomanticData data = mc.player.getData(ModAttachments.ALLOMANTIC_DATA.get());
        if (data.getCurrentlyBurning() != AllomanticMetal.TIN) return;

        var sound = event.getSound();
        if (sound == null) return;

        net.minecraft.world.phys.Vec3 soundPos = new net.minecraft.world.phys.Vec3(
                sound.getX(), sound.getY(), sound.getZ());

        String soundName = sound.getLocation().toString();
        TinSoundTracker.onSoundPlayed(soundName, soundPos);
    }
}
