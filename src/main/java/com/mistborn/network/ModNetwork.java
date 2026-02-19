package com.mistborn.network;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Central network registration and helper utilities for the Mistborn mod.
 */
public class ModNetwork {

    private ModNetwork() {}

    /**
     * Called on the mod event bus during {@link RegisterPayloadHandlersEvent}.
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");

        registrar.playToClient(
                SyncAllomanticDataPacket.TYPE,
                SyncAllomanticDataPacket.STREAM_CODEC,
                SyncAllomanticDataPacket::handle);

        registrar.playToServer(
                ClientActionPacket.TYPE,
                ClientActionPacket.STREAM_CODEC,
                ClientActionPacket::handle);
    }

    /**
     * Convenience: sync the player's Allomantic data to themselves if the dirty flag is set.
     */
    public static void syncIfDirty(ServerPlayer player) {
        AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());
        if (data.isDirty()) {
            sync(player, data);
            data.clearDirty();
        }
    }

    /**
     * Unconditionally sync the player's data to themselves.
     */
    public static void sync(ServerPlayer player, AllomanticData data) {
        PacketDistributor.sendToPlayer(player, SyncAllomanticDataPacket.of(data));
    }
}
