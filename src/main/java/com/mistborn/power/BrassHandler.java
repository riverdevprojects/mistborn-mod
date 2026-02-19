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
 * Brass Allomancy – Soother.
 *
 * <p>While burning Brass, every 10 ticks the burner suppresses the attack AI
 * of hostile mobs within {@link MistbornConfig#BRASS_RANGE} blocks by
 * clearing their target.  A soft blue particle cloud surrounds affected mobs
 * to signal they are soothed.</p>
 *
 * <p>After burning stops, affected mobs keep the suppression for
 * {@link MistbornConfig#BRASS_LINGER_TICKS} ticks before their AI reverts.
 * This linger countdown is stored in the {@code BRASS_LINGER} attachment on
 * each mob so it persists even if the burner logs out.</p>
 */
public class BrassHandler {

    private static final int PULSE_INTERVAL = 10;

    // ── Active burning tick ───────────────────────────────────────────────────

    public static void tick(ServerPlayer player) {
        if (player.tickCount % PULSE_INTERVAL != 0) return;

        double range = MistbornConfig.BRASS_RANGE.get();
        int lingerTicks = MistbornConfig.BRASS_LINGER_TICKS.get();

        AABB searchBox = player.getBoundingBox().inflate(range);
        List<Monster> hostiles = player.level().getEntitiesOfClass(
                Monster.class, searchBox, e -> true);

        for (Monster mob : hostiles) {
            mob.setTarget(null);
            mob.setLastHurtByMob(null);
            // Reset the linger timer to max so it stays soothed
            mob.setData(ModAttachments.BRASS_LINGER.get(), lingerTicks);
            spawnSoothParticles(player.level() instanceof ServerLevel sl ? sl : null, mob);
        }
    }

    // ── Linger tick (called every server tick for all mobs with BRASS_LINGER > 0) ──

    /**
     * Called from {@link PowerHandler#tickGlobalEffects} for every living entity
     * each server tick.  Decrements the linger counter and, while it is active,
     * continues to suppress the mob's target.
     */
    public static void tickLingerOnMob(LivingEntity entity) {
        if (!(entity instanceof Mob mob)) return;
        if (!mob.hasData(ModAttachments.BRASS_LINGER.get())) return;
        int linger = mob.getData(ModAttachments.BRASS_LINGER.get());
        if (linger <= 0) return;

        mob.setTarget(null);
        mob.setLastHurtByMob(null);
        mob.setData(ModAttachments.BRASS_LINGER.get(), linger - 1);

        if (entity.level() instanceof ServerLevel sl && mob.tickCount % 4 == 0) {
            spawnSoothParticles(sl, mob);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void spawnSoothParticles(ServerLevel level, Mob mob) {
        if (level == null) return;
        double x = mob.getX() + (level.random.nextDouble() - 0.5) * mob.getBbWidth();
        double y = mob.getY() + level.random.nextDouble() * mob.getBbHeight();
        double z = mob.getZ() + (level.random.nextDouble() - 0.5) * mob.getBbWidth();
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 1, 0, 0.05, 0, 0.02);
    }

    private BrassHandler() {}
}
