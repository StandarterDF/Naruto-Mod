package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * The healing network of the slug sage: nature energy poured into everyone the caster stands with.
 *
 * <p>Not a hand laid on one wound. The network is a field - it finds every body on the caster's side
 * that is close enough to be part of it and knits them all at once, peeling poison and withering off
 * as it goes. That is the shape of a battlefield technique: the caster does not choose who is saved,
 * the field does, and standing in it is the whole price of being saved.</p>
 *
 * <p>It can only be held in sage mode, because it is nature energy doing the work and not chakra. The
 * entry price is paid once and the hold is cheap, so the real cost of the network is the sage mode it
 * burns through while the caster stands still healing instead of fighting - which is exactly the
 * trade the technique is meant to demand.</p>
 */
public class KatsuyuNetworkAbility extends Ability implements Ability.Channeled {

    /** V C V B C V - the silencing seal with the last key turned, so the two seals sit side by side. */
    public static final long COMBO = 212312L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "katsuyu_network");

    /** How far the network reaches from the caster. */
    private static final double RANGE = 8.0d;

    /** Chakra to open the network. */
    private static final float ENTRY_COST = 40.0f;

    /** Chakra burnt for every tick the network is kept open. */
    private static final float CHAKRA_PER_TICK = 0.4f;

    private static final int CHAKRA_REGEN_DELAY = 10;

    /** Health restored to each ally per pulse, and how many ticks apart the pulses are. */
    private static final float HEAL_AMOUNT = 1.0f;
    private static final int HEAL_INTERVAL = 20;

    /** How often the network is drawn so the caster can see its reach. */
    private static final int PARTICLE_INTERVAL = 4;

    /** Healing is quiet work. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    /** The network has to be held: a tap would pay the entry price and heal nobody. */
    @Override
    public boolean canActivateBelowMinCharge() {
        return false;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.sageModeEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.sage.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ninjaData.isSageMode()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.katsuyu.nosage",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (chargeAmount < 0) {
            return false;
        }

        float cost = this.masteryCost(ninjaData, chargeAmount == 0 ? ENTRY_COST : CHAKRA_PER_TICK);
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

        // The network is paid for as it is held, so the mode is mastered by the time spent healing.
        this.recordMasteryCredit(player, ninjaData, MASTERY_CREDIT_PER_TICK);

        if (ticksChanneled % PARTICLE_INTERVAL == 0) {
            drawField(level, player, ticksChanneled);
        }
        if (ticksChanneled % HEAL_INTERVAL == 0) {
            healAllies(level, player);
        }
    }

    /**
     * The work happens pulse by pulse, so releasing the seal has nothing left to do; the mastery was
     * already paid tick by tick.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
    }

    /** A held network is mastered by holding it, not by opening it. */
    @Override
    public void recordMastery(Player player, INinjaData ninjaData) {
    }

    /** Mends every body on the caster's side inside the field and washes its curses away. */
    private void healAllies(ServerLevel level, Player caster) {
        List<LivingEntity> allies = level.getEntitiesOfClass(LivingEntity.class,
                caster.getBoundingBox().inflate(RANGE),
                entity -> entity.isAlive() && !entity.isSpectator() && isAlly(caster, entity));

        for (LivingEntity ally : allies) {
            ally.heal(HEAL_AMOUNT);
            ally.removeEffect(MobEffects.POISON);
            ally.removeEffect(MobEffects.WITHER);
            level.sendParticles(ParticleTypes.HEART, ally.getX(),
                    ally.getY() + ally.getBbHeight() * 0.8d, ally.getZ(),
                    3, 0.3d, 0.3d, 0.3d, 0.01d);
        }
    }

    /**
     * Who the network reaches: the caster, their own clones, their friends and team mates, and any
     * tamed animal. Anyone else is a stranger in the middle of a healing field, and is left alone.
     */
    private static boolean isAlly(Player caster, LivingEntity candidate) {
        if (candidate == caster) {
            return true;
        }
        if (candidate instanceof ShadowCloneEntity clone) {
            return clone.isOwnedBy(caster);
        }
        if (candidate instanceof TamableAnimal animal) {
            return animal.isTame();
        }
        if (candidate instanceof Player other) {
            return Friends.isOwnSide(caster, other);
        }
        return false;
    }

    /**
     * A ring of light at the edge of the field and a thread of it towards each body it is working
     * on, so the reach of the technique is visible rather than guessed at.
     */
    private static void drawField(ServerLevel level, Player caster, int ticks) {
        double step = Math.PI / 8.0d;
        double phase = ticks * 0.15d;
        for (int i = 0; i < 16; i++) {
            double angle = i * step + phase;
            double x = caster.getX() + Math.cos(angle) * RANGE;
            double z = caster.getZ() + Math.sin(angle) * RANGE;
            level.sendParticles(ParticleTypes.END_ROD, x, caster.getY() + 0.15d, z,
                    1, 0.0d, 0.05d, 0.0d, 0.0d);
        }
        level.sendParticles(ParticleTypes.END_ROD, caster.getX(),
                caster.getY() + caster.getBbHeight() * 0.7d, caster.getZ(),
                2, 0.25d, 0.35d, 0.25d, 0.01d);
    }
}
