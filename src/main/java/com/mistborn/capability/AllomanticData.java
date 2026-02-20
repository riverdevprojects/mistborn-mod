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
 *   <li>{@code setMetals} – metals currently <em>set</em> (queued) via the radial wheel.
 *       A metal is "set" when the player selects it in the radial; selecting again un-sets it.
 *       Multiple metals can be set simultaneously.</li>
 *   <li>{@code activeMetals} – metals currently <em>burning</em> (F-toggle ON).
 *       Pressing F activates all set metals at once; pressing F again deactivates all.</li>
 * </ul>
 */
public class AllomanticData implements INBTSerializable<CompoundTag> {

    /** Metals this player has Misting access to. */
    private final Set<AllomanticMetal> unlockedMetals = EnumSet.noneOf(AllomanticMetal.class);

    /** Current reserve of each metal, 0.0 – 100.0. */
    private final Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);

    /**
     * Metals currently "set" (queued) via the radial wheel.
     * Multiple metals can be set simultaneously.
     */
    private final Set<AllomanticMetal> setMetals = EnumSet.noneOf(AllomanticMetal.class);

    /**
     * Metals currently actively burning (F-toggle is ON for these metals).
     * Always a subset of setMetals.
     */
    private final Set<AllomanticMetal> activeMetals = EnumSet.noneOf(AllomanticMetal.class);

    /**
     * Transient cooldown (ticks) set when the player iron-pulls toward a HEAVY block.
     * While > 0, fall/impact damage is negated. Not serialized – intentionally ephemeral.
     */
    private transient int ironPullBlockCooldown = 0;

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

    // ── Set metals (queued via radial wheel) ──────────────────────────────────

    public Set<AllomanticMetal> getSetMetals() {
        return setMetals;
    }

    public boolean isMetalSet(AllomanticMetal metal) {
        return setMetals.contains(metal);
    }

    public void addToSet(AllomanticMetal metal) {
        if (setMetals.add(metal)) dirty = true;
    }

    public void removeFromSet(AllomanticMetal metal) {
        if (setMetals.remove(metal)) dirty = true;
    }

    // ── Active metals (burning – F-toggle) ───────────────────────────────────

    public Set<AllomanticMetal> getActiveMetals() {
        return activeMetals;
    }

    public boolean isMetalActive(AllomanticMetal metal) {
        return activeMetals.contains(metal);
    }

    public void addToActive(AllomanticMetal metal) {
        if (activeMetals.add(metal)) dirty = true;
    }

    public void removeFromActive(AllomanticMetal metal) {
        if (activeMetals.remove(metal)) dirty = true;
    }

    public void clearActiveMetals() {
        if (!activeMetals.isEmpty()) {
            activeMetals.clear();
            dirty = true;
        }
    }

    public void setActiveMetals(Set<AllomanticMetal> metals) {
        activeMetals.clear();
        activeMetals.addAll(metals);
        dirty = true;
    }

    // ── Iron pull block cooldown ──────────────────────────────────────────────

    /** Set when the player iron-pulls toward a HEAVY block; prevents landing damage. */
    public void setIronPullBlockCooldown(int ticks) {
        ironPullBlockCooldown = ticks;
    }

    public int getIronPullBlockCooldown() {
        return ironPullBlockCooldown;
    }

    /** Decrement the cooldown by one tick (called from PowerHandler each tick). */
    public void tickIronPullBlockCooldown() {
        if (ironPullBlockCooldown > 0) ironPullBlockCooldown--;
    }

    // ── Backward-compatibility shims (used by legacy sync/packet paths) ───────

    /**
     * Returns the first active metal by enum ordinal, or the first set metal, or null.
     * Kept for code paths that only care about a single "current" metal (e.g. packet handlers
     * that haven't been fully updated yet).
     */
    public AllomanticMetal getCurrentlyBurning() {
        if (!activeMetals.isEmpty()) return activeMetals.iterator().next();
        if (!setMetals.isEmpty()) return setMetals.iterator().next();
        return null;
    }

    /**
     * Clears setMetals and sets it to just this metal (and STEEL if metal is IRON).
     * Kept for backward compatibility with the old single-selection model.
     * Pass {@code null} to clear everything.
     */
    public void setCurrentlyBurning(AllomanticMetal metal) {
        setMetals.clear();
        activeMetals.clear();
        if (metal != null) {
            setMetals.add(metal);
            if (metal == AllomanticMetal.IRON) {
                setMetals.add(AllomanticMetal.STEEL);
            }
        }
        dirty = true;
    }

    /**
     * Returns true if any metal is actively burning (F-toggle is on for at least one metal).
     */
    public boolean isBurningActive() {
        return !activeMetals.isEmpty();
    }

    /**
     * If {@code active} is true, activates all set metals. If false, clears all active metals.
     * Kept for backward compatibility with the old single-burn-toggle model.
     */
    public void setBurningActive(boolean active) {
        if (active) {
            if (!activeMetals.equals(setMetals)) {
                activeMetals.clear();
                activeMetals.addAll(setMetals);
                dirty = true;
            }
        } else {
            if (!activeMetals.isEmpty()) {
                activeMetals.clear();
                dirty = true;
            }
        }
    }

    // ── Derived convenience flags ─────────────────────────────────────────────

    /**
     * True when Pewter is actively burning.
     * Used by {@link com.mistborn.power.IronSteelHandler} for weight-class elevation.
     */
    public boolean isPewterBurning() {
        return activeMetals.contains(AllomanticMetal.PEWTER);
    }

    /**
     * True when Copper is actively burning.
     * Used to suppress Bronze pulse detection.
     */
    public boolean isCopperActive() {
        return activeMetals.contains(AllomanticMetal.COPPER);
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

        // Set metals (queued)
        ListTag setList = new ListTag();
        for (AllomanticMetal m : setMetals) {
            setList.add(StringTag.valueOf(m.name()));
        }
        tag.put("setMetals", setList);

        // Active metals (burning)
        ListTag activeList = new ListTag();
        for (AllomanticMetal m : activeMetals) {
            activeList.add(StringTag.valueOf(m.name()));
        }
        tag.put("activeMetals", activeList);

        return tag;
    }

    @Override
    public void deserializeNBT(HolderLookup.Provider provider, CompoundTag tag) {
        unlockedMetals.clear();
        reserves.clear();
        setMetals.clear();
        activeMetals.clear();

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

        // Set metals – new format
        if (tag.contains("setMetals", Tag.TAG_LIST)) {
            ListTag list = tag.getList("setMetals", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                AllomanticMetal m = AllomanticMetal.fromName(list.getString(i));
                if (m != null) setMetals.add(m);
            }
        } else if (tag.contains("currentlyBurning", Tag.TAG_STRING)) {
            // Backward compat: migrate old single-selection save data
            AllomanticMetal m = AllomanticMetal.fromName(tag.getString("currentlyBurning"));
            if (m != null) {
                setMetals.add(m);
                if (m == AllomanticMetal.IRON) setMetals.add(AllomanticMetal.STEEL);
            }
        }

        // Active metals – new format
        if (tag.contains("activeMetals", Tag.TAG_LIST)) {
            ListTag list = tag.getList("activeMetals", Tag.TAG_STRING);
            for (int i = 0; i < list.size(); i++) {
                AllomanticMetal m = AllomanticMetal.fromName(list.getString(i));
                if (m != null) activeMetals.add(m);
            }
        } else if (tag.contains("isBurningActive", Tag.TAG_BYTE)) {
            // Backward compat: migrate old burn-active flag
            if (tag.getBoolean("isBurningActive")) {
                activeMetals.addAll(setMetals);
            }
        }
    }

    /**
     * Copy all state from {@code other} into this instance (used when applying
     * a sync packet on the client, or during player clone/respawn).
     */
    public void copyFrom(AllomanticData other) {
        unlockedMetals.clear();
        unlockedMetals.addAll(other.unlockedMetals);

        reserves.clear();
        reserves.putAll(other.reserves);

        setMetals.clear();
        setMetals.addAll(other.setMetals);

        activeMetals.clear();
        activeMetals.addAll(other.activeMetals);

        // ironPullBlockCooldown is transient – not copied
        dirty = false;
    }
}
