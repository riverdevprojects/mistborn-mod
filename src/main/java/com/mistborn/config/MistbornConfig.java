package com.mistborn.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * All numeric constants for the Mistborn mod, tweakable via the NeoForge config system
 * without recompiling.  Registered as a COMMON config in MistbornMod.
 */
public class MistbornConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ── Burning ──────────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue BURN_RATE = BUILDER
            .comment("Reserve drained per server tick while a metal is burning (0.0–100.0).")
            .defineInRange("burnRate", 0.5, 0.01, 100.0);

    // ── Iron / Steel ─────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue IRON_STEEL_RANGE = BUILDER
            .comment("Radius in blocks within which Iron/Steel can detect metal sources.")
            .defineInRange("ironSteelRange", 30.0, 1.0, 128.0);

    public static final ModConfigSpec.DoubleValue PUSH_PULL_FORCE = BUILDER
            .comment("Base force applied during a Steel Push or Iron Pull.")
            .defineInRange("pushPullForce", 1.2, 0.1, 20.0);

    public static final ModConfigSpec.DoubleValue PROJECTILE_DAMAGE = BUILDER
            .comment("Damage dealt to a living entity struck by a Steel-pushed item projectile.")
            .defineInRange("projectileDamage", 4.0, 0.0, 40.0);

    public static final ModConfigSpec.DoubleValue STEELJUMP_FORCE = BUILDER
            .comment("Upward force applied when Steeljumping off a heavy metal block below the player.")
            .defineInRange("steeljumpForce", 1.8, 0.1, 20.0);

    /** Maximum angle (degrees) from straight down at which Steeljumping is allowed. */
    public static final ModConfigSpec.DoubleValue STEELJUMP_ANGLE = BUILDER
            .comment("Maximum angle in degrees from directly below the player within which a block"
                    + " can trigger a Steeljump (default 45).")
            .defineInRange("steeljumpAngle", 45.0, 5.0, 90.0);

    /**
     * When pushing off a grounded iron ingot, the maximum angle in degrees from
     * directly above the ingot within which it acts as an anchor (launches player).
     * Beyond this angle the ingot slides along the ground instead.
     */
    public static final ModConfigSpec.DoubleValue INGOT_ANCHOR_ANGLE = BUILDER
            .comment("Maximum angle in degrees from directly above a grounded iron ingot"
                    + " at which it acts as a Steel-push anchor. Beyond this angle the"
                    + " ingot slides instead of anchoring (default 45).")
            .defineInRange("ingotAnchorAngle", 45.0, 5.0, 90.0);

    // ── Bronze ───────────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue BRONZE_RANGE = BUILDER
            .comment("Radius in blocks for Bronze pulse detection.")
            .defineInRange("bronzeRange", 30.0, 1.0, 128.0);

    // ── Brass / Zinc ─────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue BRASS_RANGE = BUILDER
            .comment("Radius in blocks for Brass soothing effect.")
            .defineInRange("brassRange", 20.0, 1.0, 128.0);

    public static final ModConfigSpec.IntValue BRASS_LINGER_TICKS = BUILDER
            .comment("Ticks after Brass burning stops before mob AI is restored.")
            .defineInRange("brassLingerTicks", 60, 0, 600);

    public static final ModConfigSpec.DoubleValue ZINC_RANGE = BUILDER
            .comment("Radius in blocks for Zinc rioting effect.")
            .defineInRange("zincRange", 20.0, 1.0, 128.0);

    public static final ModConfigSpec.IntValue ZINC_LINGER_TICKS = BUILDER
            .comment("Ticks after Zinc burning stops before mob AI reverts.")
            .defineInRange("zincLingerTicks", 60, 0, 600);

    // ── Tin ──────────────────────────────────────────────────────────────────

    public static final ModConfigSpec.DoubleValue TIN_SOUND_RANGE = BUILDER
            .comment("Radius in blocks within which Tin detects nearby sounds.")
            .defineInRange("tinSoundRange", 64.0, 1.0, 256.0);

    // ── Built spec ───────────────────────────────────────────────────────────

    public static final ModConfigSpec SPEC = BUILDER.build();
}
