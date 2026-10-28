package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.jutsuprojectile.RasenshurikenJutsuEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * The rasengan given a shape and a nature: the sphere is flattened into a spinning disc of wind and
 * thrown instead of driven in.
 *
 * <p>It is channelled like the sphere it is built on, but the release is the point of it: the longer
 * the disc is held the faster and harder it is when it leaves the hand, and where the rasengan stops
 * at the caster's arm this one carries on across the battlefield. What it reaches first it scoops up
 * off the ground and blows apart in mid-air over their head. See
 * {@link RasenshurikenJutsuEntity}.</p>
 *
 * <p>Holding it costs more than the rasengan does, and throwing it costs a lump on top - a technique
 * that reaches that far is not meant to be free.</p>
 */
public class RasenshurikenJutsuAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. A blast, heard a long way off.
     */
    @Override
    public int noiseRadius() {
        return 48;
    }


    /** C B B C B V - the rasengan's seals, then one more to shape it. */
    public static final long COMBO = 31233L;

    /** Ticks of channelling before the disc is worth throwing. */
    private static final int MIN_CHARGE = 20;

    /** Ticks at which the disc is as good as it gets. */
    private static final int FULL_CHARGE = 60;

    /** Chakra burned for every tick it is held. */
    private static final float CHAKRA_PER_TICK = 1.0f;

    /**
     * Paid once, on release, on top of everything spent holding it. Deliberately kept under the
     * battery upkeep reserve (see {@code ChakraBatteryItem#fuelUpkeep}) so that a caster who has a
     * charged battery on them can always afford to actually throw the disc they just charged.
     */
    private static final float RELEASE_COST = 25.0f;

    /** How fast the disc leaves the hand at its smallest and at full charge. */
    private static final double MIN_SPEED = 0.55d;
    private static final double MAX_SPEED = 0.95d;

    /** What the charge is worth to the disc, from a barely held one up to a full one. */
    private static final float MIN_POWER = 0.7f;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (ninjaData.getSeninMana() <= 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.fail.senin.notenough",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW),
                    1).withStyle(ChatFormatting.RED), true);
            return false;
        }
        float factor = Math.max(0.3f, 1.0f - (ninjaData.getSeninMana() / Math.max(1.0f, ninjaData.getMaxSeninMana())) * 0.7f);
        float sageFactor = ninjaData.isSageMode() ? 1.0f : 5.0f;
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK * factor * sageFactor);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    /**
     * The disc spinning in the caster's hand while it is being held.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, 4, 0.15d, 0.15d, 0.15d, 0.02d);
        if (ticksChanneled % 4 == 0) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, hand.x, hand.y, hand.z, 1, 0.0d, 0.0d, 0.0d, 0.0d);
        }
        if (ticksChanneled % 5 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 3, 0.2d, 0.2d, 0.2d, 0.05d);
        }
    }

    /**
     * Throws the disc: how far it goes and how hard it lands is decided here and carried in the
     * entity itself, so letting go of a fully held one is worth waiting for.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A tap that never charged: there is nothing to throw, and nothing to pay for.
        if (ticksActive < MIN_CHARGE) {
            level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
            return;
        }
        float releaseFactor = Math.max(0.3f, 1.0f - (ninjaData.getSeninMana() / Math.max(1.0f, ninjaData.getMaxSeninMana())) * 0.7f);
        float releaseCost = this.masteryCost(ninjaData, RELEASE_COST * releaseFactor);
        if (!ninjaData.isSageMode()) {
            releaseCost = Math.max(releaseCost, ninjaData.getMaxChakra() * 0.5f);
        }
        if (ninjaData.getChakra() < releaseCost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return;
        }
        ninjaData.useChakra(releaseCost, 20 * 2);

        float power = Mth.clamp((ticksActive - MIN_CHARGE) / (float) (FULL_CHARGE - MIN_CHARGE), 0.0f, 1.0f);
        double speed = MIN_SPEED + (MAX_SPEED - MIN_SPEED) * power;

        RasenshurikenJutsuEntity disc = new RasenshurikenJutsuEntity(player, speed);
        disc.setPower(MIN_POWER + (1.0f - MIN_POWER) * power);
        level.addFreshEntity(disc);

        level.playSound(null, player, SoundEvents.TRIDENT_THROW, SoundSource.PLAYERS, 1.0f, 0.8f);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                player.getEyeY() - 0.2d, player.getZ(), 4, 0.3d, 0.3d, 0.3d, 0.0d);
    }
}
