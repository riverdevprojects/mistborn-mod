package com.mistborn.power;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.config.MistbornConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

import java.util.ArrayList;
import java.util.List;

/**
 * Shared logic for Iron (Pull) and Steel (Push) Allomancy.
 *
 * <h2>Detection</h2>
 * Scans within {@link MistbornConfig#IRON_STEEL_RANGE} blocks for:
 * <ul>
 *   <li><b>LIGHT</b> – {@link ItemEntity} instances carrying metal items
 *       (tagged {@code c:ingots}, {@code c:nuggets}, or metal armor/tools).</li>
 *   <li><b>MEDIUM</b> – Players or mobs wearing any iron/gold/chainmail/netherite
 *       armour piece, or holding a metal item.</li>
 *   <li><b>HEAVY</b> – Metal storage blocks, rails, anvils, cauldrons.</li>
 * </ul>
 *
 * <h2>Pewter rule</h2>
 * If a MEDIUM entity is burning Pewter their weight class is elevated to HEAVY.
 *
 * <h2>Crosshair targeting</h2>
 * The source with the smallest angular deviation from the player's look direction
 * is selected as the push/pull target.
 *
 * <h2>Physics</h2>
 * <ul>
 *   <li>LIGHT  – target moves, player stays (pull) / item becomes projectile (push).</li>
 *   <li>MEDIUM – both move toward/away from each other, force split evenly.</li>
 *   <li>HEAVY  – player moves (block stays).</li>
 * </ul>
 */
public class IronSteelHandler {

    // ── Metal item detection helpers ──────────────────────────────────────────

    /** Returns true if the ItemStack is considered a metal item for Allomantic purposes. */
    public static boolean isMetalItem(ItemStack stack) {
        if (stack.isEmpty()) return false;
        // Tagged as ingot or nugget (covers most metals from mods too)
        if (stack.is(Tags.Items.INGOTS) || stack.is(Tags.Items.NUGGETS)) return true;
        // Chainmail armor (no ArmorMaterials constant in 1.21.1)
        if (isChainmailItem(stack.getItem())) return true;
        // Other metal armor via material comparison (iron, gold, netherite)
        if (stack.getItem() instanceof ArmorItem armor) {
            var mat = armor.getMaterial().value();
            return isMetalArmorMaterial(mat);
        }
        // Common metal tools/weapons by identity (iron, gold, netherite)
        var item = stack.getItem();
        return item == net.minecraft.world.item.Items.IRON_SWORD
                || item == net.minecraft.world.item.Items.GOLDEN_SWORD
                || item == net.minecraft.world.item.Items.NETHERITE_SWORD
                || item == net.minecraft.world.item.Items.IRON_AXE
                || item == net.minecraft.world.item.Items.GOLDEN_AXE
                || item == net.minecraft.world.item.Items.NETHERITE_AXE
                || item == net.minecraft.world.item.Items.IRON_PICKAXE
                || item == net.minecraft.world.item.Items.GOLDEN_PICKAXE
                || item == net.minecraft.world.item.Items.NETHERITE_PICKAXE
                || item == net.minecraft.world.item.Items.IRON_SHOVEL
                || item == net.minecraft.world.item.Items.GOLDEN_SHOVEL
                || item == net.minecraft.world.item.Items.NETHERITE_SHOVEL
                || item == net.minecraft.world.item.Items.IRON_HOE
                || item == net.minecraft.world.item.Items.GOLDEN_HOE
                || item == net.minecraft.world.item.Items.NETHERITE_HOE
                || item == net.minecraft.world.item.Items.SHEARS     // iron shears
                || item == net.minecraft.world.item.Items.BUCKET;    // iron bucket
    }

    private static boolean isMetalArmorMaterial(net.minecraft.world.item.ArmorMaterial mat) {
        // CHAINMAIL does not have an ArmorMaterials constant in 1.21.1; handled by item identity.
        return mat == ArmorMaterials.IRON.value()
                || mat == ArmorMaterials.GOLD.value()
                || mat == ArmorMaterials.NETHERITE.value();
    }

    private static boolean isChainmailItem(net.minecraft.world.item.Item item) {
        return item == net.minecraft.world.item.Items.CHAINMAIL_HELMET
                || item == net.minecraft.world.item.Items.CHAINMAIL_CHESTPLATE
                || item == net.minecraft.world.item.Items.CHAINMAIL_LEGGINGS
                || item == net.minecraft.world.item.Items.CHAINMAIL_BOOTS;
    }

