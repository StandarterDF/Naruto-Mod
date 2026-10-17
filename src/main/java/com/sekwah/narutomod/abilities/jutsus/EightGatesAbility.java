package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;

/**
 * The eight inner gates, opened one at a time.
 *
 * <p>Every cast opens the next gate, and by the time all eight are open the caster is fast, strong
 * and hard to hurt in a way nothing else in the mod can match. The catch is that the body is not
 * built for it: the gates grind the caster down the whole time they are open, and the drain grows
 * with the square of how many are open, so two gates are something a caster can keep up for ten
 * minutes while the last two are a countdown.</p>
 *
 * <p><b>The eighth gate cannot be closed.</b> Up to the seventh the caster can still seal them again
 * with {@link GateReleaseAbility}, but once the last gate is open no cast shuts it and eight gates
 * drain a full health bar in a little over half a minute. Death is the only way out, which is the
 * point: the eighth gate is meant to be spent, not used. The gates fall away with death and are gone
 * when the caster comes back.</p>
 *
 * <p>The effects are kept alive from {@code PlayerEvents}, not here, since they have to be refreshed
 * every tick while the gates are open.</p>
 */
public class EightGatesAbility extends Ability {

    /** V B B B */
    public static final long COMBO = 2333L;

    public static final int MAX_GATES = 8;

    /** Chakra and stamina each gate costs to force open. */
    private static final float CHAKRA_PER_GATE = 25.0f;
    private static final float STAMINA_PER_GATE = 15.0f;

    /** How often, in ticks, the open gates take their toll on the caster. */
    private static final int DRAIN_INTERVAL = 20;

    /**
     * Health lost each tick of drain, multiplied by the square of the open gates.
     *
     * <p>Anchored on two numbers rather than picked out of the air: the second gate costs a tenth of
     * a heart a second, so a full health bar lasts ten minutes, and the eighth costs a bit over half
     * a heart a second, which is a little over half a minute to live. Everything between follows
     * from the square, so the early gates are a slow tax and the last two are a countdown.</p>
     *
     * <p>Fractions are not thrown away, they are carried: the caster is charged whole points of
     * health so that the health bar actually moves, and the remainder waits for the next tick.</p>
     */
    private static final float DRAIN_COEFFICIENT = 0.00833f;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        // There is no way back from the eighth gate: the cast that would close them fails instead.
        if (ninjaData.getGateCount() >= MAX_GATES) {
            player.displayClientMessage(Component.translatable("jutsu.fail.gates_open",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        if (ninjaData.getChakra() < CHAKRA_PER_GATE || ninjaData.getStamina() < STAMINA_PER_GATE) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(CHAKRA_PER_GATE, 20 * 3);
        ninjaData.useStamina(STAMINA_PER_GATE, 20 * 3);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        int gates = ninjaData.getGateCount() + 1;
        ninjaData.setGateCount(gates);

        // A burst coloured by how far the caster has pushed it.
        level.sendParticles(ParticleTypes.FLAME, player.getX(),
                player.getY() + player.getBbHeight() / 2.0d, player.getZ(),
                10 + gates * 6, 0.4d, 0.6d, 0.4d, 0.02d);
        level.playSound(null, player, SoundEvents.RAVAGER_ROAR, SoundSource.PLAYERS,
                0.7f, 0.6f + gates * 0.05f);
        player.displayClientMessage(Component.translatable("jutsu.eight_gates.open",
                Component.literal(String.valueOf(gates)).withStyle(ChatFormatting.GOLD))
                .withStyle(ChatFormatting.RED), true);
    }

    /**
     * Keeps the effects of however many gates are open running, and takes the health they cost.
     * Called every tick from {@code PlayerEvents} for every player, so it has to be cheap when there
     * is nothing to do.
     */
    public static void maintain(Player player, INinjaData ninjaData) {
        int gates = ninjaData.getGateCount();
        if (gates <= 0) {
            return;
        }

        int shortDuration = DRAIN_INTERVAL + 20;
        // Speed and strength rise with every gate; the defensive ones only every other gate, so the
        // last few gates are about hitting harder rather than becoming untouchable.
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, shortDuration, gates - 1, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, shortDuration, (gates - 1) / 2, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, shortDuration, (gates - 1) / 3, true, false));
        player.addEffect(new MobEffectInstance(MobEffects.JUMP, shortDuration, Math.min(gates - 1, 3), true, false));

        if (player.tickCount % DRAIN_INTERVAL == 0) {
            // Charged in whole points of health, because the health bar is drawn in half hearts and a
            // drain that always arrived as a fraction of one would never visibly move it. The unpaid
            // remainder is carried into the next tick, so the long run cost is unchanged.
            float owed = ninjaData.getGateBacklashCarry() + DRAIN_COEFFICIENT * gates * gates;
            int points = (int) owed;
            ninjaData.setGateBacklashCarry(owed - points);
            if (points > 0) {
                backlash(player, points);
            }
        }
    }

    /**
     * Takes the health the open gates are owed, in whole points.
     *
     * <p>This is deliberately not damage in the usual sense: it goes straight onto the health bar
     * instead of through the damage pipeline, because the backlash is the caster's own body giving
     * out and nothing may soak it up. Armour, resistance, absorption, a chakra shield, the gates'
     * own resistance buff and even creative mode all leave it alone, which is the whole point of an
     * irreversible technique. The feedback vanilla would have given is replayed by hand, and dying
     * is done by hand too so that no last-second save can cancel it.</p>
     */
    private static void backlash(Player player, int points) {
        float remaining = player.getHealth() - points;
        if (remaining > 0.0f) {
            player.setHealth(remaining);
            if (player.level() instanceof ServerLevel level) {
                // The red flash and the grunt a normal hit would have caused, since this is not one.
                level.broadcastEntityEvent(player, (byte) 2);
                level.playSound(null, player, SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 0.5f, 1.0f);
                level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, player.getX(),
                        player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                        4, 0.3d, 0.3d, 0.3d, 0.0d);
            }
            return;
        }

        player.setHealth(0.0f);
        player.die(NarutoDamageTypes.getDamageSource(player.level(), NarutoDamageTypes.GATE_BACKLASH));
    }

    /**
     * Shuts every gate: no effects, no drain. Only death gets to do this now, the caster cannot.
     */
    public static void close(Player player, INinjaData ninjaData) {
        ninjaData.setGateCount(0);
        ninjaData.setGateBacklashCarry(0.0f);
        player.removeEffect(MobEffects.MOVEMENT_SPEED);
        player.removeEffect(MobEffects.DAMAGE_BOOST);
        player.removeEffect(MobEffects.DAMAGE_RESISTANCE);
        player.removeEffect(MobEffects.JUMP);
    }
}
