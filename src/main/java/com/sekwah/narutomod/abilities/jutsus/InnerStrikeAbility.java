package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * A palm that goes through the guard instead of against it.
 *
 * <p>Everything else in the taijutsu branch is about the surface of a body: where to hit, how hard,
 * how many times. This one is about the inside. A short palm to the chest does very little to the
 * skin and a great deal to whatever the body was in the middle of doing - a hand seal comes apart,
 * a held technique is lost, and a creature that thinks with a borrowed mind is left standing still
 * for a second while it finds its feet again.</p>
 *
 * <p>It is deliberately cheap, quick and short-ranged. It is not a finisher but an interruption,
 * spent to break the one technique that was about to land rather than to add another number to the
 * damage log.</p>
 */
public class InnerStrikeAbility extends Ability implements Ability.Cooldown {

    /** A palm into a body is heard about as far as a body being hit. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** 1 3 2 3 2 2 - the palm seal doubled. */
    public static final long COMBO = 113L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "inner_strike");

    /** How far a palm can reach; this is arm's length, not a lunge. */
    private static final double RANGE = 2.0d;

    /** What the palm costs and how long the arm needs before the next one. */
    private static final float COST = 10.0f;
    private static final int STAMINA_REGEN_DELAY = 15;
    private static final int COOLDOWN = 80;

    /** The strike: four points in all, half of it carried straight past armour into the body. */
    private static final float DAMAGE = 4.0f;
    private static final float MAGIC_DAMAGE = 2.0f;

    /** How long a creature with no technique to break is left standing still instead. */
    private static final int NO_AI_TICKS = 20;

    /** The mark the mark-watcher restores a mob's mind from. */
    public static final String UNTIL_TAG = "narutomod:inner_strike_until";

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target) || !canStrike(player, target)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.inner_strike.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Looked for again rather than carried over from the cost check: the target may have moved
        // out of reach in the tick between the two, and a stale reference would strike empty air.
        if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity target) || !canStrike(player, target)) {
            return;
        }

        target.hurt(player.damageSources().playerAttack(player), DAMAGE - MAGIC_DAMAGE);
        // The inner half of the palm is not stopped by plate, and a body still inside the
        // invulnerability window of the first half would otherwise shrug it off entirely.
        target.invulnerableTime = 0;
        target.hurt(player.damageSources().magic(), MAGIC_DAMAGE);

        breakConcentration(target);

        Vec3 palm = target.position().add(0.0d, target.getBbHeight() * 0.6d, 0.0d);
        level.sendParticles(ParticleTypes.CRIT, palm.x, palm.y, palm.z,
                10, 0.2d, 0.2d, 0.2d, 0.05d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.8f, 1.2f);
    }

    /**
     * Takes whatever the body was holding: a channelled technique is dropped where it stands, and a
     * creature with nothing to drop is simply left without its mind for a moment.
     */
    private static void breakConcentration(LivingEntity target) {
        if (!target.isAlive()) {
            return;
        }
        if (target instanceof Player other) {
            other.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                    .ifPresent(data -> data.setCurrentlyChanneledAbility(other, null));
            return;
        }
        if (target instanceof Mob mob) {
            mob.setNoAi(true);
            mob.getPersistentData().putLong(UNTIL_TAG, mob.level().getGameTime() + NO_AI_TICKS);
        }
    }

    /** Anything alive is fair game except the caster, their allies and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
