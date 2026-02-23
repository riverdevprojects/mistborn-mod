package com.mistborn.block.entity;

import com.mistborn.block.menu.VialFillerMenu;
import com.mistborn.item.MetalFlakeItem;
import com.mistborn.item.VialItem;
import com.mistborn.power.AllomanticMetal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Block entity for the Vial Filler block.
 *
 * <h2>Slot layout</h2>
 * <ul>
 *   <li>Slot 0 – Vial (input/output; accumulates metal contents over multiple brews).</li>
 *   <li>Slot 1 – Fuel (coal or any item with a burn time).</li>
 *   <li>Slot 2 – Metal Flakes (ingredient; ALL flakes are consumed at once on completion).</li>
 * </ul>
 *
 * <h2>Timing</h2>
 * <ul>
 *   <li>{@link #MAX_BREW_TIME} ticks per brew cycle.</li>
 *   <li>Fuel (coal = 1600 ticks) burns continuously; brew progresses while fuel > 0.</li>
 * </ul>
 */
public class VialFillerBlockEntity extends BlockEntity implements WorldlyContainer {

    // ── Slot indices ──────────────────────────────────────────────────────────

    public static final int SLOT_VIAL       = 0;
    public static final int SLOT_FUEL       = 1;
    public static final int SLOT_INGREDIENT = 2;
    public static final int NUM_SLOTS       = 3;

    // ── Timing ────────────────────────────────────────────────────────────────

    public static final int MAX_BREW_TIME = 200; // ticks (10 s)

    // ── ContainerData indices ─────────────────────────────────────────────────

    /** Index into {@link #data} for remaining fuel ticks. */
    public static final int DATA_FUEL_TIME     = 0;
    /** Index into {@link #data} for max fuel time of current fuel item. */
    public static final int DATA_MAX_FUEL_TIME = 1;
    /** Index into {@link #data} for current brew progress. */
    public static final int DATA_BREW_TIME     = 2;
    /** Index into {@link #data} for max brew time. */
    public static final int DATA_MAX_BREW_TIME = 3;

    // ── Fields ────────────────────────────────────────────────────────────────

    private final NonNullList<ItemStack> items = NonNullList.withSize(NUM_SLOTS, ItemStack.EMPTY);

    private int fuelTime    = 0; // remaining ticks of fuel
    private int maxFuelTime = 0; // total ticks of the last fuel item consumed
    private int brewTime    = 0; // ticks elapsed in the current brew

    /** Exposed to the screen/menu via ContainerData. */
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_FUEL_TIME     -> fuelTime;
                case DATA_MAX_FUEL_TIME -> maxFuelTime;
                case DATA_BREW_TIME     -> brewTime;
                case DATA_MAX_BREW_TIME -> MAX_BREW_TIME;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_FUEL_TIME     -> fuelTime    = value;
                case DATA_MAX_FUEL_TIME -> maxFuelTime = value;
                case DATA_BREW_TIME     -> brewTime    = value;
            }
        }

        @Override
        public int getCount() { return 4; }
    };

    // ── Constructor ───────────────────────────────────────────────────────────

    public VialFillerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.VIAL_FILLER.get(), pos, state);
    }

    // ── Server tick ───────────────────────────────────────────────────────────

    public static void serverTick(Level level, BlockPos pos, BlockState state,
                                  VialFillerBlockEntity be) {
        be.tick(level);
    }

    private void tick(Level level) {
        ItemStack vialStack       = items.get(SLOT_VIAL);
        ItemStack fuelStack       = items.get(SLOT_FUEL);
        ItemStack ingredientStack = items.get(SLOT_INGREDIENT);

        boolean hasVial       = !vialStack.isEmpty() && vialStack.getItem() instanceof VialItem;
        boolean hasIngredient = !ingredientStack.isEmpty()
                && ingredientStack.getItem() instanceof MetalFlakeItem;
        boolean canBrew       = hasVial && hasIngredient;

        // Consume fuel when there's something to brew and fuel is needed
        if (fuelTime <= 0 && canBrew && !fuelStack.isEmpty()) {
            int burnTime = level.fuelValues().burnDuration(fuelStack);
            if (burnTime > 0) {
                maxFuelTime = burnTime;
                fuelTime    = burnTime;
                if (!fuelStack.hasCraftingRemainingItem()) {
                    fuelStack.shrink(1);
                } else {
                    items.set(SLOT_FUEL, fuelStack.getCraftingRemainingItem());
                }
                setChanged();
            }
        }

        // Progress the brew while fuel is available and all ingredients are present
        if (canBrew && fuelTime > 0) {
            fuelTime--;
            brewTime++;
            setChanged();

            if (brewTime >= MAX_BREW_TIME) {
                // Complete the brew: consume ALL flakes from the ingredient slot and add to vial
                completeBrew(vialStack, ingredientStack);
                brewTime = 0;
                setChanged();
            }
        } else if (!canBrew) {
            // Reset brew progress if ingredients missing
            if (brewTime > 0) {
                brewTime = 0;
                setChanged();
            }
        }
    }

    /** Consumes all flakes in the ingredient slot and adds them into the vial. */
    private void completeBrew(ItemStack vialStack, ItemStack ingredientStack) {
        if (!(ingredientStack.getItem() instanceof MetalFlakeItem flakeItem)) return;

        AllomanticMetal metal = flakeItem.getMetal();
        int count = ingredientStack.getCount();

        // Add all flakes at once into the vial
        VialItem.addMetal(vialStack, metal, count);

        // Consume the entire ingredient stack
        items.set(SLOT_INGREDIENT, ItemStack.EMPTY);
    }

    // ── Drop contents on break ────────────────────────────────────────────────

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
    }

    // ── Menu factory ──────────────────────────────────────────────────────────

    public AbstractContainerMenu createMenu(int windowId, Inventory playerInv) {
        return new VialFillerMenu(windowId, playerInv, this, data);
    }

    // ── WorldlyContainer (for hoppers) ────────────────────────────────────────

    @Override
    public int[] getSlotsForFace(net.minecraft.core.Direction side) {
        return new int[]{SLOT_VIAL, SLOT_FUEL, SLOT_INGREDIENT};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack stack, @Nullable net.minecraft.core.Direction dir) {
        return switch (slot) {
            case SLOT_VIAL       -> stack.getItem() instanceof VialItem;
            case SLOT_FUEL       -> this.level != null && this.level.fuelValues().burnDuration(stack) > 0;
            case SLOT_INGREDIENT -> stack.getItem() instanceof MetalFlakeItem;
            default -> false;
        };
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction dir) {
        return slot == SLOT_VIAL;
    }

    // ── Container ─────────────────────────────────────────────────────────────

    @Override
    public int getContainerSize() { return NUM_SLOTS; }

    @Override
    public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }

    @Override
    public ItemStack getItem(int slot) { return items.get(slot); }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        setChanged();
        return ContainerHelper.removeItem(items, slot, amount);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        setChanged();
        return ContainerHelper.takeItem(items, slot);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        items.set(slot, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        return level != null
                && level.getBlockEntity(worldPosition) == this
                && player.distanceToSqr(worldPosition.getX() + 0.5,
                                        worldPosition.getY() + 0.5,
                                        worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() { items.clear(); }

    // ── NBT ───────────────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        ContainerHelper.saveAllItems(tag, items, provider);
        tag.putInt("FuelTime",    fuelTime);
        tag.putInt("MaxFuelTime", maxFuelTime);
        tag.putInt("BrewTime",    brewTime);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        ContainerHelper.loadAllItems(tag, items, provider);
        fuelTime    = tag.getInt("FuelTime");
        maxFuelTime = tag.getInt("MaxFuelTime");
        brewTime    = tag.getInt("BrewTime");
    }

    // ── Getters for GUI ───────────────────────────────────────────────────────

    public ContainerData getContainerData() { return data; }

    public int getFuelTime()    { return fuelTime; }
    public int getMaxFuelTime() { return maxFuelTime; }
    public int getBrewTime()    { return brewTime; }
}
