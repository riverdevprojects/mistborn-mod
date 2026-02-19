package com.mistborn.power;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Represents a single detectable metal source in the world, used by the
 * Iron/Steel push-pull system.
 *
 * <p>A source is either an entity (item on the ground, armoured mob/player)
 * or a block position (metal block in the world).  Exactly one of
 * {@link #entity} and {@link #blockPos} is non-null.</p>
 */
public final class MetalSource {

    /** The entity, if this source is entity-based; null otherwise. */
    public final Entity entity;

    /** The block position, if this source is block-based; null otherwise. */
    public final BlockPos blockPos;

    /** The weight class of this source. */
    public final WeightClass weightClass;

    /** World-space centre position used for distance / crosshair targeting. */
    public final Vec3 position;

    private MetalSource(Entity entity, BlockPos blockPos, WeightClass weightClass, Vec3 position) {
        this.entity      = entity;
        this.blockPos    = blockPos;
        this.weightClass = weightClass;
        this.position    = position;
    }

    /** Constructs a source backed by a living entity or item entity. */
    public static MetalSource ofEntity(Entity entity, WeightClass weightClass) {
        return new MetalSource(entity, null, weightClass, entity.position().add(0, entity.getBbHeight() / 2.0, 0));
    }

    /** Constructs a source backed by a world block. */
    public static MetalSource ofBlock(BlockPos pos) {
        Vec3 centre = Vec3.atCenterOf(pos);
        return new MetalSource(null, pos, WeightClass.HEAVY, centre);
    }

    public boolean isEntityBased() { return entity != null; }
    public boolean isBlockBased()  { return blockPos != null; }
}
