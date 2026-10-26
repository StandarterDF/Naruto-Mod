package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.BleedingBladeAbility;
import com.sekwah.narutomod.abilities.jutsus.ElementalEdgeAbility;
import com.sekwah.narutomod.abilities.jutsus.HiramekareiAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterHandAbility;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the kenjutsu stances do to a blow that is already landing.
 *
 * <p>Four of the sword stances are nothing but a claim about the next hit: the hammer is heavier, the
 * edge leaves a wound open, the element rides the steel, the water hand pushes what it touches. None
 * of that can be done from the technique, because a technique is not told when a blow connects - the
 * caster's own hits resolve in the damage pipeline and nowhere else. So the stances write a mark
 * while they are up, and this class reads the mark at the one moment it means something.</p>
 *
 * <p>Every branch is a no-op when the mark has run out, so the common case - a player swinging a
 * sword with no stance up - costs four long comparisons and nothing else.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KenjutsuStanceEvents {

    private KenjutsuStanceEvents() {
    }

    /**
     * A melee blow from a caster holding a kenjutsu stance: the weight of the hammer, the wound of
     * the bleeding blade, the element on the edge, and the push of the water hand.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        // Only a blow the caster threw with their own hand: a projectile or a technique of theirs is
        // not a sword stroke, and the stances all speak about the blade.
        if (!(event.getSource().getDirectEntity() instanceof Player caster)
                || event.getSource().getEntity() != caster) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == caster) {
            return;
        }

        if (HiramekareiAbility.isUp(caster)) {
            event.setAmount(event.getAmount() * HiramekareiAbility.DAMAGE_FACTOR);
            if (caster.isShiftKeyDown()) {
                HiramekareiAbility.crushUnderfoot(caster, victim);
            }
        }
        if (BleedingBladeAbility.isUp(caster)) {
            BleedingBladeAbility.onMeleeHit(caster, victim);
        }
        if (ElementalEdgeAbility.isUp(caster)) {
            ElementalEdgeAbility.onMeleeHit(caster, victim);
        }
        if (WaterHandAbility.isUp(caster)) {
            WaterHandAbility.onMeleeHit(caster, victim);
        }
    }
}
