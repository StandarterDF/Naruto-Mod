package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.damagetypes.NarutoDamageTypes;
import com.sekwah.narutomod.mixin.common.AbstractFurnaceAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The upkeep of Amaterasu: a body that is burning without being on fire.
 *
 * <p>The flame is deliberately not vanilla fire. Vanilla fire is put out by water, by rain and by a
 * fire resistance potion, and the whole point of the black flame is that none of those help. So the
 * burn is a timer of its own, written into the victim's persistent data and worked through here:
 * every second it eats a few points of health with its own damage type, and after ten seconds it
 * goes out, leaving only smoke. A victim that dies or is unloaded takes the tag with it, so nothing
 * has to be cleaned up on the way out.</p>
 *
 * <p>The caster is remembered by UUID so kills are credited to them. If they log out mid-burn the
 * flame keeps working, simply without a name to hang the death message on.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class AmaterasuEvents {

    /** Game time the flame goes out on. */
    private static final String UNTIL_TAG = "narutomod:amaterasu_until";

    /** Who lit it, for kill credit. */
    private static final String CASTER_TAG = "narutomod:amaterasu_caster";

    /** How long the black flame burns, in ticks. */
    private static final int DURATION = 200;

    /** How often it bites, and how hard. */
    private static final int DAMAGE_INTERVAL = 20;
    private static final float BURN_DAMAGE = 3.0f;

    /** The bite that lands with the cast itself, so the technique is felt immediately. */
    private static final float IGNITION_DAMAGE = 6.0f;

    /** How long a furnace touched by the black flame keeps cooking ten times as fast. */
    private static final int FURNACE_BOOST_TICKS = 30 * 20;

    /**
     * Extra cooking progress per tick. Vanilla adds one of its own, so nine more makes the furnace
     * run at ten times its normal speed.
     */
    private static final int FURNACE_COOK_BOOST = 9;

    /** A furnace is kept lit while the flame is on it, or the boost would have nothing to burn. */
    private static final int FURNACE_LIT_BOOST = 40;

    /**
     * Furnaces being cooked by the black flame, per dimension, with the game time their boost ends.
     * A block position is enough: the boost is a property of the place the flame was fixed to, not
     * of the block entity instance, so a chunk that unloads and comes back resumes the count.
     */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Long>> FURNACE_BOOSTS = new HashMap<>();

    /**
     * Fixes the black flame onto a creature. Server side only; callers check that the target is a
     * living, sensible one.
     */
    public static void ignite(ServerLevel level, LivingEntity target, Player caster) {
        CompoundTag data = target.getPersistentData();
        data.putLong(UNTIL_TAG, level.getGameTime() + DURATION);
        data.putString(CASTER_TAG, caster.getUUID().toString());
        // Whatever was already burning is irrelevant next to the black flame; clearing it also
        // stops vanilla fire from ticking alongside and doubling the visuals.
        target.clearFire();

        target.hurt(damageSource(level, caster), IGNITION_DAMAGE);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                24, 0.45d, 0.45d, 0.45d, 0.02d);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, target.getX(),
                target.getY() + target.getBbHeight() * 0.6d, target.getZ(),
                10, 0.3d, 0.3d, 0.3d, 0.01d);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
                SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.2f, 0.55f);
    }

    /** @return whether the creature is currently burning with the black flame. */
    public static boolean isBurning(LivingEntity entity) {
        return entity.getPersistentData().contains(UNTIL_TAG);
    }

    /** @return ticks of black flame left on the creature, or 0 when it is not burning. */
    public static int remaining(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(UNTIL_TAG) || !(entity.level() instanceof ServerLevel level)) {
            return 0;
        }
        return (int) Math.max(0L, data.getLong(UNTIL_TAG) - level.getGameTime());
    }

    /**
     * Keeps a flame alive and hands it a new master, which is what Kagutsuchi does when it gathers
     * scattered black fire onto one body. The flame never gets shorter: the later of the two ends
     * wins, so shaping a fire does not put it out.
     */
    public static void refresh(ServerLevel level, LivingEntity target, Player caster, int extraTicks) {
        CompoundTag data = target.getPersistentData();
        long until = data.contains(UNTIL_TAG) ? data.getLong(UNTIL_TAG) : level.getGameTime();
        data.putLong(UNTIL_TAG, Math.max(until, level.getGameTime() + DURATION) + extraTicks);
        data.putString(CASTER_TAG, caster.getUUID().toString());
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, target.getX(),
                target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                12, 0.4d, 0.4d, 0.4d, 0.02d);
    }

    /** Puts the flame out early, with the smoke of it going out. */
    public static void extinguish(LivingEntity entity) {
        CompoundTag data = entity.getPersistentData();
        if (!data.contains(UNTIL_TAG)) {
            return;
        }
        data.remove(UNTIL_TAG);
        data.remove(CASTER_TAG);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.5d, entity.getZ(),
                    14, 0.35d, 0.35d, 0.35d, 0.01d);
        }
    }

    /**
     * Snuffs every black flame around a point, which is Kagutsuchi in its other direction: the same
     * hand that gathers the fire can also close it. Returns how many bodies were carrying it.
     */
    public static int snuffAround(ServerLevel level, net.minecraft.world.phys.Vec3 centre, double radius) {
        int snuffed = 0;
        for (LivingEntity burning : level.getEntitiesOfClass(LivingEntity.class,
                net.minecraft.world.phys.AABB.ofSize(centre, radius * 2.0d, radius * 2.0d, radius * 2.0d),
                AmaterasuEvents::isBurning)) {
            extinguish(burning);
            snuffed++;
        }
        return snuffed;
    }

    /**
     * Stakes the black flame's heat onto every furnace within {@code radius} of the victim. The
     * furnaces then cook at ten times their speed for {@link #FURNACE_BOOST_TICKS}, and are kept
     * lit so the boost has fuel to work with. Positions are remembered rather than block entities:
     * a chunk may unload and come back inside the half minute.
     */
    public static void heatFurnaces(ServerLevel level, Vec3 centre, double radius) {
        Map<BlockPos, Long> boosts = FURNACE_BOOSTS.computeIfAbsent(level.dimension(), key -> new HashMap<>());
        long until = level.getGameTime() + FURNACE_BOOST_TICKS;
        BlockPos min = BlockPos.containing(centre.x - radius, centre.y - radius, centre.z - radius);
        BlockPos max = BlockPos.containing(centre.x + radius, centre.y + radius, centre.z + radius);
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (!level.hasChunkAt(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity) {
                boosts.put(pos.immutable(), until);
            }
        }
    }

    /** Drives the boosted furnaces while their half minute lasts, and forgets them afterwards. */
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Map<BlockPos, Long> boosts = FURNACE_BOOSTS.get(level.dimension());
        if (boosts == null || boosts.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        Iterator<Map.Entry<BlockPos, Long>> iterator = boosts.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, Long> entry = iterator.next();
            if (now >= entry.getValue()) {
                iterator.remove();
                continue;
            }
            BlockPos pos = entry.getKey();
            if (level.hasChunkAt(pos)
                    && level.getBlockEntity(pos) instanceof AbstractFurnaceBlockEntity furnace) {
                stoke(furnace);
            }
        }
    }

    /**
     * Nudges one furnace along by nine extra ticks of cooking. The furnace only finishes a cook when
     * its own tick lands exactly on the total, so this stops one short of it - the vanilla tick that
     * follows completes the item.
     */
    private static void stoke(AbstractFurnaceBlockEntity furnace) {
        AbstractFurnaceAccessor accessor = (AbstractFurnaceAccessor) furnace;
        int total = accessor.naruto$cookingTotalTime();
        if (total > 1 && !furnace.getItem(0).isEmpty()) {
            accessor.naruto$setCookingProgress(Math.min(total - 1,
                    accessor.naruto$cookingProgress() + FURNACE_COOK_BOOST));
        }
        int lit = Math.max(accessor.naruto$litTime(), FURNACE_LIT_BOOST);
        accessor.naruto$setLitTime(lit);
        accessor.naruto$setLitDuration(Math.max(accessor.naruto$litDuration(), lit));
        furnace.setChanged();
    }

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide || !isBurning(entity)) {
            return;
        }
        Level level = entity.level();
        long now = level.getGameTime();
        CompoundTag data = entity.getPersistentData();
        if (now >= data.getLong(UNTIL_TAG)) {
            extinguish(entity);
            return;
        }

        if (now % DAMAGE_INTERVAL == 0) {
            entity.hurt(damageSource(level, casterOf(level, data)), BURN_DAMAGE);
        }

        if (level instanceof ServerLevel serverLevel && entity.tickCount % 3 == 0) {
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, entity.getX(),
                    entity.getY() + entity.getBbHeight() * 0.6d, entity.getZ(),
                    3, 0.3d, 0.35d, 0.3d, 0.01d);
        }
    }

    /** The caster, if they are still online, so the kill is credited and the death message names them. */
    private static Player casterOf(Level level, CompoundTag data) {
        if (!(level instanceof ServerLevel serverLevel) || !data.contains(CASTER_TAG)) {
            return null;
        }
        try {
            UUID id = UUID.fromString(data.getString(CASTER_TAG));
            return serverLevel.getServer().getPlayerList().getPlayer(id);
        } catch (IllegalArgumentException ignored) {
            // A malformed tag is not worth a crash: the flame simply burns unattributed.
            return null;
        }
    }

    private static DamageSource damageSource(Level level, Player caster) {
        return caster == null
                ? NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.AMATERASU)
                : NarutoDamageTypes.getDamageSource(level, NarutoDamageTypes.AMATERASU, caster, caster);
    }
}
