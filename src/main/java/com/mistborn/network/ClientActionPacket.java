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
 *   <li>{@link Action#SELECT_METAL}  – Player chose a slot from the radial wheel (V).
 *       Sets the <em>selected</em> metal without starting burn.  If the player was
 *       already burning a <em>different</em> metal, the F-toggle is cleared.</li>
 *   <li>{@link Action#TOGGLE_BURN}   – Player pressed F.  Toggles burning on/off for
 *       the currently selected metal.</li>
 *   <li>{@link Action#REQUEST_PULL}  – Player right-clicked (Iron/Steel group active, F on).</li>
 *   <li>{@link Action#REQUEST_PUSH}  – Player left-clicked  (Iron/Steel group active, F on).</li>
 * </ul>
 */
public record ClientActionPacket(Action action, int metalOrdinal) implements CustomPacketPayload {

    public enum Action {
        SELECT_METAL,
        TOGGLE_BURN,
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
        Action action = actionOrd < Action.values().length
                ? Action.values()[actionOrd] : Action.TOGGLE_BURN;
        return new ClientActionPacket(action, metalOrd);
    }

    // ── Handling (server side) ────────────────────────────────────────────────

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!(ctx.player() instanceof ServerPlayer player)) return;
            AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

            switch (action) {

                // Select a metal (radial wheel V) – does NOT start burning.
                // If switching away from the currently active metal, clears the F-toggle.
                case SELECT_METAL -> {
                    AllomanticMetal[] vals = AllomanticMetal.values();
                    if (metalOrdinal < 0 || metalOrdinal >= vals.length) break;
                    AllomanticMetal metal = vals[metalOrdinal];
                    if (!data.isUnlocked(metal)) break;

                    // For Iron/Steel group (represented by IRON): require at least one reserve
                    boolean hasReserve;
                    if (metal == AllomanticMetal.IRON) {
                        hasReserve = data.getReserve(AllomanticMetal.IRON)  > 0f
                                  || data.getReserve(AllomanticMetal.STEEL) > 0f;
                    } else {
                        hasReserve = data.getReserve(metal) > 0f;
                    }
                    if (!hasReserve) break;

                    // Switching to a different metal while burning → turn off F-toggle
                    if (data.isBurningActive() && data.getCurrentlyBurning() != metal) {
                        data.setBurningActive(false);
                    }

                    data.setCurrentlyBurning(metal);
                    ModNetwork.sync(player, data);
                }

                // Toggle burning on/off (F key press)
                case TOGGLE_BURN -> {
                    AllomanticMetal selected = data.getCurrentlyBurning();
                    if (selected == null) break;

                    if (data.isBurningActive()) {
                        data.setBurningActive(false);
                    } else {
                        // Start burning only if there is reserve to consume
                        boolean canBurn;
                        if (selected == AllomanticMetal.IRON) {
                            canBurn = data.getReserve(AllomanticMetal.IRON)  > 0f
                                   || data.getReserve(AllomanticMetal.STEEL) > 0f;
                        } else {
                            canBurn = data.getReserve(selected) > 0f;
                        }
                        if (canBurn) data.setBurningActive(true);
                    }
                    ModNetwork.sync(player, data);
                }

                // Iron Pull via right-click (Iron/Steel group, F on)
                case REQUEST_PULL -> {
                    if (data.getCurrentlyBurning() != AllomanticMetal.IRON) break;
                    if (!data.isBurningActive()) break;
                    if (data.getReserve(AllomanticMetal.IRON) <= 0f) break;
                    if (!(player.level() instanceof ServerLevel sl)) break;

                    List<MetalSource> sources = IronSteelHandler.findSources(player, sl);
                    MetalSource target = IronSteelHandler.findTarget(player, sources);
                    if (target != null) IronSteelHandler.executePull(player, target);
                }

                // Steel Push via left-click (Iron/Steel group, F on)
                case REQUEST_PUSH -> {
                    if (data.getCurrentlyBurning() != AllomanticMetal.IRON) break;
                    if (!data.isBurningActive()) break;
                    if (data.getReserve(AllomanticMetal.STEEL) <= 0f) break;
                    if (!(player.level() instanceof ServerLevel sl)) break;

                    List<MetalSource> sources = IronSteelHandler.findSources(player, sl);
                    MetalSource target = IronSteelHandler.findTarget(player, sources);
                    if (target != null) IronSteelHandler.executePush(player, target, sl);
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

    public static ClientActionPacket toggleBurn() {
        return new ClientActionPacket(Action.TOGGLE_BURN, 0);
    }

    public static ClientActionPacket requestPull() {
        return new ClientActionPacket(Action.REQUEST_PULL, 0);
    }

    public static ClientActionPacket requestPush() {
        return new ClientActionPacket(Action.REQUEST_PUSH, 0);
    }
}