    /** Returns true if the entity wears at least one piece of metal armour or holds a metal item. */
    private static boolean entityHasMetal(LivingEntity entity) {
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (isMetalItem(entity.getItemBySlot(slot))) return true;
        }
        return false;
    }

    // ── Metal block detection ─────────────────────────────────────────────────

    private static boolean isMetalBlock(Level level, BlockPos pos) {
        var state = level.getBlockState(pos);
        if (state.is(Tags.Blocks.STORAGE_BLOCKS_IRON))   return true;
        if (state.is(Tags.Blocks.STORAGE_BLOCKS_GOLD))   return true;
        if (state.is(Tags.Blocks.STORAGE_BLOCKS_NETHERITE)) return true;
        if (state.is(BlockTags.RAILS))                   return true;
        if (state.is(Blocks.ANVIL))                      return true;
        if (state.is(Blocks.CHIPPED_ANVIL))              return true;
        if (state.is(Blocks.DAMAGED_ANVIL))              return true;
        if (state.is(Blocks.CAULDRON))                   return true;
        if (state.is(Blocks.WATER_CAULDRON))             return true;
        if (state.is(Blocks.LAVA_CAULDRON))              return true;
        if (state.is(Blocks.POWDER_SNOW_CAULDRON))       return true;
        if (state.is(Blocks.CHAIN))                      return true;
        if (state.is(Blocks.IRON_BARS))                  return true;
        if (state.is(Blocks.IRON_DOOR))                  return true;
        if (state.is(Blocks.IRON_TRAPDOOR))              return true;
        if (state.is(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE)) return true;
        if (state.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE)) return true;
        if (state.is(Blocks.HOPPER))                     return true;
        // Minecart is an entity, not a block; detector_rail is already covered by RAILS tag above.
        return false;
    }

    // ── Source discovery ──────────────────────────────────────────────────────

    /**
     * Discovers all metal sources within detection range of the player.
     * Called client-side (for rendering) and server-side (for physics).
     */
    public static List<MetalSource> findSources(Player player, Level level) {
        double range = MistbornConfig.IRON_STEEL_RANGE.get();
        List<MetalSource> sources = new ArrayList<>();

        // ── Entity sources ────────────────────────────────────────────────────
        AABB searchBox = player.getBoundingBox().inflate(range);

        // Item entities
        for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, searchBox, e -> true)) {
            if (isMetalItem(item.getItem())) {
                sources.add(MetalSource.ofEntity(item, WeightClass.LIGHT));
            }
        }

        // Living entities (players and mobs)
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, searchBox,
                e -> e != player && entityHasMetal(e))) {
            WeightClass wc = WeightClass.MEDIUM;
            // Pewter elevation
            if (entity.hasData(ModAttachments.ALLOMANTIC_DATA.get())) {
                AllomanticData d = entity.getData(ModAttachments.ALLOMANTIC_DATA.get());
                if (d.isPewterBurning()) wc = wc.elevated();
            }
            sources.add(MetalSource.ofEntity(entity, wc));
        }

        // ── Block sources ─────────────────────────────────────────────────────
        int iRange = (int) Math.ceil(range);
        BlockPos centre = player.blockPosition();
        for (int dx = -iRange; dx <= iRange; dx++) {
            for (int dy = -iRange; dy <= iRange; dy++) {
                for (int dz = -iRange; dz <= iRange; dz++) {
                    BlockPos pos = centre.offset(dx, dy, dz);
                    if (pos.distSqr(centre) > range * range) continue;
                    if (isMetalBlock(level, pos)) {
                        sources.add(MetalSource.ofBlock(pos));
                    }
                }
            }
        }

        return sources;
    }

    // ── Crosshair targeting ───────────────────────────────────────────────────

    /**
     * Returns the metal source closest to the player's look direction, or null
     * if the list is empty.
     */
    public static MetalSource findTarget(Player player, List<MetalSource> sources) {
        if (sources.isEmpty()) return null;

        Vec3 eye  = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        MetalSource best = null;
        double bestAngle = Double.MAX_VALUE;

        for (MetalSource src : sources) {
            Vec3 toSrc = src.position.subtract(eye);
            double dist = toSrc.length();
            if (dist < 0.1) continue;
            // Angle in radians between look direction and direction to source
            double dot   = look.dot(toSrc.normalize());
            double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
            if (angle < bestAngle) {
                bestAngle = angle;
                best      = src;
            }
        }

        return best;
    }

    // ── Force calculation ─────────────────────────────────────────────────────

    /**
     * Computes force magnitude.  Scales slightly with distance: closer = slightly
     * weaker (shorter line → less leverage), faithful to the books.
     *
     * @param dist distance from player to source in blocks
     * @return force scalar
     */
    private static double computeForce(double dist) {
        double base = MistbornConfig.PUSH_PULL_FORCE.get();
        // Minimum distance guard
        double d = Math.max(0.5, dist);
        // Slight distance scaling: force slightly increases with distance (max 1.5× at range)
        double scale = 0.8 + 0.2 * (d / MistbornConfig.IRON_STEEL_RANGE.get());
        return base * scale;
    }

    // ── Iron Pull ─────────────────────────────────────────────────────────────

    /**
     * Execute an Iron Pull from the server on the given target source.
     */
    public static void executePull(ServerPlayer player, MetalSource target) {
        Vec3 playerPos = player.position().add(0, player.getEyeHeight(), 0);
        Vec3 toPlayer  = playerPos.subtract(target.position);
        double dist    = toPlayer.length();
        if (dist < 0.001) return;
        Vec3 dir       = toPlayer.normalize();
        double force   = computeForce(dist);

        switch (target.weightClass) {
            case LIGHT -> {
                // Item flies to player
                if (target.entity instanceof ItemEntity item) {
                    applyVelocity(item, dir.scale(force), false);
                }
            }
            case MEDIUM -> {
                // Both move toward each other – split force evenly
                applyVelocity(target.entity, dir.scale(force * 0.5), true);
                applyVelocity(player, dir.scale(-1).scale(force * 0.5), true);
            }
            case HEAVY -> {
                // Player pulled toward block
                Vec3 toBlock = target.position.subtract(playerPos).normalize();
                applyVelocity(player, toBlock.scale(force), true);
            }
        }
    }

    // ── Steel Push ────────────────────────────────────────────────────────────

    /**
     * Execute a Steel Push from the server on the given target source.
     */
    public static void executePush(ServerPlayer player, MetalSource target, ServerLevel level) {
        Vec3 playerPos = player.position().add(0, player.getEyeHeight(), 0);
        Vec3 fromPlayer = target.position.subtract(playerPos);
        double dist     = fromPlayer.length();
        if (dist < 0.001) return;
        Vec3 dir        = fromPlayer.normalize();
        double force    = computeForce(dist);

        switch (target.weightClass) {
            case LIGHT -> {
                // Item becomes a projectile
                if (target.entity instanceof ItemEntity item) {
                    launchProjectile(player, item, dir.scale(force), level);
                }
            }
            case MEDIUM -> {
                // Both pushed apart – split force
                applyVelocity(target.entity, dir.scale(force * 0.5), true);
                applyVelocity(player, dir.scale(-1).scale(force * 0.5), true);
            }
            case HEAVY -> {
                if (target.blockPos != null && isBelowPlayer(player, target.blockPos)) {
                    // Steeljump: block is beneath the player – launch upward
                    double jumpForce = MistbornConfig.STEELJUMP_FORCE.get();
                    Vec3 current = player.getDeltaMovement();
                    player.setDeltaMovement(current.x, jumpForce, current.z);
                    player.resetFallDistance();
                } else {
                    // Block is beside or above – push the player directly away from it
                    applyVelocity(player, dir.scale(-1).scale(force), true);
                }
            }
        }
    }

    /**
     * Returns true if the block is beneath the player within the configured angle threshold.
     */
    private static boolean isBelowPlayer(Player player, BlockPos blockPos) {
        Vec3 playerFeet = player.position();
        Vec3 blockCentre = Vec3.atCenterOf(blockPos);
        Vec3 toBlock = blockCentre.subtract(playerFeet);
        if (toBlock.length() < 0.001) return false;
        // Angle between straight-down (0,-1,0) and vector to block
        Vec3 down = new Vec3(0, -1, 0);
        double dot = down.dot(toBlock.normalize());
        double angleDeg = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, dot))));
        return angleDeg <= MistbornConfig.STEELJUMP_ANGLE.get();
    }

    /**
     * Launches an item entity as a Steel-pushed projectile.
     */
    private static void launchProjectile(ServerPlayer pusher, ItemEntity item,
                                         Vec3 velocity, ServerLevel level) {
        // Mark the item as a projectile
        item.setData(ModAttachments.STEEL_PROJECTILE.get(), true);
        item.setDeltaMovement(velocity);
        item.setNoPickUpDelay();

        // Schedule damage check via the PowerHandler's projectile tick system
        // (PowerHandler checks STEEL_PROJECTILE each tick and handles collision)
    }

    // ── Velocity helpers ──────────────────────────────────────────────────────

    private static void applyVelocity(Entity entity, Vec3 delta, boolean additive) {
        if (additive) {
            Vec3 cur = entity.getDeltaMovement();
            entity.setDeltaMovement(cur.add(delta));
        } else {
            entity.setDeltaMovement(delta);
        }
        entity.hurtMarked = true; // force client-side position update
        if (entity instanceof ServerPlayer sp) {
            sp.connection.send(new net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket(sp));
        }
    }

    private IronSteelHandler() {}
}
