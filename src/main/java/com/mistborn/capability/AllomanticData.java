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
 */
public class AllomanticData implements INBTSerializable<CompoundTag> {

    /** Metals this player has Misting access to. */
    private final Set<AllomanticMetal> unlockedMetals = EnumSet.noneOf(AllomanticMetal.class);

    /** Current reserve of each metal, 0.0 – 100.0. */
    private final Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);

    /** The metal currently being burned, or null if none. */
    private AllomanticMetal currentlyBurning = null;

    /** Convenience flag set when Pewter is burning (affects weight-class logic). */
    private boolean isPewterBurning = false;

    /** Set while Copper is being burned; suppresses Bronze detection. */
    private boolean isCopperActive = false;

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

    // ── Burning state ────────────────────────────────────────────────────────

    public AllomanticMetal getCurrentlyBurning() {
        return currentlyBurning;
    }

    public void setCurrentlyBurning(AllomanticMetal metal) {
        if (currentlyBurning != metal) {
            currentlyBurning = metal;
            isPewterBurning  = (metal == AllomanticMetal.PEWTER);
            isCopperActive   = (metal == AllomanticMetal.COPPER);
            dirty = true;
        }
    }

    public boolean isPewterBurning() {
        return isPewterBurning;
    }

    public boolean isCopperActive() {
        return isCopperActive;
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

        // Currently burning
        if (currentlyBurning != null) {
            tag.putString("currentlyBurning", currentlyBurning.name());
        }

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        unlockedMetals.clear();
        reserves.clear();
        currentlyBurning = null;
        isPewterBurning  = false;
        isCopperActive   = false;

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

        // Currently burning
        if (tag.contains("currentlyBurning", Tag.TAG_STRING)) {
            AllomanticMetal m = AllomanticMetal.fromName(tag.getString("currentlyBurning"));
            if (m != null) {
                currentlyBurning = m;
                isPewterBurning  = (m == AllomanticMetal.PEWTER);
                isCopperActive   = (m == AllomanticMetal.COPPER);
            }
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
        isPewterBurning  = other.isPewterBurning;
        isCopperActive   = other.isCopperActive;

        dirty = false;
    }
}
