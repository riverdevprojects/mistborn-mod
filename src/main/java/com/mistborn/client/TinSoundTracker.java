package com.mistborn.client;

import com.mistborn.config.MistbornConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Client-side rolling log of nearby sound events for the Tin Allomancy HUD sidebar.
 *
 * <p>When a sound is played on the client (via the NeoForge {@code PlaySoundAtEntityEvent}
 * hook in {@link ClientEventHandler}), if the local player is burning Tin and the sound
 * origin is within {@link MistbornConfig#TIN_SOUND_RANGE} blocks, it is recorded here.</p>
 *
 * <p>The sidebar renderer ({@link HudRenderer}) reads from this class each frame.</p>
 */
public class TinSoundTracker {

    /** Maximum entries shown on the sidebar at once. */
    private static final int MAX_ENTRIES = 8;

    /** How long each entry stays visible, in ticks. */
    private static final int ENTRY_LIFETIME_TICKS = 80;

    private record SoundEntry(String direction, String soundName, int expiryTick) {}

    private static final Deque<SoundEntry> entries = new ArrayDeque<>();

    /**
     * Called from {@link ClientEventHandler} when a sound event fires on the client.
     *
     * @param soundName  Registry name of the sound event
     * @param worldPos   World position where the sound originated
     */
    public static void onSoundPlayed(String soundName, Vec3 worldPos) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 playerPos = mc.player.position();
        double rangeSq = MistbornConfig.TIN_SOUND_RANGE.get();
        rangeSq = rangeSq * rangeSq;

        if (worldPos.distanceToSqr(playerPos) > rangeSq) return;

        String dir = computeDirection(mc.player.position(), mc.player.getEyeY(), worldPos);
        String shortName = shortenSoundName(soundName);

        // Remove oldest if at capacity
        while (entries.size() >= MAX_ENTRIES) {
            entries.pollFirst();
        }

        int expiry = mc.player.tickCount + ENTRY_LIFETIME_TICKS;
        entries.addLast(new SoundEntry(dir, shortName, expiry));
    }

    /**
     * Called each render tick to prune expired entries.
     */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            entries.clear();
            return;
        }
        int currentTick = mc.player.tickCount;
        entries.removeIf(e -> e.expiryTick() <= currentTick);
    }

    /**
     * Returns a snapshot of current entries for rendering.
     * Each string is formatted as {@code [DIR] sound.name}.
     */
    public static List<String> getDisplayLines() {
        List<String> result = new ArrayList<>();
        for (SoundEntry e : entries) {
            result.add("[" + e.direction() + "] " + e.soundName());
        }
        return result;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Compute a coarse compass + vertical direction label from the player to the sound.
     */
    private static String computeDirection(Vec3 playerPos, double eyeY, Vec3 soundPos) {
        double dx = soundPos.x - playerPos.x;
        double dy = soundPos.y - eyeY;
        double dz = soundPos.z - playerPos.z;
        double hDist = Math.sqrt(dx * dx + dz * dz);

        // Vertical component
        if (hDist < 1.0 && Math.abs(dy) > 1.0) {
            return dy > 0 ? "Up" : "Down";
        }

        // Horizontal cardinal direction
        double angle = Math.toDegrees(Math.atan2(-dx, dz)); // -dx because Minecraft +X is East
        // Adjust to 0-360
        angle = (angle + 360) % 360;

        String horiz;
        if (angle < 22.5 || angle >= 337.5)  horiz = "N";
        else if (angle < 67.5)               horiz = "NE";
        else if (angle < 112.5)              horiz = "E";
        else if (angle < 157.5)              horiz = "SE";
        else if (angle < 202.5)              horiz = "S";
        else if (angle < 247.5)              horiz = "SW";
        else if (angle < 292.5)              horiz = "W";
        else                                 horiz = "NW";

        // Add Up/Down qualifier
        double vertAngle = Math.toDegrees(Math.atan2(dy, hDist));
        if (vertAngle > 30)       return horiz + "/Up";
        else if (vertAngle < -30) return horiz + "/Down";
        return horiz;
    }

    private static String shortenSoundName(String full) {
        // Show only the last two segments: e.g. "minecraft:entity.creeper.primed" → "entity.creeper.primed"
        int colon = full.indexOf(':');
        String path = colon >= 0 ? full.substring(colon + 1) : full;
        // Truncate if too long
        if (path.length() > 28) path = path.substring(path.length() - 28);
        return path;
    }

    private TinSoundTracker() {}
}
