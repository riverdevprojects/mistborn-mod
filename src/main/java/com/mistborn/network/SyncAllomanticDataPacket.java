package com.mistborn.network;

import com.mistborn.capability.AllomanticData;
import com.mistborn.capability.ModAttachments;
import com.mistborn.power.AllomanticMetal;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static com.mistborn.MistbornMod.MODID;

/**
 * Server → Client packet that syncs a player's {@link AllomanticData} to them.
 * Only sent when the dirty flag is set (i.e. data has actually changed).
 */
public record SyncAllomanticDataPacket(
        Set<AllomanticMetal> unlockedMetals,
        Map<AllomanticMetal, Float> reserves,
        AllomanticMetal currentlyBurning, // may be null; represents selected metal
        boolean isBurningActive           // F-toggle state
) implements CustomPacketPayload {

    public static final Type<SyncAllomanticDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "sync_allomantic_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncAllomanticDataPacket> STREAM_CODEC =
            StreamCodec.of(SyncAllomanticDataPacket::encode, SyncAllomanticDataPacket::decode);

    // ── Encoding ──────────────────────────────────────────────────────────────

    private static void encode(FriendlyByteBuf buf, SyncAllomanticDataPacket pkt) {
        // Unlocked metals – write as a bitmask (8 metals fit in 1 byte)
        int mask = 0;
        for (AllomanticMetal m : AllomanticMetal.values()) {
            if (pkt.unlockedMetals.contains(m)) mask |= (1 << m.ordinal());
        }
        buf.writeByte(mask);

        // Reserves – write a float for every metal
        for (AllomanticMetal m : AllomanticMetal.values()) {
            buf.writeFloat(pkt.reserves.getOrDefault(m, 0f));
        }

        // Currently selected metal (ordinal, or -1 for null)
        buf.writeByte(pkt.currentlyBurning != null ? pkt.currentlyBurning.ordinal() : -1);

        // Burn-active flag
        buf.writeBoolean(pkt.isBurningActive);
    }

    private static SyncAllomanticDataPacket decode(FriendlyByteBuf buf) {
        int mask = buf.readByte() & 0xFF;
        Set<AllomanticMetal> unlocked = EnumSet.noneOf(AllomanticMetal.class);
        AllomanticMetal[] vals = AllomanticMetal.values();
        for (int i = 0; i < vals.length; i++) {
            if ((mask & (1 << i)) != 0) unlocked.add(vals[i]);
        }

        Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);
        for (AllomanticMetal m : vals) {
            float v = buf.readFloat();
            if (v != 0f) reserves.put(m, v);
        }

        int burningOrdinal = buf.readByte();
        AllomanticMetal burning = (burningOrdinal >= 0 && burningOrdinal < vals.length)
                ? vals[burningOrdinal] : null;

        boolean burningActive = buf.readBoolean();

        return new SyncAllomanticDataPacket(unlocked, reserves, burning, burningActive);
    }

    // ── Handling (client side) ────────────────────────────────────────────────

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

            // Apply received values
            data.getUnlockedMetals().clear();
            data.getUnlockedMetals().addAll(unlockedMetals);

            // Sync each reserve individually via public API
            for (AllomanticMetal m : AllomanticMetal.values()) {
                float target = reserves.getOrDefault(m, 0f);
                float existing = data.getReserve(m);
                if (existing > 0) data.drainReserve(m, existing);
                if (target > 0)   data.addReserve(m, target);
            }

            data.setCurrentlyBurning(currentlyBurning);
            data.setBurningActive(isBurningActive);
            data.clearDirty();
        });
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Build a packet from live {@link AllomanticData}.
     */
    public static SyncAllomanticDataPacket of(AllomanticData data) {
        Set<AllomanticMetal> unlocked = EnumSet.copyOf(
                data.getUnlockedMetals().isEmpty()
                        ? EnumSet.noneOf(AllomanticMetal.class)
                        : data.getUnlockedMetals());
        Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);
        for (AllomanticMetal m : AllomanticMetal.values()) {
            float v = data.getReserve(m);
            if (v != 0f) reserves.put(m, v);
        }
        return new SyncAllomanticDataPacket(
                unlocked,
                reserves,
                data.getCurrentlyBurning(),
                data.isBurningActive());
    }
}
