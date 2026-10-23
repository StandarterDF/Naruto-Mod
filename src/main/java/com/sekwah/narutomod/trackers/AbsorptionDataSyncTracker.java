package com.sekwah.narutomod.trackers;

import com.sekwah.narutomod.capabilities.absorption.AbsorptionData;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerClone;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerSerializer;
import net.minecraft.network.FriendlyByteBuf;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ships the borrowed chakra counts to the client, which draws the HUD pips from them.
 *
 * <p>Counts are small and the map is tiny, so the whole thing goes every time it changes; the
 * contents based equals on {@link AbsorptionData} is what keeps an idle player from sending
 * anything at all.</p>
 */
public class AbsorptionDataSyncTracker implements SyncTrackerSerializer<AbsorptionData>,
        SyncTrackerClone<AbsorptionData> {

    @Override
    public void encode(AbsorptionData objectToSend, FriendlyByteBuf outBuffer) {
        Map<AbsorptionData.Nature, Integer> charges = objectToSend.snapshot();
        outBuffer.writeVarInt(charges.size());
        for (Map.Entry<AbsorptionData.Nature, Integer> entry : charges.entrySet()) {
            outBuffer.writeEnum(entry.getKey());
            outBuffer.writeVarInt(entry.getValue());
        }
    }

    @Override
    public AbsorptionData decode(FriendlyByteBuf inBuffer) {
        int size = inBuffer.readVarInt();
        Map<AbsorptionData.Nature, Integer> charges = new EnumMap<>(AbsorptionData.Nature.class);
        for (int i = 0; i < size; i++) {
            charges.put(inBuffer.readEnum(AbsorptionData.Nature.class), inBuffer.readVarInt());
        }
        AbsorptionData data = new AbsorptionData();
        data.apply(charges);
        return data;
    }

    @Override
    public AbsorptionData clone(AbsorptionData data) {
        AbsorptionData cloned = new AbsorptionData();
        cloned.apply(data.snapshot());
        return cloned;
    }
}
