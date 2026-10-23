package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;

/**
 * The Senrigan's levitation: chakra held under the caster so an eye-wielder is not nailed to the
 * ground.
 *
 * <p>Every Doujutsu technique until now was a way of seeing or a way of striking, with no way to
 * move through the air; this is the missing half. While the stance is up the caster can fly, and it
 * is paid for by the tick like any other eye power. It needs <b>any</b> open eye rather than a
 * specific one - the flight is a trick of the eye, not a property of a bloodline - so it is taught
 * on the Senrigan board only because that is where the eye's utility lives.</p>
 *
 * <p>The flight state the caster started with is written onto them and put back when the stance
 * ends, so a creative caster keeps their wings and a survival one does not keep them either. The
 * same rule as {@link KamuiPhaseAbility}: a stance must leave the body exactly as it found it.</p>
 */
public class SenriganFlightAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** B V B B B - one key past the sight. */
    public static final long COMBO = 32332L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "senrigan_flight");

    /** Chakra burnt for every tick the body is held up. */
    private static final float CHAKRA_PER_TICK = 0.3f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** NBT on the player holding the flight state to put back when the stance ends. */
    private static final String FLIGHT_TAG = "narutomod:senriganFlight";
    private static final String MAYFLY_TAG = "mayfly";
    private static final String FLYING_TAG = "flying";

    @Override
    public int noiseRadius() {
        return 0;
    }

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
        if (!NarutoConfig.senriganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senrigan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!AbstractDojutsuAbility.anyEyeActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.dojutsu.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
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
        // Mastery is earned by holding the body up, not by flicking the stance on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        grantFlight(player);
        if (player.level() instanceof ServerLevel level && player.tickCount % 5 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                    player.getY() + 0.1d, player.getZ(),
                    1, 0.2d, 0.05d, 0.2d, 0.005d);
        }
    }

    /**
     * A stance is mastered by holding it, not by switching it on, so the credit is paid per active
     * tick from {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // Only the state is synced; the abilities themselves are server-authoritative.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        release(player);
    }

    /** @return whether the caster is being held up by the eye right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /**
     * Hands the caster their wings, remembering what they had before so the stance can give it back.
     */
    private static void grantFlight(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(FLIGHT_TAG)) {
            CompoundTag saved = new CompoundTag();
            saved.putBoolean(MAYFLY_TAG, player.getAbilities().mayfly);
            saved.putBoolean(FLYING_TAG, player.getAbilities().flying);
            tag.put(FLIGHT_TAG, saved);
        }
        if (!player.getAbilities().mayfly || !player.getAbilities().flying) {
            player.getAbilities().mayfly = true;
            player.getAbilities().flying = true;
            player.onUpdateAbilities();
        }
    }

    /**
     * Puts the flight state back exactly as it was before the stance. Safe to call when nothing is
     * being held up.
     */
    public static void release(Player player) {
        CompoundTag tag = player.getPersistentData();
        if (!tag.contains(FLIGHT_TAG)) {
            return;
        }
        CompoundTag saved = tag.getCompound(FLIGHT_TAG);
        tag.remove(FLIGHT_TAG);
        player.getAbilities().mayfly = saved.getBoolean(MAYFLY_TAG);
        player.getAbilities().flying = saved.getBoolean(FLYING_TAG);
        player.onUpdateAbilities();
    }

    /**
     * Called when a caster dies: the toggle set is copied onto the new player, so the stance has to
     * be taken out of it or a respawned caster would still be marked as flying without paying.
     */
    public static void clearOnDeath(Player player, INinjaData ninjaData) {
        release(player);
        ninjaData.getToggleAbilityData().removeAbility(ID);
    }
}
