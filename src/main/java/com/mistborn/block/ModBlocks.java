package com.mistborn.block;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.mistborn.MistbornMod.MODID;

/**
 * Registry for all Mistborn blocks and their corresponding block items.
 */
public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MODID);

    public static final DeferredRegister.Items BLOCK_ITEMS =
            DeferredRegister.createItems(MODID);

    // ── Vial Filler ───────────────────────────────────────────────────────────

    public static final DeferredBlock<VialFillerBlock> VIAL_FILLER =
            BLOCKS.register("vial_filler", () -> new VialFillerBlock(
                    BlockBehaviour.Properties.of()
                            .mapColor(MapColor.STONE)
                            .strength(2.5f)
                            .sound(SoundType.STONE)
                            .requiresCorrectToolForDrops()
            ));

    public static final DeferredItem<BlockItem> VIAL_FILLER_ITEM =
            BLOCK_ITEMS.register("vial_filler", () ->
                    new BlockItem(VIAL_FILLER.get(), new Item.Properties()));

    private ModBlocks() {}
}
