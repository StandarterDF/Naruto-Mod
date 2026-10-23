package com.sekwah.narutomod.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired once, the moment a player takes up the shinobi path. The root of the mod's advancement
 * tree hangs off it, so the whole branch opens exactly when the player can actually cast.
 */
public class NinjaAwakenedTrigger extends SimpleCriterionTrigger<NinjaAwakenedTrigger.TriggerInstance> {

    @Override
    public ResourceLocation getId() {
        return NarutoTriggers.NINJA_AWAKENED_ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        return new TriggerInstance(player);
    }

    public void trigger(ServerPlayer player) {
        this.trigger(player, instance -> true);
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        public TriggerInstance(ContextAwarePredicate player) {
            super(NarutoTriggers.NINJA_AWAKENED_ID, player);
        }
    }
}
