package com.mistborn.power;

import com.mistborn.capability.ModAttachments;
import com.mistborn.config.MistbornConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Zinc Allomancy – Rioter.
 *
 * <p>While burning Zinc, every 10 ticks the burner finds hostile mobs within
 * {@link MistbornConfig#ZINC_RANGE} blocks and forces each one to attack a
 * random nearby living entity that is <em>not</em> the Zinc burner.
 * A red/orange flame particle effect surrounds rioted mobs.</p>
 *
 * <p>After burning stops, the {@code ZINC_LINGER} attachment on each affected
 * mob counts down; mobs re-acquire natural targets once the linger expires.</p>
 */
public class ZincHandler {

    private static final int PULSE_INTERVAL = 10;

    // ── Active burning tick ───────────────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        if (player.tickCount % PULSE_INTERVAL != 0) return;

        double range = MistbornConfig.ZINC_RANGE.get();
        int lingerTicks = MistbornConfig.ZINC_LINGER_TICKS.get();

        AABB searchBox = player.getBoundingBox().inflate(range);
        List<Monster> hostiles = player.level().getEntitiesOfClass(
                Monster.class, searchBox, e -> true);

        for (Monster mob : hostiles) {
            // Find a random nearby living entity that is NOT the Zinc burner
            AABB mobSearchBox = mob.getBoundingBox().inflate(16);
            List<LivingEntity> candidates = mob.level().getEntitiesOfClass(
                    LivingEntity.class, mobSearchBox,
                    e -> e != mob && e != player);

            if (!candidates.isEmpty()) {
                LivingEntity target = candidates.get(mob.level().random.nextInt(candidates.size()));
                mob.setTarget(target);
            }

            mob.setData(ModAttachments.ZINC_LINGER.get(), lingerTicks);
            spawnRiotParticles(player.level() instanceof ServerLevel sl ? sl : null, mob);
        }
    }

    // ── Linger tick ───────────────────────────────────────────────────────────

    /**
     * Called from {@link PowerHandler#tickGlobalEffects} for every mob each tick.
     * While linger is active we don't force a new target – we just let the mob
     * pursue whatever it was forced onto.  Decrement the counter.
     */
    public static void tickLingerOnMob(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return;
        if (!mob.hasData(ModAttachments.ZINC_LINGER.get())) return;
        int linger = mob.getData(ModAttachments.ZINC_LINGER.get());
        if (linger <= 0) return;

        mob.setData(ModAttachments.ZINC_LINGER.get(), linger - 1);

        if (entity.level() instanceof ServerLevel sl && mob.tickCount % 4 == 0) {
            spawnRiotParticles(sl, mob);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void spawnRiotParticles(ServerLevel level, Mob mob) {
        if (level == null) return;
        double x = mob.getX() + (level.random.nextDouble() - 0.5) * mob.getBbWidth();
        double y = mob.getY() + level.random.nextDouble() * mob.getBbHeight();
        double z = mob.getZ() + (level.random.nextDouble() - 0.5) * mob.getBbWidth();
        level.sendParticles(ParticleTypes.FLAME, x, y, z, 1, 0, 0.05, 0, 0.04);
    }

    private ZincHandler() {}
}
