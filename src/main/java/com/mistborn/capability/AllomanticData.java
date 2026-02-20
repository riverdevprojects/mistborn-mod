package com.mistborn.capability;

import com.mistborn.power.AllomanticMetal;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.neoforged.neoforge.common.util.INBTSerializable;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Persistent player data tracking Allomantic state.
 * Stored via NeoForge's IAttachmentType system and serialised to NBT.
 *
 * <p>Two related but separate concepts:</p>
 * <ul>
 *   <li>{@code currentlyBurning} – the metal currently <em>selected</em> (shown in HUD).
 *       For the Iron/Steel group this is always {@link AllomanticMetal#IRON}.</li>
 *   <li>{@code isBurningActive} – whether the player has toggled burning ON with F.
 *       Effects only apply when this is {@code true}.</li>
 * </ul>
 */
public class AllomanticData implements INBTSerializable<CompoundTag> {

    /** Metals this player has Misting access to. */
    private final Set<AllomanticMetal> unlockedMetals = EnumSet.noneOf(AllomanticMetal.class);

    /** Current reserve of each metal, 0.0 – 100.0. */
    private final Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);

    /**
     * The metal currently selected (may or may not be actively burning).
     * {@code null} if nothing has been selected yet.
     * For the Iron/Steel group, this is always {@link AllomanticMetal#IRON}.
     */
    private AllomanticMetal currentlyBurning = null;

    /**
     * Whether the F-toggle is on. Effects drain reserves and apply only when true.
     */
    private boolean isBurningActive = false;

    /**
     * Dirty flag – set whenever data changes so the sync packet is only
     * dispatched when needed.
     */
    private transient boolean dirty = false;

    // ── Unlocked metals ──────────────────────────────────────────────────────

    public Set<AllomanticMetal> getUnlockedMetals() {
        return unlockedMetals;
    }

    public boolean isUnlocked(AllomanticMetal metal) {
        return unlockedMetals.contains(metal);
    }

    public void unlockMetal(AllomanticMetal metal) {
        if (unlockedMetals.add(metal)) {
            dirty = true;
        }
    }

    // ── Reserves ─────────────────────────────────────────────────────────────

    public float getReserve(AllomanticMetal metal) {
        return reserves.getOrDefault(metal, 0f);
    }

    /**
     * Add {@code amount} to the reserve for {@code metal}, capped at 100.
     */
    public void addReserve(AllomanticMetal metal, float amount) {
        float current = getReserve(metal);
        float newVal = Math.min(100f, current + amount);
        reserves.put(metal, newVal);
        dirty = true;
    }

    /**
     * Drain {@code amount} from the reserve.  Returns the resulting value.
     * Clamps to 0; does not go negative.
     */
    public float drainReserve(AllomanticMetal metal, float amount) {
        float current = getReserve(metal);
        float newVal = Math.max(0f, current - amount);
        reserves.put(metal, newVal);
        dirty = true;
        return newVal;
    }

    // ── Selected metal ───────────────────────────────────────────────────────

    /**
     * Returns the currently selected metal, or {@code null} if none selected.
     * For the Iron/Steel group this is always {@link AllomanticMetal#IRON}.
     */
    public AllomanticMetal getCurrentlyBurning() {
        return currentlyBurning;
    }

    /**
     * Sets the selected metal without changing the burn-active toggle.
     * Pass {@code null} to clear the selection entirely.
     */
    public void setCurrentlyBurning(AllomanticMetal metal) {
        if (currentlyBurning != metal) {
            currentlyBurning = metal;
            dirty = true;
        }
    }

    // ── Burn-active toggle ───────────────────────────────────────────────────

    /** Returns true if the F-toggle is on (burning is active). */
    public boolean isBurningActive() {
        return isBurningActive;
    }

    /**
     * Sets the burn-active state (the F-toggle).
     * Does nothing if there is no selected metal.
     */
    public void setBurningActive(boolean active) {
        if (isBurningActive != active) {
            isBurningActive = active;
            dirty = true;
        }
    }

    // ── Derived convenience flags ─────────────────────────────────────────────

    /**
     * True when Pewter is selected AND burning is active.
     * Used by {@link com.mistborn.power.IronSteelHandler} for weight-class elevation.
     */
    public boolean isPewterBurning() {
        return currentlyBurning == AllomanticMetal.PEWTER && isBurningActive;
    }

    /**
     * True when Copper is selected AND burning is active.
     * Used to suppress Bronze pulse detection.
     */
    public boolean isCopperActive() {
        return currentlyBurning == AllomanticMetal.COPPER && isBurningActive;
    }

    // ── Dirty flag ───────────────────────────────────────────────────────────

    public boolean isDirty() {
        return dirty;
    }

    public void clearDirty() {
        dirty = false;
    }

    public void markDirty() {
        dirty = true;
    }

    // ── NBT serialisation ─────────────────────────────────────────────────────

    @Override
    public CompoundTag serializeNBT(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();

        // Unlocked metals
        ListTag unlockedList = new ListTag();
        for (AllomanticMetal m : unlockedMetals) {
            unlockedList.add(StringTag.valueOf(m.name()));
        }
        tag.put("unlockedMetals", unlockedList);

        // Reserves
        CompoundTag reservesTag = new CompoundTag();
        for (Map.Entry<AllomanticMetal, Float> entry : reserves.entrySet()) {
            reservesTag.putFloat(entry.getKey().name(), entry.getValue());
        }
        tag.put("reserves", reservesTag);

        // Selected metal
        if (currentlyBurning != null) {
            tag.putString("currentlyBurning", currentlyBurning.name());
        }

        // Burn-active toggle
        tag.putBoolean("isBurningActive", isBurningActive);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        unlockedMetals.clear();
        reserves.clear();
        currentlyBurning = null;
        isBurningActive  = false;

        // Unlocked metals
        if (tag.contains("unlockedMetals", Tag.TAG_LIST)) {
            ListTag list = tag.getList("unlockedMetals", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                AllomanticMetal m = AllomanticMetal.fromName(list.getString(i));
                if (m != null) unlockedMetals.add(m);
            }
        }

        // Reserves
        if (tag.contains("reserves", Tag.TAG_COMPOUND)) {
            CompoundTag reservesTag = tag.getCompound("reserves");
            for (AllomanticMetal m : AllomanticMetal.values()) {
                if (reservesTag.contains(m.name())) {
                    reserves.put(m, reservesTag.getFloat(m.name()));
                }
            }
        }

        // Selected metal
        if (tag.contains("currentlyBurning", Tag.TAG_STRING)) {
            AllomanticMetal m = AllomanticMetal.fromName(tag.getString("currentlyBurning"));
            if (m != null) currentlyBurning = m;
        }

        // Burn-active toggle
        if (tag.contains("isBurningActive", Tag.TAG_BYTE)) {
            isBurningActive = tag.getBoolean("isBurningActive");
        }
    }

    /**
     * Copy all state from {@code other} into this instance (used when applying
     * a sync packet on the client).
     */
    public void copyFrom(AllomanticData other) {
        unlockedMetals.clear();
        unlockedMetals.addAll(other.unlockedMetals);

        reserves.clear();
        reserves.putAll(other.reserves);

        currentlyBurning = other.currentlyBurning;
        isBurningActive  = other.isBurningActive;

        dirty = false;
    }
}
