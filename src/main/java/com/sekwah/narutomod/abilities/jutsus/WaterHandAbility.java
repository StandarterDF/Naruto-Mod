package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeMod;

import java.util.UUID;

/**
 * The water hand: a limb of water worn over the arm rather than thrown.
 *
 * <p>Water release does not have to leave the body. Held close, a film of it stretches the arm a
 * little past where the shoulder says it ends, which is the difference between a blow that lands and
 * one that falls short, and it pushes what it touches back the way a wave does. The same film keeps
 * the caster wet, so their own sleeves never catch.</p>
 *
 * <p>The reach is a transient attribute while the stance is up, and the push is read off the mark by
 * the melee hook: a technique cannot see a blow, so it can only leave word that it is there. Both go
 * with the stance, so a caster who lets it drop is back to the length of their own arm at once.</p>
 */
public class WaterHandAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** Water in the sleeve is quiet; the blow it lands is not part of this technique. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 2 3 3 - the kenjutsu seals and the two keys of the water hand. */
    public static final long COMBO = 233L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "water_hand");

    /** The mark the melee hook reads: kept a few ticks ahead while the hand is worn. */
    public static final String UNTIL_TAG = "narutomod:water_hand_until";

    /** What opening the hand costs, and what holding it costs every tick. */
    private static final float OPEN_COST = 15.0f;
    private static final float UPKEEP = 0.1f;

    private static final int STAMINA_REGEN_DELAY = 10;

    /** How much further the arm reaches, in blocks, and how hard the water pushes. */
    private static final double REACH_BONUS = 2.0d;
    private static final double PUSH = 0.5d;

    /** A stable id, so the same modifier is added and taken off rather than stacking. */
    private static final UUID REACH_MODIFIER_ID = UUID.fromString("c1a7f930-26e5-4b0d-9f83-6a5172d8be47");

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but the
     * small grace lets a blow resolved a moment after the stance drops still push.
     */
    private static final int MARK_GRACE = 5;

    /** The film is only drawn every few ticks: enough to be seen, not enough to fog the screen. */
    private static final int PARTICLE_INTERVAL = 4;
    private static final int PARTICLE_COUNT = 3;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        boolean opening = !isUp(player);
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST + UPKEEP : UPKEEP);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null && reach.getModifier(REACH_MODIFIER_ID) == null) {
            reach.addTransientModifier(new AttributeModifier(REACH_MODIFIER_ID, "narutomod:water_hand",
                    REACH_BONUS, AttributeModifier.Operation.ADDITION));
        }

        // The film is water: it puts out whatever the caster caught fire on, every tick, so a body
        // standing in its own flames is not slowly cooked while paying for the stance.
        player.clearFire();
        if (player.getRemainingFireTicks() > 0) {
            player.setRemainingFireTicks(0);
        }

        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        if (player.level() instanceof ServerLevel level && player.tickCount % PARTICLE_INTERVAL == 0) {
            Vec3 hand = JutsuEffects.handPosition(player);
            level.sendParticles(ParticleTypes.SPLASH, hand.x, hand.y, hand.z,
                    PARTICLE_COUNT, 0.2d, 0.2d, 0.2d, 0.02d);
        }
    }

    /** The water pushes what the fist lands on away from the caster, half a block's worth. */
    public static void onMeleeHit(Player caster, LivingEntity victim) {
        Vec3 away = new Vec3(victim.getX() - caster.getX(), 0.0d, victim.getZ() - caster.getZ());
        if (away.lengthSqr() < 1.0E-4d) {
            away = caster.getViewVector(1.0f);
        }
        away = away.normalize().scale(PUSH);
        victim.setDeltaMovement(away.x, victim.getDeltaMovement().y, away.z);
        victim.hurtMarked = true;
    }

    /** @return whether the hand is worn right now, asked by the melee hook. */
    public static boolean isUp(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /** @return whether the stance is in the synced toggle set, for anything that asks the data. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /** The stance is mastered by holding it, so the tick credit is paid from {@link #performServer}. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The reach is synced from the server; the film is drawn from the server's particles.
    }

    /** Letting the hand go takes the reach and the mark with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        AttributeInstance reach = player.getAttribute(ForgeMod.ENTITY_REACH.get());
        if (reach != null) {
            reach.removeModifier(REACH_MODIFIER_ID);
        }
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
