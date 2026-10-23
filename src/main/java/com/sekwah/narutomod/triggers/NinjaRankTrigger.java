package com.sekwah.narutomod.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Fired when a ninja is promoted. The rank name is a plain string so the advancement files can read
 * it without knowing the enum.
 */
public class NinjaRankTrigger extends SimpleCriterionTrigger<NinjaRankTrigger.TriggerInstance> {

    @Override
    public ResourceLocation getId() {
        return NarutoTriggers.NINJA_RANK_ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        String rank = json.has("rank") ? json.get("rank").getAsString() : "";
        return new TriggerInstance(rank, player);
    }

    public void trigger(ServerPlayer player, String rank) {
        this.trigger(player, instance -> instance.matches(rank));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        private final String rank;

        public TriggerInstance(String rank, ContextAwarePredicate player) {
            super(NarutoTriggers.NINJA_RANK_ID, player);
            this.rank = rank;
        }

        public boolean matches(String promoted) {
            return this.rank.isEmpty() || this.rank.equals(promoted);
        }
    }
}
