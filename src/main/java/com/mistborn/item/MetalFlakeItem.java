package com.mistborn.item;

import com.mistborn.power.AllomanticMetal;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A metal-flake item for one of the eight Allomantic metals.
 * Flakes cannot be eaten directly – they must be loaded into an
 * {@link VialItem Allomantic Vial} first and consumed that way.
 */
public class MetalFlakeItem extends Item {

    private final AllomanticMetal metal;

    public MetalFlakeItem(AllomanticMetal metal, Properties properties) {
        super(properties);
        this.metal = metal;
    }

    /**
     * Flakes cannot be eaten directly; the player must use an Allomantic Vial.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            player.sendSystemMessage(Component.literal(
                    "Metal flakes cannot be eaten directly. Load them into an Allomantic Vial first."));
        }

        return InteractionResultHolder.fail(stack);
    }

    public AllomanticMetal getMetal() {
        return metal;
    }
}
