package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Magen: Mrak - the dark laid over a patch of ground.
 *
 * <p>Everything inside the caster's reach is left without light and without bearings for as long as
 * the seal is held: a fight that was going somewhere stops where it is, and whoever was winning it
 * has to find their feet again before they can find the caster. Nothing is hurt. The point is to
 * break a melee apart, take one body out of the pile and be gone before the rest have worked out
 * which way they were facing.</p>
 *
 * <p>It is not an illusion that fools a body - it is the absence of anything to see - and that is
 * exactly why the eyes see through it. A Byakugan, a Sharingan or a Rinnegan reads chakra rather than
 * light, so a caster who lays this down in front of an open eye has spent their chakra for nothing at
 * all. The caster's own side is spared for the same reason every other area effect in the mod spares
 * it.</p>
 */
public class MrakAbility extends Ability implements Ability.Channeled, Ability.Cooldown {

    /** The dark is laid in silence. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C B C C A C - the genjutsu combination (C B C), then the branch's fifth step. */
    public static final long COMBO = 13131L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "mrak");

    /** How far from the caster the dark reaches. */
    private static final double RANGE = 12.0d;

    /** What opening the seal costs, and what every tick of holding it costs on top. */
    private static final float OPENING_COST = 40.0f;
    private static final float COST_PER_TICK = 0.4f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How long before the eye can put out the lights again: a minute and a half. */
    private static final int COOLDOWN = 1800;

    /**
     * How long each effect is given. It is renewed every tick, so this is only how long the dark
     * lingers after the caster lets go - long enough to leave, short enough that nobody is left
     * blind once the technique is over.
     */
    private static final int EFFECT_TICKS = 20;

    private static final int DARKNESS_AMPLIFIER = 0;

    /** The shell of ink drawn around the caster: radius, lift, and how many motes it is made of. */
    private static final double SPHERE_RADIUS = 5.0d;
    private static final double SPHERE_LIFT = 1.0d;
    private static final int SPHERE_POINTS = 18;

    /** The spacing that spreads points evenly over a sphere, so the shell has no seams in it. */
    private static final double GOLDEN_ANGLE = Math.PI * (3.0d - Math.sqrt(5.0d));

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    /** The seal has to be held for the dark to settle; a tap has no time to blind anything. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        // The cooldown is asked on the way in only: an open hold must not be cut short by the timer
        // the hold itself started.
        if (chargeAmount <= 0
                && this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        float cost = this.masteryCost(ninjaData,
                chargeAmount == 0 ? OPENING_COST + COST_PER_TICK : COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (ticksChanneled == 0) {
            // The dark was laid: the cooldown belongs to the cast, not to a hold let go at once.
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        }

        drawSphere(level, player);
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(RANGE))) {
            // The search box is a cube and its corners reach further than the technique does.
            if (!isCaught(player, target) || target.distanceToSqr(player) > RANGE * RANGE) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.DARKNESS, EFFECT_TICKS,
                    DARKNESS_AMPLIFIER, false, true));
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, EFFECT_TICKS,
                    0, false, true));
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // Nothing to undo: the effects were given short durations and run out on their own.
    }

    /** A channel is mastered by the time it is held, not by the opening seal. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * @return whether the body is one the dark is allowed to take: not the caster, not their side,
     *         and not an eye that reads chakra rather than light.
     */
    private static boolean isCaught(Player caster, LivingEntity target) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (Friends.isOwnSide(caster, target)) {
            return false;
        }
        if (target instanceof Player other) {
            // A player is a political target on a server, so it is behind the same flag every other
            // illusion on a player sits behind; a mob is not.
            if (!NarutoConfig.genjutsuOnPlayers || other.isCreative()) {
                return false;
            }
            return !seesThrough(other);
        }
        return true;
    }

    /** @return whether the player's eye is open and reading the chakra behind the darkness. */
    private static boolean seesThrough(Player player) {
        return player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .map(data -> ByakuganAbility.isActive(data)
                        || SharinganAbility.isActive(data)
                        || RinneganAbility.isActive(data))
                .orElse(false);
    }

    /**
     * Draws the dark as a shell of ink around the caster. The shell is turned a little every tick so
     * the shape reads as living shadow rather than as a fixed ring of particles.
     */
    private static void drawSphere(ServerLevel level, Player caster) {
        long phase = level.getGameTime() % SPHERE_POINTS;
        for (int i = 0; i < SPHERE_POINTS; i++) {
            double height = (i + 0.5d) / SPHERE_POINTS;
            double polar = Math.acos(1.0d - 2.0d * height);
            double azimuth = GOLDEN_ANGLE * (i + phase);
            double x = Math.sin(polar) * Math.cos(azimuth);
            double z = Math.sin(polar) * Math.sin(azimuth);
            double y = Math.cos(polar);
            level.sendParticles(ParticleTypes.SQUID_INK,
                    caster.getX() + x * SPHERE_RADIUS,
                    caster.getY() + SPHERE_LIFT + y * SPHERE_RADIUS * 0.6d,
                    caster.getZ() + z * SPHERE_RADIUS,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
    }
}
