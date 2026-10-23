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
 * Fired whenever a technique is actually cast - after its cost was paid, not when the keys are
 * pressed.
 *
 * <p>An instance may name one technique ({@code "ability": "narutomod:shadow_clone"}), in which case
 * only that technique counts, or leave it out to accept any cast at all.</p>
 */
public class JutsuCastTrigger extends SimpleCriterionTrigger<JutsuCastTrigger.TriggerInstance> {

    @Override
    public ResourceLocation getId() {
        return NarutoTriggers.JUTSU_CAST_ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        ResourceLocation ability = json.has("ability")
                ? ResourceLocation.tryParse(json.get("ability").getAsString())
                : null;
        return new TriggerInstance(ability, player);
    }

    public void trigger(ServerPlayer player, ResourceLocation ability) {
        this.trigger(player, instance -> instance.matches(ability));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        @Nullable
        private final ResourceLocation ability;

        public TriggerInstance(@Nullable ResourceLocation ability, ContextAwarePredicate player) {
            super(NarutoTriggers.JUTSU_CAST_ID, player);
            this.ability = ability;
        }

        public boolean matches(ResourceLocation cast) {
            return this.ability == null || this.ability.equals(cast);
        }
    }
}
