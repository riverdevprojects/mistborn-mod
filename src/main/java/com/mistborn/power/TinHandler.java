package com.mistborn.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;

/**
 * Tin Allomancy – enhanced senses.
 *
 * Server-side behaviour:
 * <ul>
 *   <li>Night Vision I refreshed every tick (3-tick duration) for perpetual
 *       night-vision while burning.</li>
 * </ul>
 *
 * Client-side behaviour (see {@link com.mistborn.client.TinSoundTracker}):
 * <ul>
 *   <li>Intercepts nearby sound events and renders a sidebar showing their
 *       approximate direction relative to the player.</li>
 * </ul>
 */
public class TinHandler {

    private static final int EFFECT_DURATION = 3;

    public static void tick(ServerPlayer player) {
        player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, EFFECT_DURATION, 0, false, false, false));
    }

    private TinHandler() {}
}
