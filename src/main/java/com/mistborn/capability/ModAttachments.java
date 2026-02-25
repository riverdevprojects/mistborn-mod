package com.mistborn.capability;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

import static com.mistborn.MistbornMod.MODID;

/**
 * Registers NeoForge IAttachmentType entries for the Mistborn mod.
 */
public class ModAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, MODID);

    /**
     * The main Allomantic data attachment, persisted to NBT and synced to the client
     * via {@link com.mistborn.network.SyncAllomanticDataPacket}.
     */
    public static final Supplier<AttachmentType<AllomanticData>> ALLOMANTIC_DATA =
            ATTACHMENT_TYPES.register("allomantic_data", () ->
                    AttachmentType.serializable(AllomanticData::new).build());

    /**
     * Tracks whether an item entity is currently acting as a Steel-pushed projectile.
     * Not persisted – resets on chunk reload (fine for transient physics objects).
     */
    public static final Supplier<AttachmentType<Boolean>> STEEL_PROJECTILE =
            ATTACHMENT_TYPES.register("steel_projectile", () ->
                    AttachmentType.builder(() -> false).build());

    /**
     * Marks an iron-ingot {@link net.minecraft.world.entity.item.ItemEntity} as having
     * landed on the ground and being available as a Steel-push anchor.
     *
     * <p>When true, the item is treated as {@link com.mistborn.power.WeightClass#HEAVY}
     * by the Iron/Steel handler.  If the player pushes from too far to the side, the
     * ingot slides along the ground instead of anchoring the player.</p>
     *
     * <p>Not persisted – clears on chunk reload, which is fine for a transient physics state.</p>
     */
    public static final Supplier<AttachmentType<Boolean>> GROUNDED_INGOT =
            ATTACHMENT_TYPES.register("grounded_ingot", () ->
                    AttachmentType.builder(() -> false).build());

    /**
     * Stores the pending Brass-linger countdown for a mob (ticks until AI is restored).
     * 0 means no lingering effect.
     */
    public static final Supplier<AttachmentType<Integer>> BRASS_LINGER =
            ATTACHMENT_TYPES.register("brass_linger", () ->
                    AttachmentType.builder(() -> 0).build());

    /**
     * Stores the pending Zinc-linger countdown for a mob.
     */
    public static final Supplier<AttachmentType<Integer>> ZINC_LINGER =
            ATTACHMENT_TYPES.register("zinc_linger", () ->
                    AttachmentType.builder(() -> 0).build());

    private ModAttachments() {}
}
