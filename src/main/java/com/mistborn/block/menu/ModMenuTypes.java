package com.mistborn.block.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import static com.mistborn.MistbornMod.MODID;

/**
 * Registry for all Mistborn menu (container) types.
 */
public class ModMenuTypes {

    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<VialFillerMenu>> VIAL_FILLER =
            MENU_TYPES.register("vial_filler",
                    () -> IMenuTypeExtension.create((windowId, inv, data) ->
                            new VialFillerMenu(windowId, inv)));

    private ModMenuTypes() {}
}
