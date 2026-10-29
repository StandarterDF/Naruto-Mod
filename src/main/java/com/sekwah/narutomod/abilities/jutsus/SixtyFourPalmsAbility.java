package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Eight Trigrams: Sixty-Four Palms, the whole kata walked on one body.
 *
 * <p>The gentle fist strikes a single tenketsu at a time, and against a body that is already inside
 * arm's reach that is one strike too few: by the time the second palm is thrown the first has been
 * answered. This is the answer - the target is fixed at the start of the hold and then the caster
 * simply does not stop, four palms a second into the same channels until sixty-four of them have
 * landed. Each palm is small on its own; the point is that the sixty-fourth is followed by a body
 * that cannot lift its arms.</p>
 *
 * <p>The hold ends itself after the sixteenth hit, see {@link #MAX_HITS}: sixty-four palms are the
 * whole kata, and charging for a seventeenth would be charging for nothing. The target is fixed
 * rather than re-read from the crosshair, so a caster can keep walking the palms into a body that is
 * trying to circle them - and loses the hold if that body gets away, because a palm that does not
 * reach is not a palm.</p>
 */
public class SixtyFourPalmsAbility extends Ability implements Ability.Channeled, Ability.Cooldown {

    /** Sixty-four palms landing carry about as far as a shout. */
    @Override
    public int noiseRadius() {
        return 10;
    }

    /** V B V V B B V - the eye's seal, then the palm's, then one key for the whole kata. */
    public static final long COMBO = 3132L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "sixty_four_palms");

    /** How far the palms reach at the start of the hold, in blocks: one step, not a spear. */
    private static final double RANGE = 3.0d;

    /** How much slack the fixed target gets before the hold lets go of it, in blocks. */
    private static final double REACH_SLACK = 0.75d;

    /** What opening the kata costs, and the upkeep of standing in it. */
    private static final float BASE_COST = 30.0f;
    private static final float COST_PER_TICK = 0.4f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How long the eye needs before it can walk the kata again, in ticks. */
    private static final int COOLDOWN = 240;

    /** Ticks between two palms, and how many hits carry four palms each. */
    private static final int TICKS_PER_PALM = 4;
    private static final int MAX_HITS = 16;

    /** A single palm of the kata: light on its own, which is why there are sixty-four of them. */
    private static final float PALM_DAMAGE = 1.5f;

    /** Chakra taken out of a ninja target by one palm, capped by what they actually have. */
    private static final float PALM_DRAIN = 4.0f;

    /** How long one palm closes the channels for, refreshed by every palm that follows it. */
    private static final int SEAL_TICKS = 20;

    /** What a body that took the whole kata is left with: no strength and no speed. */
    private static final int FINISH_TICKS = 100;

    /** How many motes are drawn between the hand and the struck body. */
    private static final int LINE_POINTS = 8;

    /**
     * The tag the palms write. The same stamp the single palm and the needles use, so a body cannot
     * be sealed twice over by two techniques that mean the same thing.
     */
    private static final String SEAL_UNTIL_TAG = "narutomod:tenketsu_until";

    /**
     * The body each caster has fixed for the current hold. The ability is a registry singleton, so
     * this map is the only place a hold lives; the entry is dropped when the hold ends.
     */
    private final Map<UUID, UUID> fixed = new HashMap<>();

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

    /** A tap has not got the time to land even the first palm, and fixes no target. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        // The cooldown is only asked on the way in: an open hold must not be cut short by a timer
        // the hold itself started.
        if (chargeAmount <= 0 && this.checkCooldown(player, ninjaData, this.getTranslationKey(ninjaData))) {
            return false;
        }
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // The sixty-fourth palm is the last: ending the channel here stops the upkeep at the same
        // moment instead of letting the caster pay for a kata that has nothing left to strike.
        if (chargeAmount >= MAX_HITS * TICKS_PER_PALM) {
            player.displayClientMessage(Component.translatable("jutsu.sixty_four_palms.spent",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)),
                    true);
            return false;
        }
        LivingEntity target = null;
        if (chargeAmount == 0) {
            // The body is fixed once, at the start. Looking away does not move the kata onto somebody
            // else - that is what makes it a kata rather than a hundred separate palms.
            if (!(Targeting.lookedAtEntity(player, RANGE) instanceof LivingEntity looked)
                    || !canStrike(player, looked)) {
                player.displayClientMessage(Component.translatable("jutsu.fail.sixty_four_palms.notarget",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            target = looked;
        } else if (!this.fixed.containsKey(player.getUUID())) {
            return false;
        }

        // The opening of the kata is paid once, on the first tick; the palms are paid for by the tick.
        float cost = this.masteryCost(ninjaData,
                chargeAmount == 0 ? BASE_COST + COST_PER_TICK : COST_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        // Fixed only once the cast is actually paid for, so a refused opening leaves no stale mark.
        if (target != null) {
            this.fixed.put(player.getUUID(), target.getUUID());
        }
        return true;
    }

    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        // The first palm lands on the fourth tick of the hold, not on the key press, so a caster who
        // lets go at once has struck nothing and paid only the opening.
        if ((ticksChanneled + 1) % TICKS_PER_PALM != 0) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity target = this.fixedTarget(player, level);
        if (target == null) {
            this.loseHold(player, ninjaData, ticksChanneled);
            return;
        }
        if (target.distanceToSqr(player) > (RANGE + REACH_SLACK) * (RANGE + REACH_SLACK)) {
            player.displayClientMessage(Component.translatable("jutsu.sixty_four_palms.lost",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)),
                    true);
            this.loseHold(player, ninjaData, ticksChanneled);
            return;
        }

        strike(level, player, target, (ticksChanneled + 1) / TICKS_PER_PALM);
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        this.fixed.remove(player.getUUID());
        // A hold that got as far as a palm is a cast and carries the cooldown; one that was let go
        // before the fourth tick only cost the chakra it burnt.
        if (ticksActive >= TICKS_PER_PALM - 1) {
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        }
    }

    /**
     * A channel is mastered by the time the palms are walked, so the credit is paid per active tick
     * from {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** @return the fixed body of this hold, or null when it is gone from the level. */
    private LivingEntity fixedTarget(Player player, ServerLevel level) {
        UUID id = this.fixed.get(player.getUUID());
        if (id == null) {
            return null;
        }
        Entity entity = level.getEntity(id);
        if (!(entity instanceof LivingEntity target) || !target.isAlive() || target.isRemoved()) {
            return null;
        }
        return target;
    }

    /**
     * Ends a hold that has nothing left to strike: chakra stops being paid at once rather than
     * waiting for the keys to come up, and the hold is credited as a cast if any palm landed.
     */
    private void loseHold(Player player, INinjaData ninjaData, int ticksChanneled) {
        this.fixed.remove(player.getUUID());
        if (ticksChanneled >= TICKS_PER_PALM - 1) {
            this.registerCooldown(player, ninjaData, this.getTranslationKey(ninjaData));
        }
        ninjaData.setCurrentlyChanneledAbility(player, null);
    }

    /**
     * One palm of the kata: the cut, the chakra it spills, the seal it leaves, and - on the
     * sixteenth - the weight that stays in the body afterwards.
     */
    private void strike(ServerLevel level, Player player, LivingEntity target, int hit) {
        // Every palm has to land. Vanilla keeps a body invulnerable for ten ticks after a hit, which
        // would swallow every strike of the kata but the first: the window is cleared per palm.
        target.invulnerableTime = 0;
        float mastery = player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .map(nd -> Mastery.level(nd, SixtyFourPalmsAbility.ID)).orElse(0.0f);
        target.hurt(player.damageSources().magic(), PALM_DAMAGE * (1.0f + 0.5f * mastery));
        drain(target);
        seal(target, level.getGameTime());

        Vec3 hand = JutsuEffects.handPosition(player);
        Vec3 impact = target.position().add(0.0d, target.getBbHeight() * 0.55d, 0.0d);
        Vec3 span = impact.subtract(hand);
        for (int point = 1; point <= LINE_POINTS; point++) {
            Vec3 at = hand.add(span.scale(point / (double) (LINE_POINTS + 1)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
        level.sendParticles(ParticleTypes.END_ROD, impact.x, impact.y, impact.z,
                4, 0.12d, 0.12d, 0.12d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_STRONG, SoundSource.PLAYERS, 0.35f, 1.5f);

        if (hit >= MAX_HITS) {
            finish(level, target);
        }
    }

    /** The sixty-fourth palm: the body keeps the weight of every node that has just been closed. */
    private static void finish(ServerLevel level, LivingEntity target) {
        target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, FINISH_TICKS, 0, false, false));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FINISH_TICKS, 0, false, false));
        level.sendParticles(ParticleTypes.ENCHANT, target.getX(),
                target.getY() + target.getBbHeight() * 0.55d, target.getZ(),
                18, 0.3d, 0.4d, 0.3d, 0.04d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.2f);
    }

    /** Spills a ninja's chakra. Anything without a pool is left to the damage and the seal. */
    private static void drain(LivingEntity target) {
        if (!(target instanceof Player other)) {
            return;
        }
        other.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            float lost = Math.min(PALM_DRAIN, data.getChakra());
            if (lost > 0.0f) {
                data.useChakra(lost, CHAKRA_REGEN_DELAY);
            }
        });
    }

    /**
     * Closes the struck channels until {@link #SEAL_TICKS} from now. The stamp only ever moves later:
     * a palm that lands just after a longer seal must not shorten it.
     */
    private static void seal(LivingEntity target, long now) {
        CompoundTag data = target.getPersistentData();
        long until = now + SEAL_TICKS;
        if (data.getLong(SEAL_UNTIL_TAG) < until) {
            data.putLong(SEAL_UNTIL_TAG, until);
        }
    }

    /** Anything but the caster, their allies, and the untouchable. */
    private static boolean canStrike(Player caster, LivingEntity target) {
        if (target == caster || target.isRemoved() || target.isSpectator() || !target.isAlive()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        return !target.isAlliedTo(caster) && !Friends.areFriends(caster, target);
    }
}
