package com.mistborn.power;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.config.MistbornConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Bronze Allomancy – Seeker.
 *
 * Every 10 ticks the Bronze burner pulses outward within
 * {@link MistbornConfig#BRONZE_RANGE} blocks.  Any living entity that is
 * currently burning an Allomantic metal receives {@code MobEffects.GLOWING}
 * for 15 ticks, making them visually highlighted.
 *
 * Players whose capability has {@code isCopperActive == true} are immune
 * (they are inside a Coppercloud and cannot be sensed).
 *
 * Mobs do not burn metals, so they will never trigger the Glowing effect
 * through this system.  The handler is written to accept any future
 * {@code LivingEntity} subclass that exposes an {@link AllomanticData}
 * attachment, ensuring forward compatibility with Allomantic mobs.
 */
public class BronzeHandler {

    private static final int PULSE_INTERVAL = 10;
    private static final int GLOW_DURATION  = 15;

    public static void tick(ServerPlayer player) {
        if (player.tickCount % PULSE_INTERVAL != 0) return;

        double range = MistbornConfig.BRONZE_RANGE.get();
        AABB searchBox = player.getBoundingBox().inflate(range);

        List<LivingEntity> nearby = player.level().getEntitiesOfClass(
                LivingEntity.class, searchBox,
                e -> e != player);

        for (LivingEntity entity : nearby) {
            if (!entity.hasData(ModAttachments.ALLOMANTIC_DATA.get())) continue;

            AllomanticData data = entity.getData(ModAttachments.ALLOMANTIC_DATA.get());

            // Skip Copper-shielded entities
            if (data.isCopperActive()) continue;

            // Only light up entities that are actively burning a metal
            if (data.getCurrentlyBurning() == null) continue;

            entity.addEffect(new MobEffectInstance(MobEffects.GLOWING, GLOW_DURATION, 0, false, false, false));
        }
    }

    private BronzeHandler() {}
}
