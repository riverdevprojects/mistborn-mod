package com.mistborn;

import com.mistborn.capability.ModAttachments;
import com.mistborn.command.PowerCommand;
import com.mistborn.config.MistbornConfig;
import com.mistborn.item.ModItems;
import com.mistborn.network.ModNetwork;
import com.mistborn.power.PowerHandler;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for the Mistborn: The Final Empire mod.
 *
 * <p>Registers all server-side events and wires together the
 * capability, network, item, and power systems.</p>
 */
@Mod(MistbornMod.MODID)
public class MistbornMod {

    public static final String MODID = "mistborn";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    public MistbornMod(IEventBus modEventBus, ModContainer modContainer) {
        // Register DeferredRegisters to the mod event bus
        ModItems.ITEMS.register(modEventBus);
        ModItems.CREATIVE_TABS.register(modEventBus);
        ModAttachments.ATTACHMENT_TYPES.register(modEventBus);

        // Network payload registration
        modEventBus.addListener(ModNetwork::register);

        // Register config
        modContainer.registerConfig(ModConfig.Type.COMMON, MistbornConfig.SPEC);

        // Register this class as a game event listener
        NeoForge.EVENT_BUS.register(this);
    }

    // ── Player tick (server side) ─────────────────────────────────────────────

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            PowerHandler.onPlayerTick(sp);
        }
    }

    // ── Level tick (server side) ──────────────────────────────────────────────

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof ServerLevel sl) {
            PowerHandler.tickGlobalEffects(sl);
            PowerHandler.tickProjectiles(sl);
        }
    }

    // ── Fall damage negation for Pewter ──────────────────────────────────────

    @SubscribeEvent
    public void onLivingFall(LivingFallEvent event) {
        LivingEntity entity = event.getEntity();
        if (PowerHandler.shouldNegateFallDamage(entity)) {
            event.setCanceled(true);
        }
    }

    // ── Command registration ──────────────────────────────────────────────────

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        PowerCommand.register(event.getDispatcher());
    }

    // ── Player clone (respawn / dimension travel) ─────────────────────────────

    /**
     * When a player respawns or changes dimension, copy their Allomantic data
     * to the new player instance so nothing is lost.
     */
    @SubscribeEvent
    public void onPlayerClone(PlayerEvent.Clone event) {
        // Copy Allomantic data from the original player to the new instance.
        // NeoForge attachment types are accessible directly without reviveCaps/invalidateCaps.
        var original = event.getOriginal();
        var clone    = event.getEntity();
        if (original.hasData(ModAttachments.ALLOMANTIC_DATA.get())) {
            clone.getData(ModAttachments.ALLOMANTIC_DATA.get())
                 .copyFrom(original.getData(ModAttachments.ALLOMANTIC_DATA.get()));
        }
    }

    // ── Player login – sync data to the joining client ────────────────────────

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            com.mistborn.network.ModNetwork.sync(sp,
                    sp.getData(ModAttachments.ALLOMANTIC_DATA.get()));
        }
    }

    // ── Player respawn – sync after data copy ────────────────────────────────

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            com.mistborn.network.ModNetwork.sync(sp,
                    sp.getData(ModAttachments.ALLOMANTIC_DATA.get()));
        }
    }
}
