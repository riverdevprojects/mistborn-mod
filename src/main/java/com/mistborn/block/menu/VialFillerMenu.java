package com.mistborn.block.menu;

import com.mistborn.block.entity.VialFillerBlockEntity;
import com.mistborn.item.MetalFlakeItem;
import com.mistborn.item.VialItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.datamaps.builtin.NeoForgeDataMaps;

/**
 * Server-side menu (container) for the Vial Filler block.
 *
 * <h2>Slot layout on the GUI grid (176×166 background)</h2>
 * <pre>
 *  Slot 0 – Vial       centred top    (80, 17)
 *  Slot 1 – Fuel       bottom-left    (56, 53)
 *  Slot 2 – Ingredient bottom-right   (116, 53)
 *  Slots 3-29  – Player inventory (9×3) starting at (8, 84)
 *  Slots 30-38 – Player hotbar starting at (8, 142)
 * </pre>
 */
public class VialFillerMenu extends AbstractContainerMenu {

    private final Container container;
    private final ContainerData data;

    // ── Pixel positions of block-entity slots on the 176×166 GUI background ──
    private static final int VIAL_X       = 80;
    private static final int VIAL_Y       = 17;
    private static final int FUEL_X       = 56;
    private static final int FUEL_Y       = 53;
    private static final int INGREDIENT_X = 116;
    private static final int INGREDIENT_Y = 53;

    // ── Constructor (server side – real container) ─────────────────────────────

    public VialFillerMenu(int windowId, Inventory playerInv,
                          Container container, ContainerData data) {
        super(ModMenuTypes.VIAL_FILLER.get(), windowId);

        this.container = container;
        this.data      = data;

        checkContainerSize(container, VialFillerBlockEntity.NUM_SLOTS);
        checkContainerDataCount(data, 4);

        // ── Block-entity slots ──────────────────────────────────────────────

        // Slot 0 – Vial
        addSlot(new Slot(container, VialFillerBlockEntity.SLOT_VIAL, VIAL_X, VIAL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof VialItem;
            }
        });

        // Slot 1 – Fuel
        addSlot(new Slot(container, VialFillerBlockEntity.SLOT_FUEL, FUEL_X, FUEL_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem().builtInRegistryHolder().getData(NeoForgeDataMaps.FURNACE_FUELS) != null;
            }
        });

        // Slot 2 – Ingredient (metal flakes)
        addSlot(new Slot(container, VialFillerBlockEntity.SLOT_INGREDIENT, INGREDIENT_X, INGREDIENT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof MetalFlakeItem;
            }
        });

        // ── Player inventory (27 slots, 3 rows × 9) ────────────────────────

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInv, col + row * 9 + 9,
                        8 + col * 18, 84 + row * 18));
            }
        }

        // ── Player hotbar (9 slots) ────────────────────────────────────────

        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInv, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    // ── Constructor (client side – dummy container) ────────────────────────────

    public VialFillerMenu(int windowId, Inventory playerInv) {
        this(windowId, playerInv,
                new SimpleContainer(VialFillerBlockEntity.NUM_SLOTS),
                new SimpleContainerData(4));
    }

    // ── Shift-click quick-move ─────────────────────────────────────────────────

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack copy = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (!slot.hasItem()) return copy;

        ItemStack stack = slot.getItem();
        copy = stack.copy();

        // From block-entity slots → player inventory
        if (index < VialFillerBlockEntity.NUM_SLOTS) {
            if (!moveItemStackTo(stack, VialFillerBlockEntity.NUM_SLOTS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // From player inventory → appropriate block slot
            if (stack.getItem() instanceof VialItem) {
                if (!moveItemStackTo(stack, VialFillerBlockEntity.SLOT_VIAL, VialFillerBlockEntity.SLOT_VIAL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem() instanceof MetalFlakeItem) {
                if (!moveItemStackTo(stack, VialFillerBlockEntity.SLOT_INGREDIENT, VialFillerBlockEntity.SLOT_INGREDIENT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (stack.getItem().builtInRegistryHolder().getData(NeoForgeDataMaps.FURNACE_FUELS) != null) {
                if (!moveItemStackTo(stack, VialFillerBlockEntity.SLOT_FUEL, VialFillerBlockEntity.SLOT_FUEL + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    // ── ContainerData accessors (used by the screen) ──────────────────────────

    /** Returns fuel remaining as a fraction 0.0–1.0. */
    public float getFuelProgress() {
        int max = data.get(VialFillerBlockEntity.DATA_MAX_FUEL_TIME);
        if (max <= 0) return 0f;
        return (float) data.get(VialFillerBlockEntity.DATA_FUEL_TIME) / max;
    }

    /** Returns brew progress as a fraction 0.0–1.0. */
    public float getBrewProgress() {
        int max = data.get(VialFillerBlockEntity.DATA_MAX_BREW_TIME);
        if (max <= 0) return 0f;
        return (float) data.get(VialFillerBlockEntity.DATA_BREW_TIME) / max;
    }

    public boolean isFueled() {
        return data.get(VialFillerBlockEntity.DATA_FUEL_TIME) > 0;
    }
}
