package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.abilities.Nature;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.util.ElementalStates;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * The ninja's own element carried into the steel.
 *
 * <p>A sword cuts, and that is all it does; a ninja who was born to fire should not have to choose
 * between the blade and the element they understand best. Held up, the steel takes on the nature of
 * its owner: every blow carries a little of it past the plate, and whatever is struck is left in the
 * state that element makes - burning, soaked through, or dried out by the wind.</p>
 *
 * <p>The element is read from the caster every blow rather than fixed at the cast, so a ninja who
 * changes their nature keeps the stance and it follows them. Earth and wood have no state to leave
 * that the body does not already have, so those two cut with the chakra alone.</p>
 */
public class ElementalEdgeAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** The element on the blade is quiet; the blow that carries it is not part of this technique. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 2 5 2 in the brief; 1 3 2 3 1 2 1 here, because a combo has only three keys. */
    public static final long COMBO = 231L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "elemental_edge");

    /** The mark the melee hook reads: kept a few ticks ahead while the stance is held. */
    public static final String UNTIL_TAG = "narutomod:elemental_edge_until";

    /** What the element adds to a blow, taken past the armour rather than into it. */
    public static final float ELEMENT_DAMAGE = 2.0f;

    /** How long the element's state is left on a body that has been struck. */
    private static final int BURN_SECONDS = 3;

    /** Chakra paid for every tick the stance is up. */
    private static final float COST_PER_TICK = 0.2f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /**
     * How far ahead the mark is kept. It only has to outlive the tick it was written on, but the
     * small grace lets a blow resolved a moment after the stance drops still carry the element.
     */
    private static final int MARK_GRACE = 5;

    /** How often the blade is drawn shimmering, and how many motes. */
    private static final int PARTICLE_INTERVAL = 5;
    private static final int PARTICLE_COUNT = 2;

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
        if (!(player.getMainHandItem().getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.elemental_edge.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // A blade with no element in it is a blade paying an upkeep for nothing.
        if (Nature.byId(ninjaData.getNatureAffinity()) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.elemental_edge.noaffinity",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // A stance is mastered by the time it is held, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_GRACE);

        if (player.level() instanceof ServerLevel level && player.tickCount % PARTICLE_INTERVAL == 0) {
            Nature nature = Nature.byId(ninjaData.getNatureAffinity());
            if (nature != null) {
                Vec3 hand = JutsuEffects.handPosition(player);
                level.sendParticles(particleFor(nature), hand.x, hand.y, hand.z,
                        PARTICLE_COUNT, 0.15d, 0.2d, 0.15d, 0.01d);
            }
        }
    }

    /**
     * One blow carrying the caster's element: damage taken through the armour, and the state the
     * element leaves behind.
     */
    public static void onMeleeHit(Player caster, LivingEntity victim) {
        INinjaData data = caster.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        Nature nature = data == null ? null : Nature.byId(data.getNatureAffinity());
        if (nature == null) {
            return;
        }
        // The element is a separate hit of its own, so the invulnerability window the steel just
        // opened is cleared rather than letting the element go missing.
        victim.invulnerableTime = 0;
        victim.hurt(caster.damageSources().magic(), ELEMENT_DAMAGE);

        switch (nature) {
            case FIRE -> victim.setSecondsOnFire(BURN_SECONDS);
            case WATER -> ElementalStates.refreshWet(victim, ElementalStates.WET_TICKS);
            case WIND -> ElementalStates.dry(victim);
            default -> {
                // Earth and wood break a body; they do not leave it in a state.
            }
        }
    }

    private static net.minecraft.core.particles.SimpleParticleType particleFor(Nature nature) {
        return switch (nature) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case WIND -> ParticleTypes.SWEEP_ATTACK;
            default -> ParticleTypes.CRIT;
        };
    }

    /** @return whether the stance is up right now, asked by the melee hook. */
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
        // The element is read on the server and drawn from there.
    }

    /** Sheathing the blade takes the element out of it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }
}
