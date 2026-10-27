package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Jougan's pure passage: the caster stops being entirely in the world for as long as they hold
 * the stance.
 *
 * <p>The eye already sees the seams between places; this walks the body through one. Collisions are
 * dropped, the body stops being lit, and a wall thinner than a few blocks is crossed by stepping to
 * the first free spot on the far side rather than by slowly pushing through it. It is not the Kamui
 * pocket and not a teleport: the caster stays in the same world, keeps their footing and can be hurt
 * once the body is solid again.</p>
 *
 * <p>Both sides have to agree that the body is out of phase. Vanilla resets {@code noPhysics} at the
 * top of {@code Player#tick}, so the flags are written from {@code LivingTickEvent} - inside that
 * tick and before the movement step - and the mark is what the event reads on the side that does not
 * own the toggle set yet. The flight state is deliberately left alone: this is a passage, not a way
 * to live in the air.</p>
 */
public class JouganPhaseAbility extends Ability implements Ability.Toggled, Ability.HandleEnded {

    /** A passage is silent: nothing here disturbs the world. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** B V V C V C - the Jougan (B V V C) and two more keys. */
    public static final long COMBO = 2132L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "jougan_phase");

    /**
     * The mark the flag hook reads: the tick the passage should be considered live. Removed when the
     * stance ends, so a released body turns solid on the same tick it lets go.
     */
    public static final String UNTIL_TAG = "narutomod:jougan_phase_until";

    /** Chakra burnt for every tick the body stays out of phase. */
    private static final float CHAKRA_PER_TICK = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far ahead of the clock the mark is kept, in ticks. */
    private static final long MARK_TICKS = 5L;

    /** Invisibility is renewed short, so it fades on its own the moment the stance does. */
    private static final int EFFECT_TICKS = 40;

    /** How far past a wall a free landing spot is looked for. */
    private static final double STEP_RANGE = 3.0d;

    /** How finely that span is searched, and how often the search runs. */
    private static final double STEP_PRECISION = 0.25d;
    private static final int STEP_INTERVAL = 2;

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
        if (!NarutoConfig.jouganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!JouganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.jougan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
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
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + MARK_TICKS);

        MobEffectInstance invisibility = player.getEffect(MobEffects.INVISIBILITY);
        if (invisibility == null || invisibility.getDuration() <= EFFECT_TICKS) {
            player.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, EFFECT_TICKS, 0, false, false));
        }

        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (player.tickCount % 4 == 0) {
            level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                    3, 0.25d, 0.4d, 0.25d, 0.03d);
        }
        if (player.tickCount % STEP_INTERVAL == 0) {
            stepThroughWall(level, player);
        }
    }

    /**
     * A stance is mastered by holding it, so the credit is paid per active tick from
     * {@link #performServer} and this records nothing.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    @Override
    public void performToggleClient(Player player, INinjaData ninjaData) {
        // The toggle state is synced; the flags are kept by DojutsuLeftoverEvents on both sides.
    }

    @Override
    public void handleAbilityEnded(Player player, INinjaData ninjaData, int ticksActive) {
        player.getPersistentData().remove(UNTIL_TAG);
    }

    /** @return whether the caster's body is out of phase right now. */
    public static boolean isActive(INinjaData ninjaData) {
        return ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(ID);
    }

    /**
     * @return whether the body should be out of phase, read from the synced toggle on both sides and
     *         from the short-lived mark as a fallback for the tick the toggle is written.
     */
    public static boolean isPhasing(Player player) {
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData != null && isActive(ninjaData)) {
            return true;
        }
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /**
     * Steps the caster to the far side of a wall when their body is inside one, provided the landing
     * spot is free. Only the server moves the body; the client is corrected by the usual teleport.
     */
    private static void stepThroughWall(ServerLevel level, Player player) {
        AABB box = player.getBoundingBox();
        if (level.noCollision(player, box)) {
            return;
        }
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        if (flat.lengthSqr() < 1.0E-4d) {
            return;
        }
        flat = flat.normalize();
        for (double distance = STEP_PRECISION; distance <= STEP_RANGE; distance += STEP_PRECISION) {
            Vec3 offset = flat.scale(distance);
            if (!level.noCollision(player, box.move(offset))) {
                continue;
            }
            Vec3 from = player.position();
            player.teleportTo(from.x + offset.x, from.y + offset.y, from.z + offset.z);
            player.fallDistance = 0.0f;
            player.setDeltaMovement(Vec3.ZERO);
            player.hurtMarked = true;
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x,
                    from.y + player.getBbHeight() * 0.5d, from.z, 10, 0.25d, 0.4d, 0.25d, 0.04d);
            level.sendParticles(ParticleTypes.PORTAL, player.getX(),
                    player.getY() + player.getBbHeight() * 0.5d, player.getZ(), 10, 0.25d, 0.4d, 0.25d, 0.04d);
            return;
        }
    }
}
