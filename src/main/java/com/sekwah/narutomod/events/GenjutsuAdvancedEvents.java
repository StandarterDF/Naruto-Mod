package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.GenjutsuJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.KagaamiAbility;
import com.sekwah.narutomod.abilities.jutsus.KokoniArazuAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * The upkeep of the illusions that work on a whole scene rather than on one mind.
 *
 * <p>Kokoni Arazu hides the caster by emptying the room around every creature that can see them, and
 * Kagaami puts two copies of the caster where a body's eyes will land instead. Neither can be
 * carried by the cast alone: the marks have to be expired, targets have to be taken away again every
 * time a goal finds a new one, and the images have to be kept at the caster's shoulders. That is what
 * this class does, and it never casts anything - the techniques stay self-contained and only stamp
 * their marks.</p>
 *
 * <p>Everything is read off the entities' persistent data rather than out of fields, so a creature
 * that is unloaded and loaded again keeps its illusion for exactly as long as it should, the same way
 * the older genjutsu and the sleep touch do it. It only runs on the server.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class GenjutsuAdvancedEvents {

    /** How far around the caster a mirror image is looked for. */
    private static final double PHANTOM_SEARCH = 16.0d;

    /**
     * How far an image may be from its caster before the images are put back at their shoulders. The
     * caster's own upkeep already holds them there every tick; this only catches the one left behind
     * by a teleport, a dimension change, or a tick the stance could not pay for.
     */
    private static final double SNAP_DISTANCE = 6.0d;

    /** The most minds one mass illusion can hold at once. */
    private static final int MAX_MASS_TARGETS = 3;

    /** Mastery at which a mass illusion reaches one more mind, and then a second. */
    private static final float FIRST_EXTRA_TARGET = 0.3f;
    private static final float SECOND_EXTRA_TARGET = 0.6f;

    private GenjutsuAdvancedEvents() {
    }

    /** Runs every mark, and drops each one as soon as its time is up. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        if (entity.getPersistentData().contains(KokoniArazuAbility.UNTIL_TAG)) {
            maintainKokoni(entity);
        }
        if (entity instanceof ShadowCloneEntity phantom
                && phantom.getPersistentData().getBoolean(KagaamiAbility.PHANTOM_TAG)) {
            maintainPhantom(phantom);
        }
    }

    /**
     * Keeps a creature inside the empty room: whatever it had decided to fight is forgotten, and the
     * path it was walking towards that fight stops where it stands.
     */
    private static void maintainKokoni(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (entity.level().getGameTime() >= data.getLong(KokoniArazuAbility.UNTIL_TAG)) {
            data.remove(KokoniArazuAbility.UNTIL_TAG);
            data.remove(KokoniArazuAbility.CASTER_TAG);
            return;
        }
        if (!(entity instanceof Mob mob)) {
            return;
        }
        if (mob.getTarget() != null) {
            mob.setTarget(null);
            mob.getNavigation().stop();
        }
    }

    /**
     * Keeps a mirror image alive beside its caster and takes it off the field when the illusion is
     * over. An image whose caster has logged out has nothing left to haunt and goes with them.
     */
    private static void maintainPhantom(ShadowCloneEntity phantom) {
        CompoundTag data = phantom.getPersistentData();
        if (phantom.level().getGameTime() >= data.getLong(KagaamiAbility.UNTIL_TAG)) {
            removePhantom(phantom);
            return;
        }
        Player caster = phantom.getOwner();
        if (caster == null) {
            removePhantom(phantom);
            return;
        }
        if (phantom.distanceToSqr(caster) > SNAP_DISTANCE * SNAP_DISTANCE) {
            KagaamiAbility.placePhantom(phantom, caster, data.getInt(KagaamiAbility.SIDE_TAG));
        }
    }

    /** Takes an expired image out of the world, marks and all. */
    private static void removePhantom(ShadowCloneEntity phantom) {
        CompoundTag data = phantom.getPersistentData();
        data.remove(KagaamiAbility.PHANTOM_TAG);
        data.remove(KagaamiAbility.UNTIL_TAG);
        data.remove(KagaamiAbility.CASTER_TAG);
        data.remove(KagaamiAbility.SIDE_TAG);
        // Faded rather than popped: an illusion has no body to leave smoke behind.
        phantom.discard();
    }

    /**
     * Refuses the target changes the illusions are there to prevent: a creature inside the empty room
     * cannot pick the caster back out of it, and a creature going for the caster with the mirror
     * images up finds one of them instead. Clearing a target must stay possible, or the illusion
     * could never be let go of.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        LivingEntity chosen = event.getNewTarget();
        if (chosen == null) {
            return;
        }
        CompoundTag data = mob.getPersistentData();
        if (data.getLong(KokoniArazuAbility.UNTIL_TAG) > mob.level().getGameTime()
                && data.getString(KokoniArazuAbility.CASTER_TAG).equals(chosen.getStringUUID())) {
            event.setNewTarget(null);
            return;
        }
        if (chosen instanceof Player caster) {
            ShadowCloneEntity phantom = nearestPhantom(caster);
            if (phantom != null) {
                event.setNewTarget(phantom);
            }
        }
    }

    /** @return the closest live mirror image standing for this caster, or null when none is up. */
    @Nullable
    private static ShadowCloneEntity nearestPhantom(Player caster) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return null;
        }
        ShadowCloneEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (ShadowCloneEntity phantom : level.getEntitiesOfClass(ShadowCloneEntity.class,
                caster.getBoundingBox().inflate(PHANTOM_SEARCH))) {
            CompoundTag data = phantom.getPersistentData();
            if (!data.getBoolean(KagaamiAbility.PHANTOM_TAG)
                    || data.getLong(KagaamiAbility.UNTIL_TAG) <= level.getGameTime()
                    || !caster.getStringUUID().equals(data.getString(KagaamiAbility.CASTER_TAG))) {
                continue;
            }
            double distance = phantom.distanceToSqr(caster);
            if (distance < best) {
                best = distance;
                nearest = phantom;
            }
        }
        return nearest;
    }

    /**
     * How many minds one mass illusion may hold at once, for the parent to wire into the casting
     * technique: a ninja starts with one and reaches one more at thirty percent mastery, and a
     * second at sixty, up to {@link #MAX_MASS_TARGETS}. The mastery read is the base illusion's own
     * ({@link GenjutsuJutsuAbility}), which is the technique a mass cast grows out of.
     */
    public static int massTargets(INinjaData ninjaData) {
        float mastery = Mastery.level(ninjaData, GenjutsuJutsuAbility.ID);
        int targets = 1;
        if (mastery >= FIRST_EXTRA_TARGET) {
            targets++;
        }
        if (mastery >= SECOND_EXTRA_TARGET) {
            targets++;
        }
        return Math.min(MAX_MASS_TARGETS, targets);
    }
}
