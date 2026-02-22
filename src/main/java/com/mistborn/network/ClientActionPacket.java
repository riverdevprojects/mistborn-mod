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
 *       <b>Toggles</b> the metal in/out of the set <em>and</em> active state simultaneously –
 *       selecting a metal immediately starts burning it; deselecting stops burning.
 *       For the Iron/Steel group (represented by IRON), toggles both IRON and STEEL together.</li>
 *   <li>{@link Action#TOGGLE_BURN}   – Player pressed F.
 *       Toggles the Iron/Steel <em>power</em> (push/pull effect) on or off while Iron or Steel
 *       is actively burning. Iron and Steel continue to burn regardless; only the effect
 *       is suppressed when power is off. Has no effect if neither Iron nor Steel is burning.</li>
 *   <li>{@link Action#REQUEST_PULL}  – Player right-clicked (Iron active and power enabled).</li>
 *   <li>{@link Action#REQUEST_PUSH}  – Player left-clicked (Steel active and power enabled).</li>
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

                // Toggle a metal in/out of the set+active state (radial wheel V).
                // Selecting a metal IMMEDIATELY starts burning it (adds to both set and active).
                // Deselecting IMMEDIATELY stops burning (removes from both set and active).
                // For the Iron/Steel group (represented by IRON), both IRON and STEEL are toggled together.
                case SELECT_METAL -> {
                    AllomanticMetal[] vals = AllomanticMetal.values();
                    if (metalOrdinal < 0 || metalOrdinal >= vals.length) break;
                    AllomanticMetal metal = vals[metalOrdinal];
                    if (!data.isUnlocked(metal)) break;

                    if (metal == AllomanticMetal.IRON) {
                        // Iron/Steel group: toggle both together
                        boolean isSet = data.isMetalSet(AllomanticMetal.IRON)
                                     || data.isMetalSet(AllomanticMetal.STEEL);
                        if (isSet) {
                            // Deselect both: stop burning immediately
                            data.removeFromSet(AllomanticMetal.IRON);
                            data.removeFromSet(AllomanticMetal.STEEL);
                            data.removeFromActive(AllomanticMetal.IRON);
                            data.removeFromActive(AllomanticMetal.STEEL);
                        } else {
                            // Select whichever of the pair are unlocked and have reserve;
                            // immediately mark them active (start burning)
                            if (data.isUnlocked(AllomanticMetal.IRON)
                                    && data.getReserve(AllomanticMetal.IRON) > 0f) {
                                data.addToSet(AllomanticMetal.IRON);
                                data.addToActive(AllomanticMetal.IRON);
                            }
                            if (data.isUnlocked(AllomanticMetal.STEEL)
                                    && data.getReserve(AllomanticMetal.STEEL) > 0f) {
                                data.addToSet(AllomanticMetal.STEEL);
                                data.addToActive(AllomanticMetal.STEEL);
                            }
                        }
                    } else {
                        // Regular metal: toggle set+active simultaneously
                        if (data.isMetalSet(metal)) {
                            // Deselect: stop burning immediately
                            data.removeFromSet(metal);
                            data.removeFromActive(metal);
                        } else {
                            // Select: start burning immediately if reserve available
                            if (data.getReserve(metal) > 0f) {
                                data.addToSet(metal);
                                data.addToActive(metal);
                            }
                        }
                    }
                    ModNetwork.sync(player, data);
                }

                // Toggle Iron/Steel POWER (push/pull effect) on/off (F key press).
                // Iron and Steel continue burning regardless; only their effect is suppressed.
                // Has no effect if neither Iron nor Steel is actively burning.
                case TOGGLE_BURN -> {
                    boolean ironActive  = data.isMetalActive(AllomanticMetal.IRON);
                    boolean steelActive = data.isMetalActive(AllomanticMetal.STEEL);
                    if (ironActive || steelActive) {
                        data.toggleIronSteelPower();
                        ModNetwork.sync(player, data);
                    }
                }

                // Iron Pull via right-click (Iron is active AND power is enabled)
                case REQUEST_PULL -> {
                    if (!data.isMetalActive(AllomanticMetal.IRON)) break;
                    if (!data.isIronSteelPowerEnabled()) break;
                    if (data.getReserve(AllomanticMetal.IRON) <= 0f) break;
                    if (!(player.level() instanceof ServerLevel sl)) break;

                    List<MetalSource> sources = IronSteelHandler.findSources(player, sl);
                    MetalSource target = IronSteelHandler.findTarget(player, sources);
                    if (target != null) IronSteelHandler.executePull(player, target);
                }

                // Steel Push via left-click (Steel is active AND power is enabled)
                case REQUEST_PUSH -> {
                    if (!data.isMetalActive(AllomanticMetal.STEEL)) break;
                    if (!data.isIronSteelPowerEnabled()) break;
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
