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
import org.lwjgl.glfw.GLFW;

/**
 * Handles all client-side NeoForge events for the Mistborn mod.
 *
 * <p>Registered on the NeoForge event bus (not the mod bus) in
 * {@link com.mistborn.MistbornClient} for client-only events.</p>
 *
 * <h2>Control scheme</h2>
 * <ul>
 *   <li><b>V (hold)</b> – Show radial wheel; <b>1-8</b> while held instantly select a slot
 *       and commit it immediately; releasing V commits the mouse-hovered slot.</li>
 *   <li><b>F (press)</b> – Toggle burning on/off for the currently selected metal.
 *       Switching metals via V while burning turns F off automatically (server-side).</li>
 *   <li><b>Left-click</b> – Steel Push: only fires when the Iron/Steel group is selected
 *       and the F-toggle is active; otherwise normal Minecraft attack.</li>
 *   <li><b>Right-click</b> – Iron Pull: same guard; otherwise normal Minecraft use.</li>
 * </ul>
 */
public class ClientEventHandler {

    // ── State ─────────────────────────────────────────────────────────────────

    /** True while the radial-menu key (V) was held last frame. */
    private static boolean wasRadialHeld = false;

    /**
     * Set to true when a number key commits a slot during the current V-hold.
     * Prevents V-release from double-committing.
     */
    private static boolean numberKeySelectedThisCycle = false;

    /** Per-number-key previous held state (index 0 = key 1 … index 7 = key 8). */
    private static final boolean[] wasNumKeyHeld = new boolean[8];

    /** GLFW key codes for number row keys 1-8. */
    private static final int[] NUM_KEY_CODES = {
            GLFW.GLFW_KEY_1, GLFW.GLFW_KEY_2, GLFW.GLFW_KEY_3, GLFW.GLFW_KEY_4,
            GLFW.GLFW_KEY_5, GLFW.GLFW_KEY_6, GLFW.GLFW_KEY_7, GLFW.GLFW_KEY_8
    };

    /** True while the burn-toggle key (F) was held last frame. */
    private static boolean wasBurnHeld = false;

    /** Tick counter for rate-limiting push/pull mouse-click packet sends. */
    private static int pushPullCooldown = 0;

    // ── GUI rendering ─────────────────────────────────────────────────────────

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        HudRenderer.render(event.getGuiGraphics());

        if (ModKeybinds.KEY_RADIAL.isDown()) {
            RadialMenuRenderer.render(event.getGuiGraphics());
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
        if (!event.getLevel().isClientSide()) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        TinSoundTracker.tick();

        if (pushPullCooldown > 0) pushPullCooldown--;

        handleRadialKey(mc);
        handleBurnKey(mc);
        handleMouseButtons(mc);
    }

    // ── Keybind handlers ──────────────────────────────────────────────────────

    /**
     * Handles the radial-wheel key (V hold).
     *
     * <ul>
     *   <li>While V is held: polls number keys 1-8; a rising edge immediately
     *       force-hovers and commits that slot index.</li>
     *   <li>When V is released: if no number key was used this cycle, commits
     *       the mouse-hovered slot (or auto-selects if there's only one slot).</li>
     * </ul>
     */
    private static void handleRadialKey(Minecraft mc) {
        boolean held = ModKeybinds.KEY_RADIAL.isDown();
        long    win  = mc.getWindow().getWindow();

        if (held) {
            // Poll number keys for instant slot selection
            for (int i = 0; i < NUM_KEY_CODES.length; i++) {
                boolean numHeld = GLFW.glfwGetKey(win, NUM_KEY_CODES[i]) == GLFW.GLFW_PRESS;
                if (numHeld && !wasNumKeyHeld[i]) {
                    // Rising edge – pin this slot in the renderer and commit immediately
                    RadialMenuRenderer.forceHoverIndex(i);
                    AllomanticMetal selected = RadialMenuRenderer.getHoveredMetal();
                    if (selected != null) {
                        PacketDistributor.sendToServer(ClientActionPacket.selectMetal(selected));
                        numberKeySelectedThisCycle = true;
                    }
                }
                wasNumKeyHeld[i] = numHeld;
            }
        } else {
            // V released
            if (wasRadialHeld) {
                if (!numberKeySelectedThisCycle) {
                    // Commit mouse-hovered slot
                    AllomanticMetal hovered = RadialMenuRenderer.getHoveredMetal();
                    if (hovered != null) {
                        PacketDistributor.sendToServer(ClientActionPacket.selectMetal(hovered));
                    } else if (RadialMenuRenderer.getSlotCount() == 1) {
                        // Auto-select the sole slot even if mouse was in dead-zone
                        RadialMenuRenderer.forceHoverIndex(0);
                        AllomanticMetal sole = RadialMenuRenderer.getHoveredMetal();
                        if (sole != null) {
                            PacketDistributor.sendToServer(ClientActionPacket.selectMetal(sole));
                        }
                    }
                }
                numberKeySelectedThisCycle = false;
                RadialMenuRenderer.resetHover();
            }

            // Clear number-key tracking when V is not held
            for (int i = 0; i < wasNumKeyHeld.length; i++) wasNumKeyHeld[i] = false;
        }

        wasRadialHeld = held;
    }

