package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * Shadow Imitation: the caster's shadow runs out to a body and holds it still.
 *
 * <p>It is a held technique, not a strike. While the seal is kept and the caster stands still, the
 * looked-at creature is rooted where it stands: its own goals are switched off, its legs are heavy,
 * and it cannot turn on the caster. The price grows with the body being held - a chicken costs
 * almost nothing and a hundred-health brute drains a pool in a few seconds - so the technique is a
 * question of whether the caster can afford to keep the thing they caught.</p>
 *
 * <p>The hold is a mark on the victim, refreshed every tick. {@code ShadowGrabEvents} gives the body
 * its own mind back when the mark expires, which is what keeps a stopped server or a caster who
 * logged out from leaving a mob frozen forever.</p>
 */
public class ShadowGrabAbility extends Ability implements Ability.Channeled {

    /** A shadow moving across the ground is quiet; the strain is in the caster, not the air. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B A B A C C - the Nara seal. */
    public static final long COMBO = 22L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "shadow_grab");

    /** Game time until which the victim is held. Refreshed every active tick. */
    public static final String UNTIL_TAG = "narutomod:shadow_grab_until";

    /** UUID of the caster holding the shadow. */
    public static final String SOURCE_TAG = "narutomod:shadow_grab_source";

    /** How far the shadow can reach. */
    private static final double RANGE = 16.0d;

    /** Chakra paid to open the hold; the upkeep then scales with the victim's health. */
    private static final float OPEN_COST = 15.0f;

    /** Base upkeep per tick plus a share of the victim's maximum health. */
    private static final float BASE_UPKEEP = 0.4f;
    private static final float UPKEEP_PER_HEALTH = 0.035f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How far ahead the mark is kept. */
    private static final int MARK_GRACE = 5;

    /** A stunned body is also slowed so the pose reads as a hold rather than a frozen mob. */
    private static final int SLOW_AMPLIFIER = 6;
    private static final int SLOW_TICKS = 15;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** A tap has nothing to hold: the seal has to be kept. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        Mob target = findTarget(player);
        if (target == null) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.shadow_grab.notarget",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
            }
            return false;
        }
        float base = chargeAmount == 0 ? OPEN_COST : BASE_UPKEEP + target.getMaxHealth() * UPKEEP_PER_HEALTH;
        float cost = this.masteryCost(ninjaData, base);
        if (ninjaData.getChakra() < cost) {
            if (chargeAmount == 0) {
                player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            }
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
        Mob target = findTarget(player);
        if (target == null) {
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        hold(target, player, level);
        drawShadow(level, player, target);
    }

    /** Stamps the hold on the victim and reads the stance from it. */
    private static void hold(Mob target, Player caster, ServerLevel level) {
        CompoundTag data = target.getPersistentData();
        data.putLong(UNTIL_TAG, level.getGameTime() + MARK_GRACE);
        data.putString(SOURCE_TAG, caster.getStringUUID());
        target.setNoAi(true);
        target.setTarget(null);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS,
                SLOW_AMPLIFIER, false, false));
    }

    /** The shadow on the ground between the two bodies, so the hold is visible from either side. */
    private static void drawShadow(ServerLevel level, Player caster, Mob target) {
        Vec3 from = caster.position();
        Vec3 to = target.position();
        int points = 12;
        for (int i = 1; i <= points; i++) {
            Vec3 at = from.add(to.subtract(from).scale(i / (double) (points + 1)));
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 0.05d, at.z,
                    1, 0.05d, 0.0d, 0.05d, 0.0d);
        }
        if (caster.tickCount % 10 == 0) {
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.SCULK_BLOCK_SPREAD, SoundSource.PLAYERS, 0.35f, 0.7f);
        }
    }

    /** The looked-at mob, if it is alive and not on the caster's side. */
    private static Mob findTarget(Player player) {
        if (Targeting.lookedAtEntity(player, RANGE) instanceof Mob mob
                && mob.isAlive() && !mob.isSpectator() && !mob.isRemoved()
                && !mob.isAlliedTo(player) && !Friends.areFriends(player, mob)) {
            return mob;
        }
        return null;
    }

    /**
     * Releasing the seal (or running dry) gives every body this caster was holding its own mind back.
     * The lookup is by the mark rather than by a remembered reference, so a target that was swapped
     * or unloaded mid-hold is still cleaned up.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        List<Mob> held = level.getEntitiesOfClass(Mob.class,
                player.getBoundingBox().inflate(RANGE * 2.0d),
                mob -> player.getStringUUID().equals(mob.getPersistentData().getString(SOURCE_TAG)));
        for (Mob mob : held) {
            mob.getPersistentData().remove(UNTIL_TAG);
            mob.getPersistentData().remove(SOURCE_TAG);
            mob.setNoAi(false);
        }
    }

    /** A held technique is credited per tick, not per activation. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }
}
