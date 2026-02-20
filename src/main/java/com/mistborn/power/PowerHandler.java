package com.mistborn.power;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.config.MistbornConfig;
import com.mistborn.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Central dispatcher called each server tick for player burning logic and
 * global effects (mob linger counters, Steel projectiles).
 *
 * <p>Called from the server-side {@code PlayerTickEvent.Post} and
 * {@code LevelTickEvent.Post} listeners in {@code MistbornMod}.</p>
 */
public class PowerHandler {

    // ── Per-player burning tick ───────────────────────────────────────────────

    /**
     * Runs each server tick for every online player.
     * Drains reserve, applies metal effects, syncs to client on change.
     */
    public static void onPlayerTick(ServerPlayer player) {
        AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());
        AllomanticMetal selected = data.getCurrentlyBurning();

        // Nothing selected, or F-toggle is off: no effects this tick
        if (selected == null || !data.isBurningActive()) {
            ModNetwork.syncIfDirty(player);
            return;
        }

        float burnRate = (float) MistbornConfig.BURN_RATE.get().doubleValue();

        if (selected == AllomanticMetal.IRON) {
            // Iron/Steel group – drain both reserves at half-rate per tick.
            // The player can push as long as Steel has reserve, pull as long as Iron has reserve.
            float ironLeft  = data.getReserve(AllomanticMetal.IRON);
            float steelLeft = data.getReserve(AllomanticMetal.STEEL);

            if (ironLeft <= 0f && steelLeft <= 0f) {
                // Both depleted: auto-stop burning (keep metal selected)
                data.setBurningActive(false);
                ModNetwork.syncIfDirty(player);
                return;
            }

            // Drain each at half the normal rate (burning both simultaneously)
            float half = burnRate * 0.5f;
            if (ironLeft  > 0f) data.drainReserve(AllomanticMetal.IRON,  half);
            if (steelLeft > 0f) data.drainReserve(AllomanticMetal.STEEL, half);

            // Iron/Steel effects are entirely key-driven (mouse clicks send push/pull packets)

        } else {
            // All other metals: standard single-reserve drain
            float reserve = data.getReserve(selected);
            if (reserve <= 0f) {
                data.setBurningActive(false);
                ModNetwork.syncIfDirty(player);
                return;
            }

            float newReserve = data.drainReserve(selected, burnRate);

            // Apply per-tick metal effect
            switch (selected) {
                case PEWTER -> PewterHandler.tick(player);
                case TIN    -> TinHandler.tick(player);
                case COPPER -> CopperHandler.tick(player);
                case BRONZE -> BronzeHandler.tick(player);
                case BRASS  -> BrassHandler.tick(player);
                case ZINC   -> ZincHandler.tick(player);
                default     -> { /* IRON/STEEL handled above; others future-proof */ }
            }

            if (newReserve <= 0f) {
                data.setBurningActive(false);
            }
        }

        ModNetwork.syncIfDirty(player);
    }

    // ── Global effects (run once per server level tick) ───────────────────────

    /**
     * Called once per server level tick to handle:
     * <ul>
     *   <li>Brass / Zinc linger countdowns on all living entities</li>
     *   <li>Steel projectile collision detection on item entities</li>
     * </ul>
     */
    public static void tickGlobalEffects(ServerLevel level) {
        // Brass & Zinc linger on all loaded living entities
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof LivingEntity living) {
                BrassHandler.tickLingerOnMob(living);
                ZincHandler.tickLingerOnMob(living);
            }
        });
    }

    /**
     * Must be called from a LevelTickEvent (or similar) to process Steel projectile
     * collision.  Only steel-pushed item entities are tracked.
     */
    public static void tickProjectiles(ServerLevel level) {
        List<ItemEntity> items = new java.util.ArrayList<>();
        level.getAllEntities().forEach(entity -> {
            if (entity instanceof ItemEntity item
                    && item.hasData(ModAttachments.STEEL_PROJECTILE.get())
                    && Boolean.TRUE.equals(item.getData(ModAttachments.STEEL_PROJECTILE.get()))) {
                items.add(item);
            }
        });

        for (ItemEntity item : items) {
            processProjectile(level, item);
        }
    }

    private static void processProjectile(ServerLevel level, ItemEntity item) {
        Vec3 motion = item.getDeltaMovement();
        if (motion.lengthSqr() < 0.01) {
            // Come to rest – clear projectile flag
            item.setData(ModAttachments.STEEL_PROJECTILE.get(), false);
            return;
        }

        // Sweep: check if any living entity is within the motion vector this tick
        Vec3 start = item.position();
        Vec3 end   = start.add(motion);
        AABB sweepBox = new AABB(
                Math.min(start.x, end.x) - 0.5, Math.min(start.y, end.y) - 0.5, Math.min(start.z, end.z) - 0.5,
                Math.max(start.x, end.x) + 0.5, Math.max(start.y, end.y) + 0.5, Math.max(start.z, end.z) + 0.5);

        // Cast to Entity so the != comparison compiles across unrelated subtypes.
        final net.minecraft.world.entity.Entity itemAsEntity = item;
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, sweepBox,
                e -> e != itemAsEntity && !e.isSpectator());

        for (LivingEntity target : targets) {
            item.setData(ModAttachments.STEEL_PROJECTILE.get(), false);
            item.setDeltaMovement(Vec3.ZERO);

            // Pewter burner: no damage, item just drops
            boolean targetBurningPewter = target.hasData(ModAttachments.ALLOMANTIC_DATA.get())
                    && target.getData(ModAttachments.ALLOMANTIC_DATA.get()).isPewterBurning();

            if (!targetBurningPewter) {
                float damage = (float) MistbornConfig.PROJECTILE_DAMAGE.get().doubleValue();
                target.hurt(level.damageSources().thrown(item, null), damage);
            }
            break; // Only one collision per tick
        }

        // Check block collision – if the item stopped moving due to a block, clear the flag
        // NeoForge handles block collision for item entities; speed drops naturally.
        if (!level.noCollision(new AABB(end.x - 0.1, end.y - 0.1, end.z - 0.1,
                                        end.x + 0.1, end.y + 0.1, end.z + 0.1))) {
            item.setData(ModAttachments.STEEL_PROJECTILE.get(), false);
            item.setDeltaMovement(Vec3.ZERO);
        }
    }

    // ── Fall damage hook ──────────────────────────────────────────────────────

    /**
     * Called from {@code LivingFallEvent} to negate fall damage when Pewter is burning.
     *
     * @return true if fall damage should be negated
     */
    public static boolean shouldNegateFallDamage(LivingEntity entity) {
        if (!entity.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return false;
        return entity.getData(ModAttachments.ALLOMANTIC_DATA.get()).isPewterBurning();
    }

    private PowerHandler() {}
}
