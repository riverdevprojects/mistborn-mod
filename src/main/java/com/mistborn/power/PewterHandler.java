package com.mistborn.power;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;

/**
 * Pewter Allomancy – physical enhancement.
 *
 * While burning Pewter the player receives:
 * <ul>
 *   <li>Speed II (MOVEMENT_SPEED amplifier 1)</li>
 *   <li>Strength III (DAMAGE_BOOST amplifier 2)</li>
 * </ul>
 * Both effects are refreshed every tick with a 3-tick duration so they fade
 * almost immediately when burning stops.
 *
 * Pewter also negates fall damage (handled in {@link PowerHandler}).
 */
public class PewterHandler {

    /** Duration in ticks for the refreshed effect – just long enough to feel continuous. */
    private static final int EFFECT_DURATION = 3;

    public static void tick(ServerPlayer player) {
        applyEffects(player);
    }

    public static void applyEffects(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 1, false, false, false));
        entity.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST,   EFFECT_DURATION, 2, false, false, false));
    }

    private PewterHandler() {}
}
