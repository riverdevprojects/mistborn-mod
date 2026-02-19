package com.mistborn.network;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.power.AllomanticMetal;
import com.mistborn.power.IronSteelHandler;
import com.mistborn.power.MetalSource;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

import static com.mistborn.MistbornMod.MODID;

/**
 * Client → Server packet for all player-triggered Allomantic actions.
 *
 * <ul>
 *   <li>{@link Action#SELECT_METAL}  – Player chose a metal from the radial menu.</li>
 *   <li>{@link Action#STOP_BURN}     – Player released the burn key (KEY_BURN).</li>
 *   <li>{@link Action#REQUEST_PULL}  – Player is holding KEY_PULL (Iron active).</li>
 *   <li>{@link Action#REQUEST_PUSH}  – Player is holding KEY_PUSH (Steel active).</li>
 * </ul>
 */
public record ClientActionPacket(Action action, int metalOrdinal) implements CustomPacketPayload {

    public enum Action {
        SELECT_METAL,
        STOP_BURN,
        REQUEST_PULL,
        REQUEST_PUSH
    }

    public static final Type<ClientActionPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "client_action"));

    public static final StreamCodec<FriendlyByteBuf, ClientActionPacket> STREAM_CODEC =
            StreamCodec.of(ClientActionPacket::encode, ClientActionPacket::decode);

    private static void encode(FriendlyByteBuf buf, ClientActionPacket pkt) {
        buf.writeByte(pkt.action.ordinal());
        buf.writeByte(pkt.metalOrdinal);
    }

    private static ClientActionPacket decode(FriendlyByteBuf buf) {
        int actionOrd = buf.readByte() & 0xFF;
        int metalOrd  = buf.readByte() & 0xFF;
        Action action = actionOrd < Action.values().length ? Action.values()[actionOrd] : Action.STOP_BURN;
        return new ClientActionPacket(action, metalOrd);
    }

    // ── Handling (server side) ────────────────────────────────────────────────

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

            switch (action) {
                case SELECT_METAL -> {
                    AllomanticMetal[] vals = AllomanticMetal.values();
                    if (metalOrdinal >= 0 && metalOrdinal < vals.length) {
                        AllomanticMetal metal = vals[metalOrdinal];
                        if (data.isUnlocked(metal) && data.getReserve(metal) > 0f) {
                            data.setCurrentlyBurning(metal);
                            ModNetwork.sync(player, data);
                        }
                    }
                }
                case STOP_BURN -> {
                    data.setCurrentlyBurning(null);
                    ModNetwork.sync(player, data);
                }
                case REQUEST_PULL -> {
                    if (data.getCurrentlyBurning() == AllomanticMetal.IRON
                            && player.level() instanceof ServerLevel sl) {
                        List<MetalSource> sources = IronSteelHandler.findSources(player, sl);
                        MetalSource target = IronSteelHandler.findTarget(player, sources);
                        if (target != null) {
                            IronSteelHandler.executePull(player, target);
                        }
                    }
                }
                case REQUEST_PUSH -> {
                    if (data.getCurrentlyBurning() == AllomanticMetal.STEEL
                            && player.level() instanceof ServerLevel sl) {
                        List<MetalSource> sources = IronSteelHandler.findSources(player, sl);
                        MetalSource target = IronSteelHandler.findTarget(player, sources);
                        if (target != null) {
                            IronSteelHandler.executePush(player, target, sl);
                        }
                    }
                }
            }
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ── Convenience factories ─────────────────────────────────────────────────

    public static ClientActionPacket selectMetal(AllomanticMetal metal) {
        return new ClientActionPacket(Action.SELECT_METAL, metal.ordinal());
    }

    public static ClientActionPacket stopBurn() {
        return new ClientActionPacket(Action.STOP_BURN, 0);
    }

    public static ClientActionPacket requestPull() {
        return new ClientActionPacket(Action.REQUEST_PULL, 0);
    }

    public static ClientActionPacket requestPush() {
        return new ClientActionPacket(Action.REQUEST_PUSH, 0);
    }
}
