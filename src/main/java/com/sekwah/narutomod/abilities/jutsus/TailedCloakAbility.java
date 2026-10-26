package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

import java.util.List;
import java.util.UUID;

/**
 * The chakra cloak drawn out into a tail: the shell thickens, and whatever stands too close is
 * swept away by it.
 *
 * <p>It is the chakra cloak taken one step further and therefore only exists on top of it - the
 * first cloak has to be up before this one can be pulled over it. What it adds is bulk rather than
 * finesse: eight points of armour, a tail sweep that hits everything hostile within reach every
 * second, and, once every five seconds, a roar that marks out every mob nearby so nothing can hide
 * from the caster in a crowd.</p>
 *
 * <p>The sweep leaves friends and the caster's own clones alone, like every other area effect in
 * the mod, and the roar is loud enough to be heard across a fight, which is the point of a roar.</p>
 */
public class TailedCloakAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A roar carries; the stance itself does not. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B B C A - the tailed cloak seal. */
    public static final long COMBO = 332122L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tailed_cloak");

    /** The mark that says the tailed cloak is up, refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:tailed_cloak_until";

    /** A stable id, so the same modifier is added and taken off rather than stacking. */
    private static final UUID ARMOUR_MODIFIER_ID = UUID.fromString("d2a8f4c6-1b73-49e5-8c0d-7f5a3b9e2c81");

    /** Eight points of armour - four pieces' worth of chakra over the whole body. */
    private static final double ARMOUR_BONUS = 8.0d;

    /** Chakra paid to draw the tail out, before mastery, and every tick it is held. */
    private static final float OPEN_COST = 40.0f;
    private static final float UPKEEP = 0.6f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How often the tail sweeps, how far it reaches and how hard it lands. */
    private static final int SWEEP_INTERVAL = 20;
    private static final double SWEEP_RADIUS = 4.0d;
    private static final float SWEEP_DAMAGE = 4.0f;

    /** How often the cloak roars, and how far the roar is heard by the mobs it marks. */
    private static final int ROAR_INTERVAL = 100;
    private static final double ROAR_RADIUS = 8.0d;
    private static final int ROAR_GLOW_TICKS = 40;

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
        if (!ChakraCloakAbility.isActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chakra_cloak.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }

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
        applyArmour(player);
        player.getPersistentData().putLong(UNTIL_TAG, now + MARK_GRACE);

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        if (opening) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    40, 0.6d, 0.9d, 0.6d, 0.03d);
            level.playSound(null, player, SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.8f, 1.0f);
        } else if (player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.FLAME, player.getX(),
                    player.getY() + player.getBbHeight() * 0.8d, player.getZ(),
                    4, 0.4d, 0.6d, 0.4d, 0.01d);
        }

        if (player.tickCount % SWEEP_INTERVAL == 0) {
            sweep(player, level);
        }
        if (player.tickCount % ROAR_INTERVAL == 0) {
            roar(player, level);
        }
    }

    /** The tail takes everything hostile within reach off its feet, every second. */
    private static void sweep(Player player, ServerLevel level) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SWEEP_RADIUS),
                target -> target.isAlive() && !Friends.isOwnSide(player, target));
        for (LivingEntity target : targets) {
            target.hurt(player.damageSources().magic(), SWEEP_DAMAGE);
            if (target.isAlive()) {
                target.knockback(0.4d, target.getX() - player.getX(), target.getZ() - player.getZ());
            }
        }
        if (!targets.isEmpty()) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                    player.getY() + 1.0d, player.getZ(), 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }

    /**
     * A roar once every five seconds. It does no harm; it marks every mob that hears it, so nothing
     * hostile can slip out of sight while the cloak is up.
     */
    private static void roar(Player player, ServerLevel level) {
        level.playSound(null, player, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS, 1.0f, 0.85f);
        List<LivingEntity> heard = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(ROAR_RADIUS),
                target -> target instanceof Mob && target.isAlive() && !Friends.isOwnSide(player, target));
        for (LivingEntity target : heard) {
            target.addEffect(new MobEffectInstance(MobEffects.GLOWING, ROAR_GLOW_TICKS, 0, true, false));
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, player.getX(),
                player.getY() + 1.2d, player.getZ(), 12, 0.8d, 0.3d, 0.8d, 0.02d);
    }

    /** Re-applied every tick so a lost modifier comes back with the stance. */
    private static void applyArmour(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null && armour.getModifier(ARMOUR_MODIFIER_ID) == null) {
            armour.addTransientModifier(new AttributeModifier(ARMOUR_MODIFIER_ID, "narutomod:tailed_cloak",
                    ARMOUR_BONUS, AttributeModifier.Operation.ADDITION));
        }
    }

    /**
     * Takes the tailed cloak off the caster's attributes. Used when the stance ends and when a mark
     * is found to have expired without the stance (a death, a relog).
     */
    public static void removeModifiers(Player player) {
        AttributeInstance armour = player.getAttribute(Attributes.ARMOR);
        if (armour != null) {
            armour.removeModifier(ARMOUR_MODIFIER_ID);
        }
    }

    /** A stance is mastered by the time it is up, so the credit is paid per tick above. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The attribute is synced from the server, so there is nothing to do on the client.
    }

    /** Dropping the tail takes its mark and its armour with it. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        removeModifiers(player);
    }
}
