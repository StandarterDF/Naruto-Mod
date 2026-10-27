package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
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
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The outer path, used as a leash: the caster holds their hands out and invisible chains pin
 * everything in front of them to the spot.
 *
 * <p>Chains are a good answer to a group because they do not take the group out of the fight - a
 * chained body can still swing, still cast and still block. What it cannot do is close the gap or
 * run away, which is exactly the moment the caster's other paths want. The cone is deliberately
 * narrow: this is an aimed technique, not a blast, and three bodies is all the caster's hands can
 * hold at once.</p>
 *
 * <p>The chains are paid for as they are held - a large opening price and a trickle for every tick
 * afterwards - so a caster cannot leave a group pinned for free while they do something else.</p>
 */
public class OuterPathChainsAbility extends Ability implements Ability.Channeled {

    /** Chains are thrown, not detonated; they carry no further than the caster's voice. */
    @Override
    public int noiseRadius() {
        return 8;
    }

    /** 2 3 3 3 1 2 - the outer run, one key past the Rinbo echo. */
    public static final long COMBO = 2323L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "outer_chains");

    /** What opening the chains costs, and what each held tick costs after that. */
    private static final float BASE_COST = 50.0f;
    private static final float COST_PER_TICK = 1.5f;

    /** How far the cone reaches. */
    private static final double RANGE = 12.0d;

    /** The narrowest cone that still lets a caster sweep across a group without aiming at each body. */
    private static final double CONE_DOT = 0.75d;

    /** How many bodies may be held at once. */
    private static final int MAX_TARGETS = 3;

    /** How long one hold may run before the caster has to throw the chains again. */
    private static final int MAX_TICKS = 120;

    /** How long the slowdown is given for; the mark renews it, so it only has to bridge a tick. */
    private static final int CHAIN_REFRESH = 10;

    /**
     * How far ahead the chain mark is kept. Short, because the mark is the only thing keeping a body
     * pinned: once the channel stops paying, the chains have to let go within a few ticks.
     */
    public static final int HOLD_TICKS = 5;

    /** Slowness amplifier: six is what makes a body crawl rather than jog. */
    public static final int SLOW_AMPLIFIER = 6;

    /** The mark that says a body is being held, read by {@code RinneganExtraEvents}. */
    public static final String UNTIL_TAG = "narutomod:outer_chain_until";

    /** Ticks before the caster's own regeneration resumes after paying for the chains. */
    private static final int CHAKRA_REGEN_DELAY = 10;

    /** How many motes are drawn along one chain. */
    private static final int CHAIN_POINTS = 8;

    /**
     * The bodies each caster's current hold is tied to. They are latched when the chains are thrown
     * so the caster does not have to keep the crosshair on a body that is already caught, the same
     * way a thrown thread stays tied to what it caught.
     */
    private final Map<UUID, List<UUID>> holds = new HashMap<>();

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** Throwing chains is a hold, not a tap: a single tick would never pin anything. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (chargeAmount < 0 || chargeAmount >= MAX_TICKS) {
            return false;
        }
        if (chargeAmount == 0) {
            List<LivingEntity> caught = targets(player);
            if (caught.isEmpty()) {
                player.displayClientMessage(Component.translatable("jutsu.fail.outer_chains.notarget",
                        Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                        .withStyle(ChatFormatting.RED), true);
                return false;
            }
            this.holds.put(player.getUUID(), caught.stream().map(LivingEntity::getUUID).toList());
        } else if (!this.holds.containsKey(player.getUUID())) {
            return false;
        }

        float cost = this.masteryCost(ninjaData, chargeAmount == 0 ? BASE_COST : COST_PER_TICK);
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
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        List<UUID> caught = this.holds.get(player.getUUID());
        if (caught == null) {
            return;
        }
        long now = level.getGameTime();
        for (int i = 0; i < caught.size(); i++) {
            if (!(level.getEntity(caught.get(i)) instanceof LivingEntity target) || !target.isAlive()) {
                continue;
            }
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, CHAIN_REFRESH,
                    SLOW_AMPLIFIER, false, true));
            target.getPersistentData().putLong(UNTIL_TAG, now + HOLD_TICKS);
            drawChain(level, player, target, i);
        }
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        // The hold ends when the keys come up or the chakra runs out: the marks are what actually
        // pin the bodies, so letting go of the map is the whole release.
        this.holds.remove(player.getUUID());
    }

    /**
     * A channel is mastered by the time it is held, so the credit is paid per active tick from
     * {@link #handleChannelling} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** Everything living, hostile to the caster, and inside the cone the gaze cuts out. */
    private static List<LivingEntity> targets(Player caster) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getViewVector(1.0f).normalize();
        return caster.level().getEntitiesOfClass(LivingEntity.class,
                        caster.getBoundingBox().inflate(RANGE), entity -> canCatch(caster, entity, eye, look))
                .stream()
                .sorted(Comparator.comparingDouble(entity -> entity.distanceToSqr(caster)))
                .limit(MAX_TARGETS)
                .toList();
    }

    private static boolean canCatch(Player caster, LivingEntity target, Vec3 eye, Vec3 look) {
        if (target == caster || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        if (Friends.isOwnSide(caster, target)) {
            return false;
        }
        Vec3 to = target.getEyePosition().subtract(eye);
        double distance = to.length();
        if (distance < 0.01d || distance > RANGE) {
            return false;
        }
        return to.normalize().dot(look) >= CONE_DOT;
    }

    /** One chain from one of the caster's hands to a caught body. */
    private static void drawChain(ServerLevel level, Player caster, LivingEntity target, int index) {
        Vec3 start = hand(caster, index % 2 != 0);
        Vec3 end = target.position().add(0.0d, target.getBbHeight() * 0.5d, 0.0d);
        Vec3 span = end.subtract(start);
        for (int i = 1; i <= CHAIN_POINTS; i++) {
            Vec3 at = start.add(span.scale(i / (double) (CHAIN_POINTS + 1)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z,
                    1, 0.01d, 0.01d, 0.01d, 0.0d);
        }
    }

    /**
     * Where one of the caster's hands is. Mirrored by hand rather than taken from
     * {@code JutsuEffects} because the chain has to be able to leave from either side.
     */
    private static Vec3 hand(Player player, boolean left) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        flat = flat.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flat.normalize();
        Vec3 right = new Vec3(-flat.z, 0.0d, flat.x);
        double side = left ? -0.34d : 0.34d;
        return player.position()
                .add(flat.scale(0.30d))
                .add(right.scale(side))
                .add(0.0d, 1.15d, 0.0d);
    }
}
