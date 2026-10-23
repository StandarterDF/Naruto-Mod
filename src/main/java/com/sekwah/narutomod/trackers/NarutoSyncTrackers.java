package com.sekwah.narutomod.trackers;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.capabilities.DoubleJumpData;
import com.sekwah.narutomod.capabilities.absorption.AbsorptionData;
import com.sekwah.narutomod.capabilities.disabledabilitydata.DisabledAbilityData;
import com.sekwah.narutomod.capabilities.mastery.MasteryData;
import com.sekwah.narutomod.capabilities.skills.SkillData;
import com.sekwah.narutomod.capabilities.toggleabilitydata.ToggleAbilityData;
import com.sekwah.sekclib.capabilitysync.capabilitysync.RegisterSyncTrackerTypeEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NarutoSyncTrackers {
    @SubscribeEvent
    public static void registerSyncTrackerEvent(RegisterSyncTrackerTypeEvent event) {
        event.registerSyncTracker(ToggleAbilityData.class, new ToggleAbilityDataSyncTracker());
        event.registerSyncTracker(DoubleJumpData.class, new DoubleJumpDataSyncTracker());
        event.registerSyncTracker(MasteryData.class, new MasteryDataSyncTracker());
        event.registerSyncTracker(SkillData.class, new SkillDataSyncTracker());
        event.registerSyncTracker(AbsorptionData.class, new AbsorptionDataSyncTracker());
        event.registerSyncTracker(DisabledAbilityData.class, new DisabledAbilityDataSyncTracker());
    }
}