    /**
     * Handles the F key, which now specifically toggles the Iron/Steel push/pull
     * <em>power</em> (effect) on or off. Iron and Steel continue burning regardless;
     * only whether the effect fires changes.
     *
     * <p>The packet is only sent (and the server only processes it) when Iron or Steel
     * is actively burning, so pressing F while those metals are not selected has no effect.</p>
     *
     * <p>Fires on the <em>rising edge</em> (key just pressed), not on release.</p>
     */
    private static void handleBurnKey(Minecraft mc) {
        boolean held = ModKeybinds.KEY_BURN.isDown();

        if (held && !wasBurnHeld) {
            // Rising edge – send iron/steel power toggle
            PacketDistributor.sendToServer(ClientActionPacket.toggleBurn());
        }

        wasBurnHeld = held;
    }

    /**
     * Handles left-click (Steel Push) and right-click (Iron Pull) for the Iron/Steel group.
     *
     * <p>Only sends packets when Iron or Steel is actively burning <em>and</em> the
     * Iron/Steel power flag is enabled (F toggle ON). When the power flag is OFF, clicks
     * are suppressed so the player can interact normally (e.g. punch something) without
     * being pulled/pushed. Uses a 2-tick rate-limit to avoid flooding the server.</p>
     */
    private static void handleMouseButtons(Minecraft mc) {
        if (mc.player == null) return;
        if (!mc.player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return;

        AllomanticData data = mc.player.getData(ModAttachments.ALLOMANTIC_DATA.get());

        // Iron pull and Steel push are each independently active when their metal is burning
        boolean ironSteelActive = data.isMetalActive(AllomanticMetal.IRON)
                || data.isMetalActive(AllomanticMetal.STEEL);

        // Power flag check: F toggles whether push/pull effect fires while still burning
        if (!ironSteelActive || !data.isIronSteelPowerEnabled()) return;

        long    win       = mc.getWindow().getWindow();
        boolean leftHeld  = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT)  == GLFW.GLFW_PRESS;
        boolean rightHeld = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;

        if (pushPullCooldown <= 0) {
            if (leftHeld) {
                // Left-click → Steel Push
                PacketDistributor.sendToServer(ClientActionPacket.requestPush());
                pushPullCooldown = 2;
            } else if (rightHeld) {
                // Right-click → Iron Pull
                PacketDistributor.sendToServer(ClientActionPacket.requestPull());
                pushPullCooldown = 2;
            }
        }
    }

    // ── Sound interception for Tin ────────────────────────────────────────────

    @SubscribeEvent
    public void onPlaySound(PlaySoundEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!mc.player.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return;

        AllomanticData data = mc.player.getData(ModAttachments.ALLOMANTIC_DATA.get());
        // Only intercept sounds when Tin is actively burning
        if (!data.isMetalActive(AllomanticMetal.TIN)) return;

        var sound = event.getSound();
        if (sound == null) return;

        net.minecraft.world.phys.Vec3 soundPos = new net.minecraft.world.phys.Vec3(
                sound.getX(), sound.getY(), sound.getZ());

        TinSoundTracker.onSoundPlayed(sound.getLocation().toString(), soundPos);
    }
}
