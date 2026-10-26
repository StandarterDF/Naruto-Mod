package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * Beast carry: a small creature is hoisted onto the caster's shoulders and held there.
 *
 * <p>The technique is a grip, not a throw. A ninja who has it can pick up a dog, a goat, a chicken or
 * a wounded ally's summon and simply keep it - the creature rides above the caster's head for as long
 * as the chakra holds out, unable to run, unable to bite, and slowed to whatever the caster can carry.
 * It is how a beast is taken alive, or kept out of a fight it would lose.</p>
 *
 * <p>Only something that can reasonably be lifted answers the grip: people and anything with a
 * monster's worth of health are too big to shoulder. The class records which creature was grabbed
 * ({@link #CARRIED_KEY}) and how long the grip is still paid for ({@link #CARRY_UNTIL_KEY}) in the
 * caster's persistent data, because the hooks that actually pin the creature and slow it down read
 * those two tags rather than searching the world. {@link #carried(Player)} and {@link #clear(Player)}
 * are the tab the hooks use to read and drop the grip.</p>
 */
public class BeastCarryAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** C C A B - the wind branch's beast seal. */
    public static final long COMBO = 3312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "beast_carry");

    /** UUID of the creature on the caster's shoulders, held in the caster's persistent data. */
    public static final String CARRIED_KEY = "narutomod:carried_entity";

    /** Game time the grip has been paid up to; a lease, so a lost stance stops pinning at once. */
    public static final String CARRY_UNTIL_KEY = "narutomod:carry_until";

    /** Close enough to grab: a little beyond arm's reach, no further. */
    private static final double RANGE = 4.0d;

    /** Chakra per tick while the creature is held. */
    private static final float COST_PER_TICK = 0.15f;
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** Anything this tough is not a beast to be shouldered - it is a boss. */
    private static final float BEAST_MAX_HEALTH = 100.0f;

    /**
     * How long the grip stays valid after the last paid tick. It only has to outlast a tick or two of
     * lag; keeping it short means a caster who dies or logs out does not leave a creature pinned in
     * the air by a tag nobody is refreshing.
     */
    private static final int LEASE_TICKS = 40;

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
        // Refused rather than paid for when there is nothing to lift: a stance that costs chakra and
        // holds empty air is not what the caster asked for.
        if (carried(player) == null && findTarget(player) == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.beast_carry.notarget",
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
        CompoundTag tag = player.getPersistentData();
        if (!tag.hasUUID(CARRIED_KEY)) {
            LivingEntity beast = findTarget(player);
            if (beast != null) {
                tag.putUUID(CARRIED_KEY, beast.getUUID());
                player.displayClientMessage(Component.translatable("jutsu.beast_carry.lifted",
                        beast.getDisplayName()).withStyle(ChatFormatting.GREEN), true);
            }
        }
        tag.putLong(CARRY_UNTIL_KEY, player.level().getGameTime() + LEASE_TICKS);
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
    }

    /** The creature currently held by this caster, or null when the shoulders are empty. */
    @Nullable
    public static UUID carried(Player player) {
        CompoundTag tag = player.getPersistentData();
        return tag.hasUUID(CARRIED_KEY) ? tag.getUUID(CARRIED_KEY) : null;
    }

    /** Drops the grip: the hooks stop pinning whatever was held and the caster is free again. */
    public static void clear(Player player) {
        CompoundTag tag = player.getPersistentData();
        tag.remove(CARRIED_KEY);
        tag.remove(CARRY_UNTIL_KEY);
    }

    /** The creature the caster is looking at that is small enough to carry, or null. */
    @Nullable
    private static LivingEntity findTarget(Player player) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity beast)) {
            return null;
        }
        if (beast == player || !beast.isAlive() || beast.isSpectator()) {
            return null;
        }
        // People are not beasts, and neither is anything built to take an army.
        if (beast instanceof Player || beast.getMaxHealth() >= BEAST_MAX_HEALTH) {
            return null;
        }
        return beast;
    }

    /**
     * A stance is mastered by the time it is up, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The creature is pinned by the server; the client has nothing to hold on to.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        clear(player);
    }
}
