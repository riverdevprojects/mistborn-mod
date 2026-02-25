package com.mistborn.power;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.config.MistbornConfig;
import com.mistborn.network.ModNetwork;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Central dispatcher called each server tick for player burning logic and
 * global effects (mob linger counters, Steel projectiles).
 *
 * <p>Called from the server-side {@code PlayerTickEvent.Post} and
 * {@code LevelTickEvent.Post} listeners in {@code MistbornMod}.</p>
 *
 * <p>Multiple metals may be active simultaneously. Each active metal drains its
 * own reserve and applies its effects every tick. If a reserve empties, that
 * metal is automatically removed from the active set.</p>
 */
public class PowerHandler {

    // ── Per-player burning tick ───────────────────────────────────────────────

    /**
     * Runs each server tick for every online player.
     * Drains reserve for each active metal, applies metal effects, syncs to client.
     */
    public static void onPlayerTick(ServerPlayer player) {
        AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

        // Tick the iron-pull-block cooldown every tick regardless of burning state
        data.tickIronPullBlockCooldown();

        Set<AllomanticMetal> activeMetals = data.getActiveMetals();
        if (activeMetals.isEmpty()) {
            ModNetwork.syncIfDirty(player);
            return;
        }

        float burnRate = (float) MistbornConfig.BURN_RATE.get().doubleValue();

        // Track which metals ran out of reserve this tick and must be deactivated
        Set<AllomanticMetal> depleted = new HashSet<>();

        // ── Iron/Steel group ──────────────────────────────────────────────────
        // Both drain at half the normal burn rate when active so burning them
        // together costs the same total as burning any other single metal.
        boolean ironActive  = activeMetals.contains(AllomanticMetal.IRON);
        boolean steelActive = activeMetals.contains(AllomanticMetal.STEEL);

        if (ironActive) {
            float remaining = data.drainReserve(AllomanticMetal.IRON, burnRate * 0.5f);
            if (remaining <= 0f) depleted.add(AllomanticMetal.IRON);
        }
        if (steelActive) {
            float remaining = data.drainReserve(AllomanticMetal.STEEL, burnRate * 0.5f);
            if (remaining <= 0f) depleted.add(AllomanticMetal.STEEL);
        }
        // Iron/Steel push/pull effects are entirely key-driven (mouse-click packets)

        // ── All other metals ──────────────────────────────────────────────────
        for (AllomanticMetal metal : activeMetals) {
            if (metal == AllomanticMetal.IRON || metal == AllomanticMetal.STEEL) continue;

            float reserve = data.getReserve(metal);
            if (reserve <= 0f) {
                depleted.add(metal);
                continue;
            }

            float newReserve = data.drainReserve(metal, burnRate);

            switch (metal) {
                case PEWTER -> PewterHandler.tick(player);
                case TIN    -> TinHandler.tick(player);
                case COPPER -> CopperHandler.tick(player);
                case BRONZE -> BronzeHandler.tick(player);
                case BRASS  -> BrassHandler.tick(player);
                case ZINC   -> ZincHandler.tick(player);
                default     -> { /* future metals */ }
            }

            if (newReserve <= 0f) depleted.add(metal);
        }

        // Remove depleted metals from active (and set) state
        for (AllomanticMetal metal : depleted) {
            data.removeFromActive(metal);
            data.removeFromSet(metal);
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
        List<ItemEntity> items = new ArrayList<>();
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

    /**
     * Must be called each server level tick to maintain the grounded-ingot anchor state.
     *
     * <p>An iron ingot {@link ItemEntity} is marked as "grounded" (and thus treated as a
     * {@link WeightClass#HEAVY} Steel-push anchor) whenever it is resting on solid ground.
     * The flag is cleared as soon as the ingot leaves the ground (e.g. bounces, is kicked
     * by an entity, or is picked up).</p>
     *
     * <p>This enables the steelpusher to throw an ingot downward and then push off it once
     * it lands, or to use ingots on the ground as static anchor points.</p>
     */
    public static void tickGroundedIngots(ServerLevel level) {
        level.getAllEntities().forEach(entity -> {
            if (!(entity instanceof ItemEntity item)) return;
            // Only iron ingots participate in the anchor system
            if (!item.getItem().is(Items.IRON_INGOT)) return;

            boolean onGround = item.onGround();
            boolean wasGrounded = item.hasData(ModAttachments.GROUNDED_INGOT.get())
                    && Boolean.TRUE.equals(item.getData(ModAttachments.GROUNDED_INGOT.get()));

            if (onGround && !wasGrounded) {
                item.setData(ModAttachments.GROUNDED_INGOT.get(), true);
            } else if (!onGround && wasGrounded) {
                item.setData(ModAttachments.GROUNDED_INGOT.get(), false);
            }
        });
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
     * Called from {@code LivingFallEvent} to negate fall/impact damage.
     *
     * <p>Damage is negated when:</p>
     * <ul>
     *   <li>Pewter is actively burning (raw physical enhancement), or</li>
     *   <li>The iron-pull-block cooldown is active, meaning the player recently
     *       pulled themselves toward a metal block and is landing against it.
     *       In Mistborn lore the Allomancer can soften their arrival by modulating
     *       the pull force, so no damage is appropriate.</li>
     * </ul>
     *
     * @return true if fall damage should be negated
     */
    public static boolean shouldNegateFallDamage(LivingEntity entity) {
        if (!entity.hasData(ModAttachments.ALLOMANTIC_DATA.get())) return false;
        AllomanticData data = entity.getData(ModAttachments.ALLOMANTIC_DATA.get());
        return data.isPewterBurning() || data.getIronPullBlockCooldown() > 0;
    }

    private PowerHandler() {}
}
