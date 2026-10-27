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
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/**
 * The chakra cloak drawn out into a tail: the shell thickens, and whatever stands too close is
 * swept away by it.
 *
 * <p>It is the chakra cloak taken one step further and therefore only exists on top of it - the
 * first cloak has to be up before this one can be pulled over it. What it adds is bulk rather than
 * finesse: twelve points of armour, harder hits, faster feet, a higher leap and a quicker arm for
 * the dig, a tail that answers whoever is hunting the caster every second, and, once every five
 * seconds, a roar that marks out every mob nearby so nothing can hide from the caster in a
 * crowd.</p>
 *
 * <p>The tail answers blows, not presence: bystanders, villagers and livestock are left alone, like
 * friends and the caster's own clones, and the roar is loud enough to be heard across a fight,
 * which is the point of a roar.</p>
 */
public class TailedCloakAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A roar carries; the stance itself does not. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C B B C A - the tailed cloak seal. */
    public static final long COMBO = 3221L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tailed_cloak");

    /** The mark that says the tailed cloak is up, refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:tailed_cloak_until";

    /** A stable id, so the same modifier is added and taken off rather than stacking. */
    private static final UUID ARMOUR_MODIFIER_ID = UUID.fromString("d2a8f4c6-1b73-49e5-8c0d-7f5a3b9e2c81");

    /** Twelve points of armour - six pieces' worth of chakra over the whole body. */
    private static final double ARMOUR_BONUS = 12.0d;

    /** Chakra paid to draw the tail out, before mastery, and every tick it is held. */
    private static final float OPEN_COST = 40.0f;
    private static final float UPKEEP = 0.6f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How often the tail sweeps, how far it reaches and how hard it lands. */
    private static final int SWEEP_INTERVAL = 20;
    private static final double SWEEP_RADIUS = 5.0d;
    private static final float SWEEP_DAMAGE = 6.0f;

    /** How often the cloak roars, and how far the roar is heard by the mobs it marks. */
    private static final int ROAR_INTERVAL = 100;
    private static final double ROAR_RADIUS = 8.0d;
    private static final int ROAR_GLOW_TICKS = 40;

    /** How far ahead the mark is kept while the cloak is worn. */
    private static final int MARK_GRACE = 10;

    /** How long the boons are handed out for; refreshed every tick, so this is just a hold time. */
    private static final int EFFECT_DURATION = 40;

    @Override
    public ActivationType activationType() {
        return ActivationType.TOGGLE;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /**
     * Refuses the switch-on before the stance joins the set when the cloak it is pulled over is
     * not up. Without this the tail flicks on and straight back off on the next upkeep tick, and
     * the reason drowns under the enable/disable lines.
     */
    @Override
    public boolean canStartToggle(Player player, INinjaData ninjaData) {
        if (!ChakraCloakAbility.isActive(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chakra_cloak.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        return true;
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
        applyBoons(player);
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

    /** The tail answers blows, not presence: only whoever is hunting the caster is swept. */
    private static void sweep(Player player, ServerLevel level) {
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SWEEP_RADIUS),
                target -> Friends.isAggressor(player, target));
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

    /** Re-applied every tick so lost gifts come back with the stance. */
    private static void applyBoons(Player player) {
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, EFFECT_DURATION, 0, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, EFFECT_DURATION, 0, true, false));
    }

    /** Every blow the tail lands comes with a burst of chakra, so the hit reads as one, and the body
     *  is thrown hard enough to leave a crater. */
    public static void onHit(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.FLAME, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                10, 0.3d, 0.4d, 0.3d, 0.02d);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.8f, 0.9f);
        Vec3 push = target.position().subtract(player.position());
        if (push.lengthSqr() < 0.0001d) {
            push = new Vec3(0.0d, 0.0d, 1.0d);
        }
        push = push.normalize().scale(1.6d);
        target.push(push.x, 0.6d, push.z);
        target.hurtMarked = true;
        extraDamageOnWall(level, target, push);
    }

    /** The rest of the blow, delivered if the thrown body meets a wall on the way out. */
    private static void extraDamageOnWall(ServerLevel level, LivingEntity target, Vec3 push) {
        double dx = push.x * 1.2d;
        double dz = push.z * 1.2d;
        if (level.noCollision(target, target.getBoundingBox().move(dx, 0.0d, dz))) {
            return;
        }
        float damage = (float) (target.getHealth() * 0.5f);
        if (damage < 1.0f) {
            damage = 1.0f;
        }
        target.hurt(target.damageSources().magic(), damage);
        level.sendParticles(ParticleTypes.CLOUD, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                12, 0.3d, 0.3d, 0.3d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 0.6f, 0.7f);
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

    /** Dropping the tail takes its mark, its armour and its boons back off at once. */
    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
        removeModifiers(player);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
        player.removeEffect(MobEffects.DIG_SPEED);
    }

    /** @return whether the cloak is currently worn, asked by anything that leans on the stance. */
    public static boolean isActive(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }
}
