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
 *
 * <p>Wire format (after unlocked-bitmask and reserves):</p>
 * <ul>
 *   <li>1 byte – setMetals bitmask (bit i = AllomanticMetal.values()[i] is set/queued)</li>
 *   <li>1 byte – activeMetals bitmask (bit i = metal is actively burning)</li>
 *   <li>1 byte – ironSteelPowerEnabled (0 = false, 1 = true)</li>
 * </ul>
 */
public record SyncAllomanticDataPacket(
        Set<AllomanticMetal> unlockedMetals,
        Map<AllomanticMetal, Float> reserves,
        Set<AllomanticMetal> setMetals,
        Set<AllomanticMetal> activeMetals,
        boolean ironSteelPowerEnabled
) implements CustomPacketPayload {

    public static final Type<SyncAllomanticDataPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MODID, "sync_allomantic_data"));

    public static final StreamCodec<FriendlyByteBuf, SyncAllomanticDataPacket> STREAM_CODEC =
            StreamCodec.of(SyncAllomanticDataPacket::encode, SyncAllomanticDataPacket::decode);

    // ── Encoding ──────────────────────────────────────────────────────────────

    private static void encode(FriendlyByteBuf buf, SyncAllomanticDataPacket pkt) {
        AllomanticMetal[] vals = AllomanticMetal.values();

        // Unlocked metals – write as a bitmask (8 metals fit in 1 byte)
        int unlockedMask = 0;
        for (AllomanticMetal m : vals) {
            if (pkt.unlockedMetals.contains(m)) unlockedMask |= (1 << m.ordinal());
        }
        buf.writeByte(unlockedMask);

        // Reserves – write a float for every metal
        for (AllomanticMetal m : vals) {
            buf.writeFloat(pkt.reserves.getOrDefault(m, 0f));
        }

        // setMetals bitmask
        int setMask = 0;
        for (AllomanticMetal m : vals) {
            if (pkt.setMetals.contains(m)) setMask |= (1 << m.ordinal());
        }
        buf.writeByte(setMask);

        // activeMetals bitmask
        int activeMask = 0;
        for (AllomanticMetal m : vals) {
            if (pkt.activeMetals.contains(m)) activeMask |= (1 << m.ordinal());
        }
        buf.writeByte(activeMask);

        // ironSteelPowerEnabled
        buf.writeBoolean(pkt.ironSteelPowerEnabled);
    }

    private static SyncAllomanticDataPacket decode(FriendlyByteBuf buf) {
        AllomanticMetal[] vals = AllomanticMetal.values();

        int unlockedMask = buf.readByte() & 0xFF;
        Set<AllomanticMetal> unlocked = EnumSet.noneOf(AllomanticMetal.class);
        for (int i = 0; i < vals.length; i++) {
            if ((unlockedMask & (1 << i)) != 0) unlocked.add(vals[i]);
        }

        Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);
        for (AllomanticMetal m : vals) {
            float v = buf.readFloat();
            if (v != 0f) reserves.put(m, v);
        }

        int setMask = buf.readByte() & 0xFF;
        Set<AllomanticMetal> setMetals = EnumSet.noneOf(AllomanticMetal.class);
        for (int i = 0; i < vals.length; i++) {
            if ((setMask & (1 << i)) != 0) setMetals.add(vals[i]);
        }

        int activeMask = buf.readByte() & 0xFF;
        Set<AllomanticMetal> activeMetals = EnumSet.noneOf(AllomanticMetal.class);
        for (int i = 0; i < vals.length; i++) {
            if ((activeMask & (1 << i)) != 0) activeMetals.add(vals[i]);
        }

        boolean ironSteelPowerEnabled = buf.readBoolean();

        return new SyncAllomanticDataPacket(unlocked, reserves, setMetals, activeMetals,
                ironSteelPowerEnabled);
    }

    // ── Handling (client side) ────────────────────────────────────────────────

    public void handle(IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            var player = Minecraft.getInstance().player;
            if (player == null) return;

            AllomanticData data = player.getData(ModAttachments.ALLOMANTIC_DATA.get());

            // Sync unlocked metals
            data.getUnlockedMetals().clear();
            data.getUnlockedMetals().addAll(unlockedMetals);

            // Sync each reserve individually via public API
            for (AllomanticMetal m : AllomanticMetal.values()) {
                float target = reserves.getOrDefault(m, 0f);
                float existing = data.getReserve(m);
                if (existing > 0) data.drainReserve(m, existing);
                if (target > 0)   data.addReserve(m, target);
            }

            // Sync set and active metals directly
            data.getSetMetals().clear();
            data.getSetMetals().addAll(setMetals);

            data.getActiveMetals().clear();
            data.getActiveMetals().addAll(activeMetals);

            // Sync iron/steel power flag
            data.setIronSteelPowerEnabled(ironSteelPowerEnabled);

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
        Set<AllomanticMetal> unlocked = data.getUnlockedMetals().isEmpty()
                ? EnumSet.noneOf(AllomanticMetal.class)
                : EnumSet.copyOf(data.getUnlockedMetals());

        Map<AllomanticMetal, Float> reserves = new EnumMap<>(AllomanticMetal.class);
        for (AllomanticMetal m : AllomanticMetal.values()) {
            float v = data.getReserve(m);
            if (v != 0f) reserves.put(m, v);
        }

        Set<AllomanticMetal> setMetals = data.getSetMetals().isEmpty()
                ? EnumSet.noneOf(AllomanticMetal.class)
                : EnumSet.copyOf(data.getSetMetals());

        Set<AllomanticMetal> activeMetals = data.getActiveMetals().isEmpty()
                ? EnumSet.noneOf(AllomanticMetal.class)
                : EnumSet.copyOf(data.getActiveMetals());

        return new SyncAllomanticDataPacket(unlocked, reserves, setMetals, activeMetals,
                data.isIronSteelPowerEnabled());
    }
}
