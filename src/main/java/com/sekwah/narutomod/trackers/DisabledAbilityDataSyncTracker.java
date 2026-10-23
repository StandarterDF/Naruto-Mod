package com.sekwah.narutomod.trackers;

import com.sekwah.narutomod.capabilities.disabledabilitydata.DisabledAbilityData;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerClone;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Ships the set of switched-off techniques to the client, which the jutsu menu greys the rows from.
 * Only sent when the set actually changed, hence the contents based equals on
 * {@link DisabledAbilityData}.
 */
public class DisabledAbilityDataSyncTracker
        implements SyncTrackerSerializer<DisabledAbilityData>, SyncTrackerClone<DisabledAbilityData> {

    @Override
    public void encode(DisabledAbilityData objectToSend, FriendlyByteBuf outBuffer) {
        outBuffer.writeInt(objectToSend.getAbilities().size());
        for (ResourceLocation id : objectToSend.getAbilities()) {
            outBuffer.writeResourceLocation(id);
        }
    }

    @Override
    public DisabledAbilityData decode(FriendlyByteBuf inBuffer) {
        int size = inBuffer.readInt();
        DisabledAbilityData data = new DisabledAbilityData(size);
        for (int i = 0; i < size; i++) {
            data.getAbilities().add(inBuffer.readResourceLocation());
        }
        return data;
    }

    @Override
    public DisabledAbilityData clone(DisabledAbilityData objectToClone) {
        DisabledAbilityData copy = new DisabledAbilityData(objectToClone.getAbilities().size());
        copy.getAbilities().addAll(objectToClone.getAbilities());
        return copy;
    }
}
