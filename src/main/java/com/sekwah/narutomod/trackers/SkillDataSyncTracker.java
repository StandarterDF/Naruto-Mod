package com.sekwah.narutomod.trackers;

import com.sekwah.narutomod.capabilities.skills.SkillData;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerClone;
import com.sekwah.sekclib.capabilitysync.capabilitysync.tracker.SyncTrackerSerializer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

/**
 * Ships the learned skill nodes to the client, which the skill screen draws the tree from. Only sent
 * when the set actually changed, hence the contents based equals on {@link SkillData}.
 */
public class SkillDataSyncTracker implements SyncTrackerSerializer<SkillData>, SyncTrackerClone<SkillData> {

    @Override
    public void encode(SkillData objectToSend, FriendlyByteBuf outBuffer) {
        outBuffer.writeVarInt(objectToSend.getPoints());
        outBuffer.writeInt(objectToSend.getUnlocked().size());
        for (ResourceLocation id : objectToSend.getUnlocked()) {
            outBuffer.writeResourceLocation(id);
        }
        outBuffer.writeInt(objectToSend.getScrolls().size());
        for (ResourceLocation id : objectToSend.getScrolls()) {
            outBuffer.writeResourceLocation(id);
        }
    }

    @Override
    public SkillData decode(FriendlyByteBuf inBuffer) {
        SkillData data = new SkillData();
        data.addPoints(inBuffer.readVarInt());
        int size = inBuffer.readInt();
        for (int i = 0; i < size; i++) {
            data.unlock(inBuffer.readResourceLocation());
        }
        int scrolls = inBuffer.readInt();
        for (int i = 0; i < scrolls; i++) {
            data.learnScroll(inBuffer.readResourceLocation());
        }
        return data;
    }

    @Override
    public SkillData clone(SkillData objectToClone) {
        SkillData copy = new SkillData();
        copy.addPoints(objectToClone.getPoints());
        copy.getUnlocked().addAll(objectToClone.getUnlocked());
        copy.getScrolls().addAll(objectToClone.getScrolls());
        return copy;
    }
}
