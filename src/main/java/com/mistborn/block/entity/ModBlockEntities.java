package com.mistborn.block.entity;

import com.mistborn.block.ModBlocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.Registries;

import static com.mistborn.MistbornMod.MODID;

/**
 * Registry for all Mistborn block entity types.
 */
public class ModBlockEntities {

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<VialFillerBlockEntity>> VIAL_FILLER =
            BLOCK_ENTITY_TYPES.register("vial_filler", () ->
                    BlockEntityType.Builder.of(VialFillerBlockEntity::new,
                                    ModBlocks.VIAL_FILLER.get())
                            .build(null));

    private ModBlockEntities() {}
}
