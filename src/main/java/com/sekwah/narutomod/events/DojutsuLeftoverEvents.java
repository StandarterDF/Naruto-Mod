package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.BlackRodAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShearAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganPhaseAbility;
import com.sekwah.narutomod.abilities.jutsus.KamuiPhaseAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneganLevitationAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganEchoAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The consequences that outlive the cast of the dojutsu techniques in this block: a cut that lands,
 * a rod that keeps working, a body that stays out of phase, and a death the far eye refuses to
 * forget.
 *
 * <p>Every one of these is a mark first and an effect second. The techniques write a tag and walk
 * away; this class reads it on the next relevant tick and does the work. That keeps the ability
 * classes small, keeps the server the only authority for anything that changes a body, and means a
 * mark written just before a crash still means something when the world comes back.</p>
 *
 * <p>The phase flags are the one thing that has to run on both sides. Vanilla resets
 * {@code noPhysics} at the top of {@code Player#tick}, and the client would otherwise push the caster
 * back out of every block the server is letting them pass through. It runs at
 * {@link EventPriority#HIGHEST} so it lands after the Kamui upkeep, which also touches the same field
 * for a different stance.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DojutsuLeftoverEvents {

    /** Set while this class is the one holding the caster's body out of collision. */
    private static final String PHASE_FLAGS = "narutomod:jougan_phase_flags";

    /** How often a body is tested against the rods around it, in ticks. */
    private static final int ROD_INTERVAL = 5;

    /** The glow a rod puts on a nearby ninja, refreshed rather than permanent. */
    private static final int ROD_GLOW_TICKS = 40;

    /** How long a rod keeps natural regeneration switched off after the last refresh. */
    private static final int ROD_REGEN_DELAY = 40;

    /** How often the echo sweep looks for expired silhouettes and feeds the living ones. */
    private static final long ECHO_SWEEP_INTERVAL = 10L;

    private DojutsuLeftoverEvents() {
    }

    /**
     * The two stances that hold a body in a state rather than in a place: the Jougan's passage
     * (both sides) and the Rinnegan's lift (server only, cleaned up if the caster vanished).
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player player) {
            applyPhaseFlags(player);
        }
        if (!entity.level().isClientSide) {
            releaseExpiredLift(entity);
        }
    }

    /**
     * Keeps a phasing caster's body out of collisions on both sides. The flag it leaves behind is
     * what lets the release put the body back without fighting the Kamui stance or a spectator.
     */
    private static void applyPhaseFlags(Player player) {
        CompoundTag data = player.getPersistentData();
        if (JouganPhaseAbility.isPhasing(player)) {
            player.noPhysics = true;
            player.setNoGravity(true);
            data.putBoolean(PHASE_FLAGS, true);
            return;
        }
        if (!data.getBoolean(PHASE_FLAGS)) {
            return;
        }
        data.remove(PHASE_FLAGS);

        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData != null && KamuiPhaseAbility.isActive(ninjaData)) {
            // The body is still someone else's smoke; leave it to that stance.
            return;
        }
        player.noPhysics = player.isSpectator();
        if (!player.isSpectator()) {
            player.setNoGravity(false);
        }
        if (!player.level().isClientSide) {
            // A caster who let go inside a wall is put back into open air rather than buried alive.
            KamuiPhaseAbility.unstick(player);
        }
    }

    /**
     * The Rinnegan's hold renews a short mark on the lifted body. If the caster is gone and the mark
     * runs out, gravity comes back here rather than by the caster's hand.
     */
    private static void releaseExpiredLift(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(RinneganLevitationAbility.LEVITATION_UNTIL)) {
            return;
        }
        if (data.getLong(RinneganLevitationAbility.LEVITATION_UNTIL) > entity.level().getGameTime()) {
            return;
        }
        data.remove(RinneganLevitationAbility.LEVITATION_UNTIL);
        entity.setNoGravity(false);
        entity.fallDistance = 0.0f;
    }

    /**
     * A blow landed inside the Chakra Shear stance cuts harder and, against a ninja, spills chakra.
     * Only a swing with the caster's own body counts; the stance is read from the mark, so it ends
     * with the body rather than with a reference to it.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getDirectEntity() instanceof Player attacker)
                || event.getSource().getEntity() != attacker) {
            return;
        }
        if (attacker.getPersistentData().getLong(ChakraShearAbility.UNTIL_TAG)
                <= attacker.level().getGameTime()) {
            return;
        }
        event.setAmount(event.getAmount() + ChakraShearAbility.BONUS_DAMAGE);
        if (event.getEntity() instanceof Player victim) {
            drainChakra(victim);
        }
    }

    /** Takes a little chakra out of a hit ninja, never more than the pool actually holds. */
    private static void drainChakra(Player victim) {
        victim.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(data -> {
            float lost = Math.min(ChakraShearAbility.DRAIN, data.getChakra());
            if (lost <= 0.0f) {
                return;
            }
            data.setChakra(data.getChakra() - lost);
            if (victim.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.WITCH, victim.getX(),
                        victim.getY() + victim.getBbHeight() * 0.6d, victim.getZ(),
                        5, 0.25d, 0.25d, 0.25d, 0.01d);
            }
        });
    }

    /**
     * A rod of the outer path holds the chakra of every ninja standing near it still: regeneration is
     * pushed back every few ticks, and the eye that planted the rod can see the bodies it caught.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.tickCount % ROD_INTERVAL != 0) {
            return;
        }
        List<Display.ItemDisplay> rods = player.level().getEntitiesOfClass(Display.ItemDisplay.class,
                player.getBoundingBox().inflate(BlackRodAbility.SUPPRESSION_RADIUS), BlackRodAbility::isRod);
        boolean withinReach = false;
        for (Display.ItemDisplay rod : rods) {
            if (rod.distanceToSqr(player) <= BlackRodAbility.SUPPRESSION_RADIUS
                    * BlackRodAbility.SUPPRESSION_RADIUS) {
                withinReach = true;
                break;
            }
        }
        if (!withinReach) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.GLOWING, ROD_GLOW_TICKS, 0, false, false));
        // Zero is spent: only the regeneration delay is wanted, and this is the public way to set it.
        player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .ifPresent(data -> data.useChakra(0.0f, ROD_REGEN_DELAY));
    }

    /**
     * The death echo window: the first caster with an open window and the dying body in reach gets
     * the silhouette and the line in chat. Only one is written per death, so two watchers do not
     * stack two identical names on the same spot.
     */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        LivingEntity dead = event.getEntity();
        if (!(dead.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        for (ServerPlayer caster : level.players()) {
            if (caster == dead || !SenriganEchoAbility.isWindowOpen(caster, now)) {
                continue;
            }
            if (caster.distanceToSqr(dead) > SenriganEchoAbility.RANGE * SenriganEchoAbility.RANGE) {
                continue;
            }
            spawnEcho(level, dead, caster, now);
            return;
        }
    }

    /** Leaves the name standing where the body fell, and tells the caster what the eye saw. */
    private static void spawnEcho(ServerLevel level, LivingEntity dead, ServerPlayer caster, long now) {
        Display.TextDisplay echo = EntityType.TEXT_DISPLAY.create(level);
        if (echo == null) {
            return;
        }
        // Display entities have no public setters in 1.20.1; their own save format is the way in.
        CompoundTag display = new CompoundTag();
        display.putString("text", Component.Serializer.toJson(dead.getDisplayName()));
        display.putBoolean("see_through", true);
        display.putBoolean("default_background", false);
        display.putInt("background", 0x40000000);
        display.putString("billboard", "center");
        display.putFloat("view_range", 1.0f);
        echo.load(display);
        echo.moveTo(dead.getX(), dead.getY() + dead.getBbHeight() + 0.35d, dead.getZ(), 0.0f, 0.0f);
        echo.setNoGravity(true);
        echo.setInvulnerable(true);

        CompoundTag data = echo.getPersistentData();
        data.putBoolean(SenriganEchoAbility.ECHO_TAG, true);
        data.putLong(SenriganEchoAbility.ECHO_UNTIL_TAG, now + SenriganEchoAbility.WINDOW_TICKS);
        level.addFreshEntity(echo);

        level.sendParticles(ParticleTypes.SOUL, dead.getX(),
                dead.getY() + dead.getBbHeight() * 0.5d, dead.getZ(),
                24, 0.4d, 0.7d, 0.4d, 0.02d);
        caster.displayClientMessage(Component.translatable("jutsu.senrigan_echo.death", dead.getDisplayName())
                .withStyle(ChatFormatting.GRAY), false);
    }

    /**
     * Ages the silhouettes out and keeps a thin trail of soul light on each one. The tag is on the
     * display itself, so a silhouette left behind by a stopped server is still cleaned up here.
     */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (now % ECHO_SWEEP_INTERVAL != 0L) {
            return;
        }
        for (Entity entity : level.getEntities().getAll()) {
            CompoundTag data = entity.getPersistentData();
            if (!data.getBoolean(SenriganEchoAbility.ECHO_TAG)) {
                continue;
            }
            if (now >= data.getLong(SenriganEchoAbility.ECHO_UNTIL_TAG)) {
                entity.discard();
                continue;
            }
            level.sendParticles(ParticleTypes.SOUL, entity.getX(), entity.getY() - 0.1d, entity.getZ(),
                    2, 0.25d, 0.4d, 0.25d, 0.01d);
        }
    }
}
