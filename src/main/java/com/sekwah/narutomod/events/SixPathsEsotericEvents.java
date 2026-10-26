package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.AmenominakaAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.abilities.jutsus.LimboAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneganAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The world side of the Six Paths: a shadow that only an eye can see, and a piece of the world that
 * has to give everybody back.
 *
 * <p>Both powers are deliberately run from the tick rather than from the cast: a limbo shadow lives
 * on its own for eight seconds and keeps striking while the caster fights elsewhere, and a body
 * dropped into the void has to come back even if the caster has died, logged out or walked away.
 * Nothing here scans the whole world - a shadow is found because it ticks, and a body is found
 * because it ticks.</p>
 *
 * @see LimboAbility
 * @see AmenominakaAbility
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SixPathsEsotericEvents {

    /**
     * Game-time mark the Six Paths mode writes on its caster; the esoteric techniques only read it.
     * Keeping the name here means the two techniques that need the gate cannot drift apart.
     */
    public static final String SIX_PATHS_UNTIL_TAG = "narutomod:six_paths_until";

    /** How far a dojutsu can pick a limbo shadow out of the air. */
    private static final double LIMBO_SIGHT_RANGE = 48.0d;

    /** How long a dojutsu's sight of the shadow lasts before it has to be renewed. */
    private static final int LIMBO_GLOW_TICKS = 30;

    /** How often the sight of nearby eyes is checked. */
    private static final int LIMBO_GLOW_REFRESH = 10;

    /** @return whether the Six Paths mode is currently up on this caster. */
    public static boolean isSixPathsActive(Player player) {
        return player.getPersistentData().getLong(SIX_PATHS_UNTIL_TAG) > player.level().getGameTime();
    }

    /**
     * Keeps every limbo shadow honest, and gives back anything that has served its time in the void.
     *
     * <p>Players are skipped here on purpose: their share of the return runs on the player tick,
     * which is a cleaner place to move a body that owns a connection.</p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity instanceof ShadowCloneEntity clone && LimboAbility.isLimboShadow(clone)) {
            maintainLimbo(clone);
            return;
        }
        if (!(entity instanceof Player) && AmenominakaAbility.isDue(entity)) {
            AmenominakaAbility.returnHome(entity);
        }
    }

    /** Puts a player dragged in by Amenominaka back where the sweep picked them up. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player.level().isClientSide) {
            return;
        }
        if (AmenominakaAbility.isDue(player)) {
            AmenominakaAbility.returnHome(player);
        }
    }

    /**
     * One tick of a limbo shadow: it stays unseen, it lets a dojutsu find it, and it hits whatever
     * stands beside it. When its time is up - or its caster is gone - it is taken off the field.
     */
    private static void maintainLimbo(ShadowCloneEntity shadow) {
        CompoundTag data = shadow.getPersistentData();
        long now = shadow.level().getGameTime();
        Player owner = ownerOf(shadow);
        if (owner == null || now >= data.getLong(LimboAbility.UNTIL_TAG)) {
            shadow.discard();
            return;
        }
        shadow.setInvisible(true);
        if (shadow.tickCount % LIMBO_GLOW_REFRESH == 0) {
            revealToDojutsu(shadow);
        }
        if (shadow.tickCount % LimboAbility.STRIKE_INTERVAL == 0) {
            strike(shadow, owner);
        }
    }

    /** @return the caster the shadow was made from, or null if that player is not here any more. */
    @Nullable
    private static Player ownerOf(ShadowCloneEntity shadow) {
        CompoundTag data = shadow.getPersistentData();
        if (!data.hasUUID(LimboAbility.OWNER_TAG) || !(shadow.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID owner = data.getUUID(LimboAbility.OWNER_TAG);
        return level.getPlayerByUUID(owner);
    }

    /**
     * Marks the shadow for anyone close enough with an eye open.
     *
     * <p>Vanilla has one glow for the whole world, so the compromise is time rather than audience:
     * the outline is only refreshed while a Byakugan, Sharingan or Rinnegan is actually looking, and
     * fades a second and a half after the last of them looks away. Without any eye open the shadow
     * is simply not there.</p>
     */
    private static void revealToDojutsu(ShadowCloneEntity shadow) {
        if (!(shadow.level() instanceof ServerLevel level)) {
            return;
        }
        boolean watched = false;
        for (Player watcher : level.getEntitiesOfClass(Player.class,
                shadow.getBoundingBox().inflate(LIMBO_SIGHT_RANGE))) {
            if (!watcher.isAlive() || watcher.isSpectator()) {
                continue;
            }
            INinjaData data = watcher.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
            if (data == null) {
                continue;
            }
            if (ByakuganAbility.isActive(data) || SharinganAbility.isActive(data) || RinneganAbility.isActive(data)) {
                watched = true;
                break;
            }
        }
        if (watched) {
            shadow.addEffect(new MobEffectInstance(MobEffects.GLOWING, LIMBO_GLOW_TICKS, 0, false, false));
        }
    }

    /**
     * The shadow's blow: unseen, unblockable in every sense that matters, and aimed at the nearest
     * body that is not on the caster's side.
     */
    private static void strike(ShadowCloneEntity shadow, Player owner) {
        if (!(shadow.level() instanceof ServerLevel level)) {
            return;
        }
        LivingEntity victim = nearestVictim(level, shadow, owner);
        if (victim == null) {
            return;
        }
        victim.hurt(shadow.damageSources().magic(), LimboAbility.STRIKE_DAMAGE);
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, victim.getX(),
                victim.getY() + victim.getBbHeight() * 0.5d, victim.getZ(),
                10, 0.25d, 0.35d, 0.25d, 0.02d);
    }

    /** The closest body in reach that the caster would call an enemy. */
    @Nullable
    private static LivingEntity nearestVictim(ServerLevel level, ShadowCloneEntity shadow, Player owner) {
        double best = LimboAbility.STRIKE_RANGE * LimboAbility.STRIKE_RANGE;
        LivingEntity victim = null;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                shadow.getBoundingBox().inflate(LimboAbility.STRIKE_RANGE))) {
            if (!isEnemyOf(owner, shadow, candidate)) {
                continue;
            }
            double distance = candidate.distanceToSqr(shadow);
            if (distance <= best) {
                best = distance;
                victim = candidate;
            }
        }
        return victim;
    }

    /** Anything alive that is neither the shadow, nor the caster's side, nor untouchable. */
    private static boolean isEnemyOf(Player owner, ShadowCloneEntity shadow, LivingEntity candidate) {
        if (candidate == shadow || !candidate.isAlive() || candidate.isSpectator()) {
            return false;
        }
        if (candidate instanceof Player player && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        return !Friends.isOwnSide(owner, candidate);
    }
}
