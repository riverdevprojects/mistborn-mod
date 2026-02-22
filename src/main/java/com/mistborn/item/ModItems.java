package com.mistborn.item;

import com.mistborn.power.AllomanticMetal;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.EnumMap;
import java.util.Map;

import static com.mistborn.MistbornMod.MODID;


/**
 * Registry holder for all Mistborn items and the Mistborn creative tab.
 */
public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MODID);

    public static final DeferredRegister<CreativeModeTab> CREATIVE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ── Metal Flakes ──────────────────────────────────────────────────────────
    // Declared before MISTBORN_TAB to avoid illegal forward references.

    public static final DeferredItem<MetalFlakeItem> IRON_FLAKE =
            registerFlake(AllomanticMetal.IRON, "iron_flake");

    public static final DeferredItem<MetalFlakeItem> STEEL_FLAKE =
            registerFlake(AllomanticMetal.STEEL, "steel_flake");

    public static final DeferredItem<MetalFlakeItem> TIN_FLAKE =
            registerFlake(AllomanticMetal.TIN, "tin_flake");

    public static final DeferredItem<MetalFlakeItem> PEWTER_FLAKE =
            registerFlake(AllomanticMetal.PEWTER, "pewter_flake");

    public static final DeferredItem<MetalFlakeItem> COPPER_FLAKE =
            registerFlake(AllomanticMetal.COPPER, "copper_flake");

    public static final DeferredItem<MetalFlakeItem> BRONZE_FLAKE =
            registerFlake(AllomanticMetal.BRONZE, "bronze_flake");

    public static final DeferredItem<MetalFlakeItem> ZINC_FLAKE =
            registerFlake(AllomanticMetal.ZINC, "zinc_flake");

    public static final DeferredItem<MetalFlakeItem> BRASS_FLAKE =
            registerFlake(AllomanticMetal.BRASS, "brass_flake");

    // ── Vial ──────────────────────────────────────────────────────────────────

    /** An empty (or filled) Allomantic vial. Stores metal flakes as NBT. */
    public static final DeferredItem<VialItem> VIAL =
            ITEMS.register("vial", () -> new VialItem(new Item.Properties().stacksTo(16)));

    // ── Lookup map ────────────────────────────────────────────────────────────

    private static final Map<AllomanticMetal, DeferredItem<MetalFlakeItem>> BY_METAL =
            new EnumMap<>(AllomanticMetal.class);

    static {
        BY_METAL.put(AllomanticMetal.IRON,   IRON_FLAKE);
        BY_METAL.put(AllomanticMetal.STEEL,  STEEL_FLAKE);
        BY_METAL.put(AllomanticMetal.TIN,    TIN_FLAKE);
        BY_METAL.put(AllomanticMetal.PEWTER, PEWTER_FLAKE);
        BY_METAL.put(AllomanticMetal.COPPER, COPPER_FLAKE);
        BY_METAL.put(AllomanticMetal.BRONZE, BRONZE_FLAKE);
        BY_METAL.put(AllomanticMetal.ZINC,   ZINC_FLAKE);
        BY_METAL.put(AllomanticMetal.BRASS,  BRASS_FLAKE);
    }

    // ── Creative tab (declared last to legally reference the flake fields above) ──

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MISTBORN_TAB =
            CREATIVE_TABS.register("mistborn_tab", () ->
                    CreativeModeTab.builder()
                            .title(Component.translatable("itemGroup.mistborn"))
                            .withTabsBefore(CreativeModeTabs.COMBAT)
                            .icon(() -> IRON_FLAKE.get().getDefaultInstance())
                            .displayItems((params, output) -> {
                                output.accept(VIAL.get());
                                output.accept(IRON_FLAKE.get());
                                output.accept(STEEL_FLAKE.get());
                                output.accept(TIN_FLAKE.get());
                                output.accept(PEWTER_FLAKE.get());
                                output.accept(COPPER_FLAKE.get());
                                output.accept(BRONZE_FLAKE.get());
                                output.accept(ZINC_FLAKE.get());
                                output.accept(BRASS_FLAKE.get());
                            })
                            .build());

    // ── Accessors ─────────────────────────────────────────────────────────────

    public static DeferredItem<MetalFlakeItem> getFlakeFor(AllomanticMetal metal) {
        return BY_METAL.get(metal);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static DeferredItem<MetalFlakeItem> registerFlake(AllomanticMetal metal, String id) {
        return ITEMS.register(id, () ->
                new MetalFlakeItem(metal, new Item.Properties().stacksTo(64)));
    }

    private ModItems() {}
}
