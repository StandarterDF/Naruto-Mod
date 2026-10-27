package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.jutsuprojectile.TailedBallEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The tailed beast bomb: the beast's chakra packed into a black sphere, grown in the hand and
 * thrown.
 *
 * <p>Three seconds of holding grows the sphere from a marble to a barrel: black motes gather onto
 * a shell around the hand that swells as the charge fills. Letting go throws whatever grew - a
 * short hold still throws, only smaller - and a full hold throws itself the moment the third
 * second is up. The thrown bomb is a {@link TailedBallEntity}: it flies where the caster looked
 * and comes apart in a genuine explosion that breaks blocks, sized by the charge behind it.</p>
 *
 * <p>It only exists on top of a cloak - either the tail drawn over the plain chakra shell or one
 * of the sealed vessel's cloaks - or on the crack of a broken seal, because the caster needs the
 * beast's chakra already in the body to have that much of it to gather.</p>
 */
public class TailedBallAbility extends Ability implements Ability.Channeled {

    /** The blast is heard a long way off, which is fair warning. */
    @Override
    public int noiseRadius() {
        return 48;
    }

    /** C C B B C A - the seal, then the three keys that gather it. */
    public static final long COMBO = 3213L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "tailed_ball");

    /** Chakra paid to open the sphere, before mastery, and every tick it is gathered. */
    private static final float OPEN_COST = 80.0f;
    private static final float UPKEEP = 0.5f;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** Three seconds of gathering before the sphere is worth throwing at full size. */
    public static final int CHARGE_TICKS = 60;

    /** One second of gathering before a let-go throws anything at all; shorter is a fizzle. */
    private static final int MIN_CHARGE = 20;

    /** Sphere shell radius while gathering, from a marble to a barrel. */
    private static final double MIN_SPHERE = 0.2d;
    private static final double MAX_SPHERE = 1.0d;

    /**
     * How far ahead of the chest the sphere starts gathering, how much further out it drifts as
     * it grows, and at what height. The drift keeps the swelling shell off the body: the caster's
     * surface is ~0.3 out and the shell reaches 1.0, so the centre rides out ahead of the growth.
     */
    private static final double CHARGE_AHEAD_MIN = 0.8d;
    private static final double CHARGE_AHEAD_GROWTH = 1.2d;
    private static final double CHARGE_HEIGHT = 1.35d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** A tap never gathers anything: the sphere has no instant form. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (chargeAmount < 0) {
            return false;
        }
        if (!hasBeastChakra(player)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.tailed_ball.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, chargeAmount == 0 ? OPEN_COST : UPKEEP);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    /**
     * The gathering. A shell of black motes swells around the hand as the charge fills, and at the
     * third second the grown sphere leaves on its own - the caster does not have to let go of the
     * keys for the throw to happen.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        drawGrowingSphere(level, player, ticksChanneled);

        if (ticksChanneled >= CHARGE_TICKS) {
            this.fire(player, level, ticksChanneled);
            ninjaData.setCurrentlyChanneledAbility(player, null);
        }
    }

    /**
     * Where the sphere is gathered: ahead of the caster at mouth height, along the flat facing
     * direction rather than the gaze, so looking down does not bury it in the floor. The centre
     * rides further out as the shell swells, so the growth never touches the caster; this point
     * is what the bomb is thrown from too, so it leaves from where it grew.
     *
     * @param filled how grown the sphere is, 0 to 1
     */
    private static Vec3 chargeCenter(Player player, float filled) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        flat = flat.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flat.normalize();
        double ahead = CHARGE_AHEAD_MIN + CHARGE_AHEAD_GROWTH * filled;
        return player.position().add(flat.scale(ahead)).add(0.0d, CHARGE_HEIGHT, 0.0d);
    }

    /**
     * The shell the bomb is gathered into: black motes on latitude rings around the gathering
     * point ahead of the caster, the whole shell swelling with the charge and slowly turning,
     * with beast-fire licking through it.
     */
    private static void drawGrowingSphere(ServerLevel level, Player player, int ticksChanneled) {
        float filled = Mth.clamp(ticksChanneled / (float) CHARGE_TICKS, 0.0f, 1.0f);
        double radius = MIN_SPHERE + (MAX_SPHERE - MIN_SPHERE) * filled;
        Vec3 center = chargeCenter(player, filled);

        int rings = 3 + (int) (filled * 3.0f);
        int points = 6 + (int) (filled * 6.0f);
        double spin = ticksChanneled * 0.25d;
        for (int ring = 0; ring <= rings; ring++) {
            double theta = Math.PI * ring / rings;
            double ringRadius = Math.sin(theta) * radius;
            double y = center.y + Math.cos(theta) * radius;
            for (int i = 0; i < points; i++) {
                double angle = Math.PI * 2.0d * i / points + spin;
                level.sendParticles(ParticleTypes.SQUID_INK,
                        center.x + Math.cos(angle) * ringRadius, y, center.z + Math.sin(angle) * ringRadius,
                        1, 0.0d, 0.0d, 0.0d, 0.0d);
            }
        }
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, center.x, center.y, center.z,
                3, radius * 0.5d, radius * 0.5d, radius * 0.5d, 0.01d);
    }

    /**
     * Throws whatever grew: a full charge at full size, a shorter hold smaller. The size, the
     * direct hit and the blast all ride in the entity, scaled by the charge.
     */
    private void fire(Player player, ServerLevel level, int ticksChanneled) {
        float power = Mth.clamp(ticksChanneled / (float) CHARGE_TICKS, 0.0f, 1.0f);
        TailedBallEntity bomb = new TailedBallEntity(player, 1.0d, power);
        Vec3 center = chargeCenter(player, power);
        bomb.setPos(center.x, center.y, center.z);
        level.addFreshEntity(bomb);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
    }

    /**
     * The beast's chakra has to be in the body already: any tailed cloak will do, from the first
     * drawn tail through the divided and perfect cloaks, or the crack a broken seal leaves open.
     */
    private static boolean hasBeastChakra(Player player) {
        if (PerfectCloakAbility.isActive(player) || SealBreakAbility.isActive(player)) {
            return true;
        }
        var data = player.getCapability(com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return false;
        }
        var active = data.getToggleAbilityData().getAbilitiesHashSet();
        return active.contains(TailedCloakAbility.ID) || active.contains(DividedCloakAbility.ID);
    }

    /**
     * A sphere is not a cast, it is a hold: the gathering is credited per tick above and the release
     * is not a second cast.
     */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /**
     * Letting go early throws the smaller sphere that had time to grow; letting go at once fizzles.
     * A full hold never reaches here - it throws itself in {@link #handleChannelling}.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        if (ticksActive >= CHARGE_TICKS) {
            return;
        }
        if (ticksActive < MIN_CHARGE) {
            level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
            return;
        }
        this.fire(player, level, ticksActive);
    }
}
