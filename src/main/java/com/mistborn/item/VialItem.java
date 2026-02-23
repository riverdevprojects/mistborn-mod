package com.mistborn.item;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.network.ModNetwork;
import com.mistborn.power.AllomanticMetal;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * A vial that can hold a mixture of Allomantic metal flakes.
 *
 * <p>Metal flake counts are stored inside the item's {@link CustomData} component
 * under the key {@code "VialMetals"}, with each metal stored by its enum name.</p>
 *
 * <p>When consumed (right-click):</p>
 * <ul>
 *   <li>Each metal in the vial unlocks that metal for the player (if not already unlocked).</li>
 *   <li>Each metal's count is converted to reserve
 *       ({@code count * FLAKE_RESERVE_AMOUNT}, capped at 100).</li>
 *   <li>The vial becomes empty and stays in the player's hand.</li>
 * </ul>
 *
 * <p>Tooltip shows the percentage composition of each metal in the vial.</p>
 */
public class VialItem extends Item {

    /** Reserve units granted per flake when the vial is consumed. */
    public static final float FLAKE_RESERVE_AMOUNT = 20f;

    /** NBT sub-tag name inside CustomData that holds metal counts. */
    private static final String TAG_VIAL_METALS = "VialMetals";

    public VialItem(Properties properties) {
        super(properties);
    }

    // ── Public data API ────────────────────────────────────────────────────────

    /**
     * Returns a mutable copy of the metal counts stored in this vial.
     * Returns an empty map if the vial is empty.
     */
    public static Map<AllomanticMetal, Integer> getMetals(ItemStack stack) {
        Map<AllomanticMetal, Integer> result = new EnumMap<>(AllomanticMetal.class);
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData == null) return result;

        CompoundTag root = customData.copyTag();
        if (!root.contains(TAG_VIAL_METALS)) return result;

        CompoundTag metals = root.getCompound(TAG_VIAL_METALS);
        for (AllomanticMetal metal : AllomanticMetal.values()) {
            if (metals.contains(metal.name())) {
                int count = metals.getInt(metal.name());
                if (count > 0) result.put(metal, count);
            }
        }
        return result;
    }

    /**
     * Writes the given metal counts back to the ItemStack's CustomData.
     * Passing an empty map clears all metal data (empty vial).
     */
    public static void setMetals(ItemStack stack, Map<AllomanticMetal, Integer> metals) {
        CompoundTag root;
        CustomData existing = stack.get(DataComponents.CUSTOM_DATA);
        root = (existing != null) ? existing.copyTag() : new CompoundTag();

        CompoundTag metalsTag = new CompoundTag();
        for (Map.Entry<AllomanticMetal, Integer> entry : metals.entrySet()) {
            if (entry.getValue() > 0) {
                metalsTag.putInt(entry.getKey().name(), entry.getValue());
            }
        }

        if (metalsTag.isEmpty()) {
            root.remove(TAG_VIAL_METALS);
        } else {
            root.put(TAG_VIAL_METALS, metalsTag);
        }

        if (root.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        }
    }

    /**
     * Adds {@code count} flakes of {@code metal} to the vial, accumulating with existing data.
     */
    public static void addMetal(ItemStack stack, AllomanticMetal metal, int count) {
        if (count <= 0) return;
        Map<AllomanticMetal, Integer> current = getMetals(stack);
        current.merge(metal, count, Integer::sum);
        setMetals(stack, current);
    }

    /**
     * Returns true if the vial contains at least one metal.
     */
    public static boolean isFilled(ItemStack stack) {
        return !getMetals(stack).isEmpty();
    }

    /**
     * Returns the total number of flakes in the vial (sum of all metal counts).
     */
    public static int getTotalFlakes(ItemStack stack) {
        return getMetals(stack).values().stream().mapToInt(Integer::intValue).sum();
    }

    // ── Item behaviour ────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            return InteractionResultHolder.success(stack);
        }

        Map<AllomanticMetal, Integer> metals = getMetals(stack);
        if (metals.isEmpty()) {
            player.sendSystemMessage(Component.literal("The vial is empty.").withStyle(ChatFormatting.GRAY));
            return InteractionResultHolder.fail(stack);
        }

        AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

        // Add reserves (and unlock) for each metal in the vial
        for (Map.Entry<AllomanticMetal, Integer> entry : metals.entrySet()) {
            AllomanticMetal metal = entry.getKey();
            int flakeCount = entry.getValue();
            float reserveToAdd = flakeCount * FLAKE_RESERVE_AMOUNT;

            // Unlock the metal so the player can burn it
            data.unlockMetal(metal);
            // Add reserves (capped at 100 inside addReserve)
            data.addReserve(metal, reserveToAdd);
        }

        // Play drinking / swallowing sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.HONEY_DRINK, SoundSource.PLAYERS,
                1.0f, 1.0f + (level.random.nextFloat() - 0.5f) * 0.2f);

        // Sync to client
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sync(sp, data);
        }

        // Clear the vial (keep the item, just empty it)
        setMetals(stack, Map.of());

        return InteractionResultHolder.consume(stack);
    }

    // ── Tooltip ───────────────────────────────────────────────────────────────

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);

        Map<AllomanticMetal, Integer> metals = getMetals(stack);
        if (metals.isEmpty()) {
            tooltip.add(Component.literal("Empty").withStyle(ChatFormatting.GRAY));
            return;
        }

        int total = metals.values().stream().mapToInt(Integer::intValue).sum();

        tooltip.add(Component.literal("Contents:").withStyle(ChatFormatting.DARK_AQUA));
        for (AllomanticMetal metal : AllomanticMetal.values()) {
            Integer count = metals.get(metal);
            if (count == null || count == 0) continue;

            double pct = (double) count / total * 100.0;
            String pctStr = String.format("%.2f%%", pct);

            // Use the metal's colour for the percentage entry
            int col = metal.getColour();
            Component line = Component.literal("  " + metal.getDisplayName() + ": " + pctStr)
                    .withStyle(style -> style.withColor(col));
            tooltip.add(line);
        }
        tooltip.add(Component.literal("Total flakes: " + total).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public Component getName(ItemStack stack) {
        if (isFilled(stack)) {
            return Component.translatable("item.mistborn.vial.filled");
        }
        return super.getName(stack);
    }
}
