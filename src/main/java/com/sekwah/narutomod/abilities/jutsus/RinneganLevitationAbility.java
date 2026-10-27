package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The Rinnegan's pull held from below rather than from the front: the caster keeps one hand on a
 * body and simply refuses it the ground.
 *
 * <p>Where {@link HeavenWeightAbility} presses a body down, this is the same hand read the other
 * way. The target loses gravity and rises at a steady, slow pace for as long as the caster holds
 * the channel; the point is not the fall at the end but the helplessness in the middle. The hold
 * ends on its own when the target dies, leaves the caster's reach, or the eye closes, and a body
 * that comes down this way is not credited with the fall.</p>
 *
 * <p>The eye must stay open: the lift is the Rinnegan's force, not a free levitation. The opening
 * cost is paid on the first tick of the hold and the upkeep on every tick after it, so a tap that
 * never holds costs the opening and nothing else.</p>
 */
public class RinneganLevitationAbility extends Ability implements Ability.Channeled {

    /** Holding a body in the air is heard as a low, continuous pull. */
    @Override
    public int noiseRadius() {
        return 10;
    }

    /** C V V C V C - the Rinnegan (C V V C) and two more keys. */
    public static final long COMBO = 12121L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "rinnegan_levitation");

    /**
     * The mark a lifted body carries while the hold lasts. It lets the event class put gravity back
     * if the caster vanishes mid-channel, which would otherwise leave a body floating forever.
     */
    public static final String LEVITATION_UNTIL = "narutomod:rinnegan_levitation_until";

    /** How far down the look ray the hand can take hold. */
    private static final double RANGE = 24.0d;

    /** How far the target may drift before the hold lets go. */
    private static final double LEASH = 40.0d;

    /** The opening cost of the hold, paid once, and the upkeep paid on every tick after it. */
    private static final float OPENING_COST = 30.0f;
    private static final float COST_PER_TICK = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How fast the held body rises, in blocks per tick. */
    private static final double RISE_SPEED = 0.12d;

    /** How long the lift mark survives after its last refresh, in ticks. */
    private static final long MARK_TICKS = 20L;

    /** The body each caster currently has hold of. Cleared when the hold ends. */
    private final Map<UUID, UUID> lifted = new HashMap<>();

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** The hold has to be held: a tap never has time to lift anything. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (chargeAmount == 0) {
            LivingEntity target = this.resolveTarget(player);
            if (target == null) {
                player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan_levitation.notarget",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            this.lifted.put(player.getUUID(), target.getUUID());
        } else if (!this.lifted.containsKey(player.getUUID())) {
            return false;
        }

        float cost = this.masteryCost(ninjaData,
                chargeAmount == 0 ? OPENING_COST + COST_PER_TICK : COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
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
        UUID targetId = this.lifted.get(player.getUUID());
        if (targetId == null) {
            return;
        }
        Entity entity = level.getEntity(targetId);
        if (!(entity instanceof LivingEntity target) || !this.stillHeld(player, target)) {
            this.lifted.remove(player.getUUID());
            ninjaData.setCurrentlyChanneledAbility(player, null);
            return;
        }

        target.setNoGravity(true);
        target.setDeltaMovement(0.0d, RISE_SPEED, 0.0d);
        target.hurtMarked = true;
        // A lift is deliberate vertical travel; the ground it ends over is not a fall.
        target.fallDistance = 0.0f;
        target.getPersistentData().putLong(LEVITATION_UNTIL, level.getGameTime() + MARK_TICKS);

        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        if (ticksChanneled % 2 == 0) {
            level.sendParticles(ParticleTypes.END_ROD, target.getX(),
                    target.getY() + 0.1d, target.getZ(),
                    3, 0.25d, 0.05d, 0.25d, 0.01d);
        }
    }

    /**
     * Runs when the keys come up or the chakra runs out. The body is set down here rather than left
     * to fall from wherever the hold ended, and the map entry is always removed.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        UUID targetId = this.lifted.remove(player.getUUID());
        if (targetId == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity entity = level.getEntity(targetId);
        if (entity instanceof LivingEntity target) {
            this.setDown(target);
        }
    }

    /**
     * A channelled hold is mastered by the time it is held, so the credit is paid per active tick
     * from {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** The looked-at body, if it is something the eye is allowed to lift. */
    private LivingEntity resolveTarget(Player player) {
        Entity lookedAt = Targeting.lookedAtEntity(player, RANGE);
        if (!(lookedAt instanceof LivingEntity target) || !canAffect(player, target)) {
            return null;
        }
        return target;
    }

    /** @return whether the caught body is still alive, still near, and still not the caster. */
    private boolean stillHeld(Player player, LivingEntity target) {
        return target != player
                && target.isAlive()
                && target.level() == player.level()
                && target.distanceToSqr(player) <= LEASH * LEASH;
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private static boolean canAffect(Player caster, LivingEntity target) {
        if (target == caster || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player player && player.isCreative()) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }

    /** Gives the body its weight back without handing it the fall that the lift caused. */
    private void setDown(LivingEntity target) {
        target.setNoGravity(false);
        target.fallDistance = 0.0f;
        target.getPersistentData().remove(LEVITATION_UNTIL);
        // Bleed off the rise rather than letting it carry into a jump once gravity returns.
        Vec3 motion = target.getDeltaMovement();
        target.setDeltaMovement(motion.x * 0.25d, 0.0d, motion.z * 0.25d);
        target.hurtMarked = true;
    }
}
