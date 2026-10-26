package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.projectile.ShurikenEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

/**
 * The scroll of blades: one seal unrolls into a wall of thrown steel.
 *
 * <p>Sixteen shuriken leave the caster's hand almost at once, spread across the width of the look
 * rather than stacked on one point. A single blade is easy to step out of; a fan is not, which is
 * what makes the technique a scroll rather than a bigger throw. Any single blade still only carries
 * what a thrown blade carries - the scroll buys reach across a crowd, not a heavier hit - so it is
 * aimed at unarmoured bodies and open ground, never at walls.</p>
 *
 * <p>The blades are real projectiles, so they fall, are stopped by terrain and can be dodged. They
 * are deliberately not pickable up: a technique that hands sixteen shuriken back to the caster would
 * be a source of ammunition rather than a technique.</p>
 */
public class BladeRainAbility extends Ability implements Ability.Cooldown {

    /** V C V C B B - the summoning keys of the guardian, with the last two turned. */
    public static final long COMBO = 212132L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "blade_rain");

    /** Chakra for the scroll. */
    private static final float COST = 40.0f;

    private static final int COOLDOWN = 400;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far away the fan is laid out. Also how far the scroll carries. */
    private static final double RANGE = 16.0d;

    /** One blade for every year of the technique's name. */
    private static final int BLADES = 16;

    /**
     * What one blade is worth. Vanilla works a projectile's damage out from its speed times its base
     * damage, so the base is set to this divided by {@link #SPEED} to land on it at launch.
     */
    private static final double DAMAGE = 4.0d;

    /** Launch speed, in blocks per tick. */
    private static final float SPEED = 3.0f;

    /** How much of the throw is spread rather than aimed, in blocks at the far end of the fan. */
    private static final float INACCURACY = 0.6f;

    /** Total width of the fan, in degrees. */
    private static final double FAN_DEGREES = 24.0d;

    /** How far the alternating blades are lifted above and below the aim line. */
    private static final double VERTICAL_SCATTER = 0.45d;

    /** A thrown volley is heard. */
    private static final int NOISE_RADIUS = 16;

    @Override
    public int noiseRadius() {
        return NOISE_RADIUS;
    }

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
        float cost = this.masteryCost(ninjaData, COST);
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.9f);

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        Vec3 flat = new Vec3(look.x, 0.0d, look.z);
        // A straight-down look has no horizontal facing; the fan still needs a width to spread over.
        flat = flat.lengthSqr() < 1.0E-4d ? new Vec3(0.0d, 0.0d, 1.0d) : flat.normalize();
        Vec3 side = new Vec3(-flat.z, 0.0d, flat.x);

        // The fan is worked out as a row of points at the far end of the throw, so its width is real
        // distance rather than a bare angle the player has to imagine.
        Vec3 centre = eye.add(look.scale(RANGE));
        double halfWidth = Math.tan(Math.toRadians(FAN_DEGREES / 2.0d)) * RANGE;

        for (int i = 0; i < BLADES; i++) {
            double spread = BLADES == 1 ? 0.0d : (2.0d * i / (BLADES - 1) - 1.0d);
            double lift = (i % 2 == 0 ? 1.0d : -1.0d) * VERTICAL_SCATTER;
            Vec3 aim = centre.add(side.scale(spread * halfWidth)).add(0.0d, lift, 0.0d);
            Vec3 direction = aim.subtract(eye);

            ShurikenEntity blade = new ShurikenEntity(level, player);
            blade.setBaseDamage(DAMAGE / SPEED);
            blade.setKnockback(0);
            // A thrown blade that could be walked over and picked up would make the scroll a source
            // of ammunition; the steel is chakra-shaped and gone once it lands.
            blade.pickup = AbstractArrow.Pickup.DISALLOWED;
            blade.shoot(direction.x, direction.y, direction.z, SPEED, INACCURACY);
            level.addFreshEntity(blade);
        }
    }
}
