package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The empty-handed chain: three bare blows landed inside two seconds hit harder each time, and the
 * third throws what it hits.
 *
 * <p>A ninja with nothing in their hands fights in a rhythm rather than one blow at a time, and the
 * whole point of the rhythm is that it is worth keeping: the first blow is what the fist is worth,
 * the second a quarter more, and the third half again on top of a throw that leaves the target slow
 * and off balance. A blade keeps its own chain (see {@code ComboSwordEvents}) and is left alone
 * here - a weapon hit drops this one back to the start rather than being caught up in it.</p>
 *
 * <p>The chain lives in this class rather than on the player: it is worth nothing across a death, a
 * relog or a change of dimension, and bolting it onto the entity would mean saving a piece of state
 * that only ever describes the last two seconds of a fight. A swing that lands nothing resets it,
 * which is why the server watches the swing counter as well as the hurt event - an empty swing never
 * reaches a hurt event at all.</p>
 *
 * <p>The throw is where the technique reads: a caster running into the last blow drives the target up
 * and off its feet, and one who plants their feet and drops low sweeps it away sideways instead. It is
 * the same finisher, thrown two different ways, and the stance decides which.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TaijutsuComboEvents {

    /** How long the chain survives between two blows. Three blows have to land inside this. */
    public static final int CHAIN_WINDOW = 40;

    /** Damage multiplier of each blow of the chain, in the order they land. */
    private static final float[] STEP_MULTIPLIERS = {1.0f, 1.25f, 1.5f};

    /** What the third blow gives the target on top of its damage: a throw and a slow. */
    public static final double FINISHER_KNOCKBACK = 0.6d;
    public static final int FINISHER_SLOW_TICKS = 40;

    /** The rising finisher of a caster running into the blow: lift rather than shove. */
    public static final double RISING_LIFT = 0.35d;

    /** The vortex finisher of a caster who plants their feet and turns: a horizontal sweep. */
    public static final double VORTEX_SHOVE = 0.4d;

    /**
     * The chains of everyone currently fighting, dropped on logout and on a finished chain so the
     * map only ever holds the casters who are mid-combo this second.
     */
    private static final Map<UUID, Chain> CHAINS = new HashMap<>();

    private TaijutsuComboEvents() {
    }

    /**
     * One blow of the chain.
     *
     * <p>It runs late on purpose: the fist, the great swords and the chakra bonuses all add their own
     * damage first, so the chain multiplies the blow the caster would actually have landed rather
     * than a bare number that the other bonuses then bury.</p>
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Player player)
                || event.getSource().getEntity() != player) {
            return;
        }
        Chain chain = CHAINS.computeIfAbsent(player.getUUID(), id -> new Chain());
        // A blow with anything in the hand is a different chain's business, and it breaks this one.
        if (!player.getMainHandItem().isEmpty()) {
            chain.reset();
            return;
        }

        int now = player.tickCount;
        if (chain.lastHitTick < 0 || now - chain.lastHitTick > CHAIN_WINDOW) {
            chain.step = 0;
        }
        chain.step = Math.min(chain.step + 1, STEP_MULTIPLIERS.length);
        chain.lastHitTick = now;
        chain.landed = true;

        float multiplier = STEP_MULTIPLIERS[chain.step - 1];
        if (multiplier != 1.0f) {
            event.setAmount(event.getAmount() * multiplier);
        }
        if (chain.step == STEP_MULTIPLIERS.length) {
            throwTarget(player, event.getEntity());
            // The finisher closes the chain; the next blow starts a fresh one.
            chain.step = 0;
        }
    }

    /**
     * Watches the swing counter for a blow that never landed.
     *
     * <p>An attack that hits nothing fires no hurt event at all, so it cannot be caught where the
     * chain is grown. What the server does see is the swing packet, which leaves the same trace a
     * client sees: the swing counter climbing from zero. A swing whose counter has come back round
     * without a landed hit behind it was a miss, and a miss ends the chain.</p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        Chain chain = CHAINS.get(player.getUUID());
        if (chain == null) {
            return;
        }
        if (player.swingTime == 1) {
            if (!chain.landed) {
                chain.reset();
                return;
            }
            chain.landed = false;
        }
        if (chain.lastHitTick >= 0 && player.tickCount - chain.lastHitTick > CHAIN_WINDOW) {
            chain.reset();
        }
    }

    /** A chain that outlives its caster's session is worth nothing, so it is simply dropped. */
    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        CHAINS.remove(event.getEntity().getUUID());
    }

    /**
     * The throw that closes the chain. The direction handed to {@code knockback} is the one from the
     * target back towards the caster, because the entity is pushed away from the vector it is given -
     * the same convention vanilla's sprint blow uses.
     */
    private static void throwTarget(Player player, LivingEntity target) {
        double dx = player.getX() - target.getX();
        double dz = player.getZ() - target.getZ();
        double length = Math.sqrt(dx * dx + dz * dz);
        if (length < 1.0e-4d) {
            // Standing inside the target: throw it the way the caster is facing instead of nowhere.
            Vec3 look = player.getLookAngle();
            dx = -look.x;
            dz = -look.z;
            length = Math.sqrt(dx * dx + dz * dz);
        }
        if (length < 1.0e-4d) {
            dx = 0.0d;
            dz = -1.0d;
            length = 1.0d;
        }
        double nx = dx / length;
        double nz = dz / length;

        target.knockback(FINISHER_KNOCKBACK, nx, nz);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, FINISHER_SLOW_TICKS, 0));

        if (player.isSprinting()) {
            // Driven up and off the ground: the blow lifts rather than pushes.
            target.push(0.0d, RISING_LIFT, 0.0d);
        } else if (player.isShiftKeyDown()) {
            // Planted and turned: the same throw, swept sideways instead of upward.
            target.push(-nx * VORTEX_SHOVE, 0.0d, -nz * VORTEX_SHOVE);
        }

        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.SWEEP_ATTACK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 0.9f, 1.0f);
        }
    }

    /** What the chain remembers about one caster between blows. */
    private static final class Chain {

        /** How many blows of the current chain have landed, from zero to the length of the table. */
        private int step;

        /** Tick count of the last landed blow, or a negative value before the first one. */
        private int lastHitTick = -1;

        /** Whether the swing currently in progress has landed, which is how a miss is spotted. */
        private boolean landed;

        private void reset() {
            this.step = 0;
            this.lastHitTick = -1;
            this.landed = false;
        }
    }
}
