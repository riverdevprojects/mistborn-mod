package com.mistborn.power;

/**
 * Enum representing each of the eight basic Allomantic metals.
 * Each metal has a display name and an ARGB colour used for HUD elements
 * and line-rendering on the client.
 */
public enum AllomanticMetal {

    IRON("Iron",    0xFF5B8DD9),   // blue
    STEEL("Steel",  0xFFD95B5B),   // red
    TIN("Tin",      0xFFBDBDBD),   // light gray
    PEWTER("Pewter",0xFFE08030),   // orange
    COPPER("Copper",0xFFB87333),   // copper / brown
    BRONZE("Bronze",0xFF8B6914),   // dark gold
    ZINC("Zinc",    0xFFE8E820),   // yellow
    BRASS("Brass",  0xFFFFD700);   // bright gold

    private final String displayName;
    /** Packed ARGB colour (alpha in highest byte). */
    private final int colour;

    AllomanticMetal(String displayName, int colour) {
        this.displayName = displayName;
        this.colour = colour;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getColour() {
        return colour;
    }

    /** Convenience: red component 0-1. */
    public float getRed()   { return ((colour >> 16) & 0xFF) / 255f; }
    /** Convenience: green component 0-1. */
    public float getGreen() { return ((colour >>  8) & 0xFF) / 255f; }
    /** Convenience: blue component 0-1. */
    public float getBlue()  { return ( colour        & 0xFF) / 255f; }

    /**
     * Case-insensitive lookup by name; returns null if not found.
     */
    public static AllomanticMetal fromName(String name) {
        if (name == null) return null;
        for (AllomanticMetal m : values()) {
            if (m.name().equalsIgnoreCase(name)) return m;
        }
        return null;
    }
}
