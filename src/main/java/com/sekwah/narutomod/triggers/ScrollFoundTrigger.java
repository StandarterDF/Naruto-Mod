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
 * Fired the first time a scroll is read, whether it teaches a technique or is a duplicate.
 *
 * <p>An instance may name one scroll (its ability id, e.g. {@code "scroll": "narutomod:kamui"}) or
 * accept any scroll by leaving the field out.</p>
 */
public class ScrollFoundTrigger extends SimpleCriterionTrigger<ScrollFoundTrigger.TriggerInstance> {

    @Override
    public ResourceLocation getId() {
        return NarutoTriggers.SCROLL_FOUND_ID;
    }

    @Override
    protected TriggerInstance createInstance(JsonObject json, ContextAwarePredicate player,
                                             DeserializationContext context) {
        ResourceLocation scroll = json.has("scroll")
                ? ResourceLocation.tryParse(json.get("scroll").getAsString())
                : null;
        return new TriggerInstance(scroll, player);
    }

    public void trigger(ServerPlayer player, ResourceLocation scroll) {
        this.trigger(player, instance -> instance.matches(scroll));
    }

    public static class TriggerInstance extends AbstractCriterionTriggerInstance {

        @Nullable
        private final ResourceLocation scroll;

        public TriggerInstance(@Nullable ResourceLocation scroll, ContextAwarePredicate player) {
            super(NarutoTriggers.SCROLL_FOUND_ID, player);
            this.scroll = scroll;
        }

        public boolean matches(ResourceLocation found) {
            return this.scroll == null || this.scroll.equals(found);
        }
    }
}
