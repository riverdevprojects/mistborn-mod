package com.mistborn.power;

import net.minecraft.server.level.ServerPlayer;

/**
 * Copper Allomancy – Coppercloud.
 *
 * While burning Copper, the {@code isCopperActive} flag on the player's
 * {@link com.mistborn.capability.AllomanticData} is true (set automatically
 * when {@code currentlyBurning == COPPER} in {@code AllomanticData}).
 *
 * This flag is checked by {@link BronzeHandler} to exclude the Copper burner
 * from the Glowing effect.  No additional per-tick logic is required on the
 * server; the copper cloud is purely a passive suppression effect.
 */
public class CopperHandler {

    /** Nothing to do per-tick – the flag on AllomanticData handles everything. */
    public static void tick(ServerPlayer player) {
        // Copper's effect is purely passive (isCopperActive flag).
        // Bronze checks that flag during its pulse scan.
    }

    private CopperHandler() {}
}
