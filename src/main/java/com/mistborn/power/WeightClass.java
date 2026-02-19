package com.mistborn.power;

/**
 * Weight classes used by the Iron/Steel push-pull system.
 *
 * <ul>
 *   <li>LIGHT  – loose item entities (ingots, nuggets, dropped metal tools/armour)</li>
 *   <li>MEDIUM – players or mobs wearing metal armour / holding metal items</li>
 *   <li>HEAVY  – placed metal blocks (iron blocks, gold blocks, rails, anvils, cauldrons)</li>
 * </ul>
 *
 * Pewter rule: any entity that is currently burning Pewter is treated as one tier heavier.
 */
public enum WeightClass {
    LIGHT, MEDIUM, HEAVY;

    /**
     * Returns the weight class elevated by one tier (HEAVY stays HEAVY).
     */
    public WeightClass elevated() {
        return switch (this) {
            case LIGHT   -> MEDIUM;
            case MEDIUM  -> HEAVY;
            case HEAVY   -> HEAVY;
        };
    }
}
