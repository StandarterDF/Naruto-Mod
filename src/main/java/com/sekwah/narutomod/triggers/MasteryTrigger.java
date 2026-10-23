package com.sekwah.narutomod.triggers;

import com.google.gson.JsonObject;
import net.minecraft.advancements.critereon.AbstractCriterionTriggerInstance;
import net.minecraft.advancements.critereon.ContextAwarePredicate;
import net.minecraft.advancements.critereon.DeserializationContext;
import net.minecraft.advancements.critereon.SimpleCriterionTrigger;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;

/**
 * Fired when a technique's mastery passes a level, so achievements can ask for "good" rather than
 * "used once".
 *
 * <p>{@code "level"} is a percentage (0-100) and an instance matches when the mastery is at least
 * that high - that keeps the JSON readable and independent of the curve. {@code "ability"} narrows
 * it to one technique; without it, any technique reaching the level counts.</p>
 */
public class MasteryTrigger extends SimpleCriterionTrigger<MasteryTrigger.TriggerInstance> {

    @Override
    public ResourceLocation getId() {
        return NarutoTriggers.MASTERY_ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        ResourceLocation ability = json.has("ability")
                ? ResourceLocation.tryParse(json.get("ability").getAsString())
                : null;
        int level = json.has("level") ? json.get("level").getAsInt() : 0;
        return new TriggerInstance(ability, level, player);
    }

    public void trigger(ServerPlayer player, ResourceLocation ability, int level) {
        this.trigger(player, instance -> instance.matches(ability, level));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        @Nullable
        private final ResourceLocation ability;
        private final int level;

        public TriggerInstance(@Nullable ResourceLocation ability, int level, ContextAwarePredicate player) {
            super(NarutoTriggers.MASTERY_ID, player);
            this.ability = ability;
            this.level = level;
        }

        public boolean matches(ResourceLocation mastered, int actualLevel) {
            if (this.ability != null && !this.ability.equals(mastered)) {
                return false;
            }
            return actualLevel >= this.level;
        }
    }
}
