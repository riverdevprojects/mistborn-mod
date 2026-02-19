package com.mistborn.item;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.network.ModNetwork;
import com.mistborn.power.AllomanticMetal;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A consumable metal-flake item for one of the eight Allomantic metals.
 * Right-clicking adds 20 units of reserve to the player's Allomantic pool
 * for this metal, if they have it unlocked.  Does nothing (with a chat
 * message) if the metal is not unlocked.
 */
public class MetalFlakeItem extends Item {

    private final AllomanticMetal metal;
    private static final float FLAKE_RESERVE_AMOUNT = 20f;

    public MetalFlakeItem(AllomanticMetal metal, Properties properties) {
        super(properties);
        this.metal = metal;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (level.isClientSide()) {
            // Prevent double-processing; actual logic runs on server
            return InteractionResultHolder.success(stack);
        }

        AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

        if (!data.isUnlocked(metal)) {
            player.sendSystemMessage(Component.literal(
                    "You are not a " + metal.getDisplayName() + " Misting. You cannot ingest this metal."));
            return InteractionResultHolder.fail(stack);
        }

        // Add reserve (capped internally at 100)
        data.addReserve(metal, FLAKE_RESERVE_AMOUNT);

        // Play swallowing / consumption sound
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EAT, SoundSource.PLAYERS, 1.0f, 1.0f + (level.random.nextFloat() - 0.5f) * 0.4f);

        // Sync to client
        if (player instanceof ServerPlayer sp) {
            ModNetwork.sync(sp, data);
        }

        // Shrink the stack by 1
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }

        return InteractionResultHolder.consume(stack);
    }

    public AllomanticMetal getMetal() {
        return metal;
    }
}
