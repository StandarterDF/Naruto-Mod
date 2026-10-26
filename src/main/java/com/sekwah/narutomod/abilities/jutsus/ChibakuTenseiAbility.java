package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.jutsuprojectile.ChibakuTenseiEntity;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Chibaku Tensei: the Rinnegan's answer to a battlefield, shaped as a ball of its own ground.
 *
 * <p>It is channelled because the ball has to be built, and the ball grows with every seal held:
 * a tap still drops a small one, while a full hold makes a ball the size of a mountain. Holding is
 * expensive on purpose - the technique is paid for by the tick, not by the cast - so a caster is
 * always choosing between a quick, cheap ball and one that is worth standing still for. Where it
 * lands is where the caster was looking, so the seals are aimed, not dropped on the caster's own
 * feet.</p>
 *
 * <p>The ball itself is a {@link ChibakuTenseiEntity}, which climbs, pulls, shakes and finally drops
 * on its own whether the caster stays to watch or not. Nothing is ever taken out of the world: the
 * ball is drawn, not excavated. The eye has to be open for it, see {@link RinneganAbility}.</p>
 */
public class ChibakuTenseiAbility extends Ability implements Ability.Channeled {

    /**
     * How far a cast of this technique carries as a game event. The ball landing is heard across a
     * village.
     */
    @Override
    public int noiseRadius() {
        return 96;
    }

    /** V B B C V B C - the long seal chain, as it is in 1.7.10. */
    public static final long COMBO = 2331231L;

    /** Ticks of holding at which the ball is as big as it gets. */
    public static final int FULL_CHARGE = 160;

    /** Chakra burnt for every tick it is held. Deliberately brutal: this is not a cheap technique. */
    private static final float CHAKRA_PER_TICK = 2.0f;

    /** Paid once, when the seals are first formed, before the ball starts to build. */
    private static final float OPENING_COST = 40.0f;

    /** What a ball that was barely held is worth, as a fraction of a fully charged one. */
    private static final float MIN_POWER = 0.2f;

    /** How far away the ground under the crosshair can be. */
    private static final double AIM_RANGE = 48.0d;

    /** How far ahead the ball is built when the caster is looking at open sky. */
    private static final double AIM_FALLBACK = 14.0d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** How much of a full charge a hold of this many ticks is worth, from a tap up to a full one. */
    public static float powerFor(int ticks) {
        return MIN_POWER + (1.0f - MIN_POWER) * Mth.clamp(ticks / (float) FULL_CHARGE, 0.0f, 1.0f);
    }

    /** The radius the ball a hold of this many ticks makes will reach. */
    public static float radiusFor(int ticks) {
        return ChibakuTenseiEntity.radiusFor(powerFor(ticks));
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.chibakuTenseiEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chibaku.disabled",
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
        // The first tick is the seal that starts the technique; every tick after it is the ball
        // drinking the caster's chakra to grow. A tap arrives as -1, and is a first tick too.
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (chargeAmount <= 0) {
            cost += this.masteryCost(ninjaData, OPENING_COST);
        }
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 25);
        return true;
    }

    /**
     * The ball forming at the aim point while the seals are held: a sphere of chakra that grows to
     * exactly the size the ball will be, so how long to keep holding is something the caster can
     * see rather than guess.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        Vec3 target = this.aimPoint(level, player);
        float radius = radiusFor(ticksChanneled);
        int steps = Mth.clamp(6 + ticksChanneled / 6, 6, 28);
        for (int i = 0; i < steps; i++) {
            double theta = level.random.nextDouble() * Math.PI * 2.0d;
            double phi = Math.acos(2.0d * level.random.nextDouble() - 1.0d);
            double x = target.x + Math.sin(phi) * Math.cos(theta) * radius;
            double y = target.y + Math.abs(Math.cos(phi)) * radius * 0.7d;
            double z = target.z + Math.sin(phi) * Math.sin(theta) * radius;
            level.sendParticles(ParticleTypes.PORTAL, x, y, z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        level.sendParticles(ParticleTypes.CLOUD, target.x, target.y + 0.5d, target.z,
                3, radius * 0.6d, 0.35d, radius * 0.6d, 0.02d);
        if (ticksChanneled % 10 == 0) {
            level.playSound(null, target.x, target.y, target.z,
                    SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS,
                    0.5f + radius * 0.12f, 0.35f);
        }
        if (ticksChanneled % 5 == 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.x, target.y + 0.5d, target.z,
                    1, radius * 0.5d, 0.2d, radius * 0.5d, 0.0d);
        }
    }

    /**
     * Builds the ball: how big it ends up is decided here from how long the seals were held, and
     * where it is built is the ground the caster was looking at. The chakra has already been paid
     * tick by tick, so letting go never costs anything on its own - a caster who spent the bar on
     * the ball still gets the ball.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A tap arrives as -1, and a hold may stop a tick or two in. Both are still a cast: build
        // the smallest ball rather than returning early, which would look like the technique did
        // nothing when it was only tapped.
        int held = Math.max(0, ticksActive);
        float power = powerFor(held);
        Vec3 target = this.aimPoint(level, player);

        ChibakuTenseiEntity ball = new ChibakuTenseiEntity(player, target.x, target.y, target.z);
        ball.setPower(power);
        level.addFreshEntity(ball);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENDER_DRAGON_GROWL, SoundSource.PLAYERS, 2.0f, 0.4f);
        level.sendParticles(ParticleTypes.PORTAL, target.x, target.y + 0.5d, target.z,
                40, 1.0d, 0.6d, 1.0d, 0.25d);
        level.sendParticles(ParticleTypes.EXPLOSION, target.x, target.y + 0.5d, target.z,
                6, 0.8d, 0.3d, 0.8d, 0.0d);
    }

    /** The technique is mastered by holding it, not by tapping it, so the end of a cast gives nothing. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
        // Earned per tick in handleChannelling, see MASTERY_CREDIT_PER_TICK.
    }

    /**
     * The first free block above the ground under the crosshair, or a point a little ahead when the
     * caster is looking at the sky.
     */
    private Vec3 aimPoint(ServerLevel level, Player player) {
        BlockHitResult hit = Targeting.lookedAtBlock(player, AIM_RANGE);
        int x;
        int z;
        if (hit != null) {
            x = hit.getBlockPos().getX();
            z = hit.getBlockPos().getZ();
        } else {
            Vec3 ahead = player.position().add(player.getLookAngle().scale(AIM_FALLBACK));
            x = Mth.floor(ahead.x);
            z = Mth.floor(ahead.z);
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        return new Vec3(x + 0.5d, y, z + 0.5d);
    }
}
