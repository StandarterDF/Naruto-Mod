package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * A shell of chakra worn over the skin: armour that is not armour, and a hand that hits harder
 * because the chakra is in it.
 *
 * <p>The cloak is a full-body commitment. There is no room in it for shaping another technique -
 * while the mark is up the caster's other casts are refused, see {@link #UNTIL_TAG} - and it leaks
 * chakra every tick, so it is something to put on when the fight has already started rather than
 * something to walk around in. In exchange the caster is harder to cut and hits harder, and cannot
 * be lit up as a target while the chakra burns over them.</p>
 *
 * <p>The two gifts ride on transient attribute modifiers rather than on effects because they have
 * to survive a milk bucket and cannot be stacked: the same two ids are re-applied every tick, so a
 * lost modifier (a reload, a respawn) comes back with the stance.</p>
 */
public class ChakraCloakAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** The chakra shell is a quiet thing; nothing about wearing it carries through the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B B A A - the cloak seal. */
    public static final long COMBO = 1212L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_cloak");

    /**
     * The mark that says the cloak is up. It is also the contract with the parent: while it is in the
     * future no other technique may be started by this caster.
     */
    public static final String UNTIL_TAG = "narutomod:chakra_cloak_until";

    /** Stable ids, so the same two modifiers are added and taken off rather than stacking. */
    private static final UUID ARMOUR_MODIFIER_ID = UUID.fromString("3f6c2a71-5d84-4b19-8e07-2a9c4f6b1d58");
    private static final UUID DAMAGE_MODIFIER_ID = UUID.fromString("b7e4d190-8c26-4f53-a1b9-6e3d7c0a9254");

    /** Four points of armour - two pieces' worth - and two points of bare-handed force. */
    private static final double ARMOUR_BONUS = 4.0d;
    private static final double DAMAGE_BONUS = 2.0d;

    /** Chakra paid to pull the cloak on, before mastery, and every tick it is worn. */
    private static final float OPEN_COST = 20.0f;
    private static final float UPKEEP = 0.3f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead the mark is kept while the cloak is worn. */
    private static final int MARK_GRACE = 10;

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
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= player.level().getGameTime();
        float cost = this.masteryCost(ninjaData, opening ? OPEN_COST : UPKEEP);
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
        // A stance is mastered by the time it is worn, not by switching it on.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        long now = player.level().getGameTime();
        boolean opening = player.getPersistentData().getLong(UNTIL_TAG) <= now;
        applyModifiers(player);
        // The chakra burning over the body hides it from anything that would mark it out.
        player.removeEffect(MobEffects.GLOWING);
        player.getPersistentData().putLong(UNTIL_TAG, now + MARK_GRACE);

        if (player.level() instanceof ServerLevel level) {
            if (opening) {
                level.sendParticles(ParticleTypes.FLAME, player.getX(),
                        player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                        30, 0.5d, 0.8d, 0.5d, 0.02d);
                level.playSound(null, player, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.6f, 1.4f);
            } else if (player.tickCount % 4 == 0) {
                level.sendParticles(ParticleTypes.FLAME, player.getX(),
                        player.getY() + player.getBbHeight() * 0.7d, player.getZ(),
                        2, 0.3d, 0.45d, 0.3d, 0.01d);
            }
        }
    }

    /** Re-applied every tick so a lost modifier comes back with the stance. */
    private static void applyModifiers(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null && armour.getModifier(ARMOUR_MODIFIER_ID) == null) {
            armour.addTransientModifier(new AttributeModifier(ARMOUR_MODIFIER_ID, "narutomod:chakra_cloak",
                    ARMOUR_BONUS, AttributeModifier.Operation.ADDITION));
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null && damage.getModifier(DAMAGE_MODIFIER_ID) == null) {
            damage.addTransientModifier(new AttributeModifier(DAMAGE_MODIFIER_ID, "narutomod:chakra_cloak_damage",
                    DAMAGE_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * Takes the cloak off the caster's attributes. Used when the stance ends and when a mark is
     * found to have expired without the stance (a death, a relog).
     */
    public static void removeModifiers(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(ARMOUR_MODIFIER_ID);
        }
        AttributeInstance damage = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (damage != null) {
            damage.removeModifier(DAMAGE_MODIFIER_ID);
        }
    }

    /** Whether the cloak is currently worn, asked by anything that leans on the stance. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /**
     * Whether the cloak leaves room in the body for another technique to start. There is no room in
     * a full-body shell for shaping: the tailed cloak pulled over it, the beast bomb thrown from
     * that tail, the sage pair that burns nature energy rather than shaping chakra - the charging
     * and the mode, which the canon wears together with the beast - and the light utility arts
     * that shape nothing - the charge, the fist, the dash, the leap, the double jump and the water
     * walk, everything in the utility package. Everything else is refused while
     * {@link #isActive(Player)} is true, and the parent's cast handlers say which one was turned
     * away.
     */
    public static boolean leavesRoomFor(Player player, Ability ability) {
        if (!isActive(player)) {
            return true;
        }
        return ability instanceof ChakraCloakAbility || ability instanceof TailedCloakAbility
                || ability instanceof TailedBallAbility
                || ability instanceof SageModeAbility
                || ability instanceof SenjutsuChargingAbility
                || ability.getClass().getPackageName()
                        .equals("com.sekwah.narutomod.abilities.utility");
    }

    /** A stance is mastered by the time it is up, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attributes are synced from the server, so there is nothing to do on the client.
    }

    /** Dropping the cloak takes its mark and its armour with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        removeModifiers(player);
    }
}
