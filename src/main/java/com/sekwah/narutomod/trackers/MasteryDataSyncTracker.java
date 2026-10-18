package com.sekwah.narutomod.trackers;

import com.sekwah.narutomod.capabilities.mastery.MasteryData;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerClone;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

import java.util.Map;

/**
 * Ships the per-technique cast counts to the client, which is what the jutsu menu draws its mastery
 * bars from. Only sent when the map actually changed, hence the contents based equals on
 * {@link MasteryData}.
 */
public class MasteryDataSyncTracker implements SyncTrackerSerializer<MasteryData>, SyncTrackerClone<MasteryData> {

    @Override
    public void encode(MasteryData objectToSend, FriendlyByteBuf outBuffer) {
        Map<ResourceLocation, Integer> casts = objectToSend.getCasts();
        outBuffer.writeInt(casts.size());
        for (Map.Entry<ResourceLocation, Integer> entry : casts.entrySet()) {
            outBuffer.writeResourceLocation(entry.getKey());
            outBuffer.writeVarInt(entry.getValue());
        }
    }

    @Override
    public MasteryData decode(FriendlyByteBuf inBuffer) {
        final int size = inBuffer.readInt();
        MasteryData data = new MasteryData();
        for (int i = 0; i < size; i++) {
            ResourceLocation ability = inBuffer.readResourceLocation();
            data.setCasts(ability, inBuffer.readVarInt());
        }
        return data;
    }

    @Override
    public MasteryData clone(MasteryData data) {
        MasteryData cloned = new MasteryData();
        cloned.getCasts().putAll(data.getCasts());
        return cloned;
    }
}
