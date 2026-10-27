package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.jutsus.AmaterasuAbility;
import com.sekwah.narutomod.abilities.jutsus.IzanagiAbility;
import com.sekwah.narutomod.abilities.jutsus.IzanamiAbility;
import com.sekwah.narutomod.abilities.jutsus.KagutsuchiAbility;
import com.sekwah.narutomod.abilities.jutsus.IllusoryDoubleAbility;
import com.sekwah.narutomod.abilities.jutsus.KotoamatsukamiAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.abilities.jutsus.SusanooAbility;
import com.sekwah.narutomod.abilities.jutsus.TsukuyomiAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * What an open Sharingan does to a body that is being hit, and what the Mangekyo costs the eye.
 *
 * <p>The eye itself is a stance and lives in {@link SharinganAbility}; the read that softens a blow
 * lives in {@link SharinganEvents}. This class is the layer above both, the consequences that need a
 * memory of an earlier hit: the moment after a read in which the caster is already somewhere else,
 * the same hand being read out of the same swing, and the price the deep techniques charge the eye
 * for being used at all.</p>
 *
 * <p>Nothing is remembered here as a field holding an entity. Every mark is a number in the
 * persistent data of the body it belongs to - the caster for the dodge window, the victim for the
 * styles it has been shown, the caster again for the strain and for the ribs - so a mark survives a
 * relog, a dimension change and a server restart, and dies with the body instead of keeping a chunk
 * of the world loaded for as long as it lasts.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class SharinganAdvancedEvents {

    /** Game time at which the eye can read another blow, written by {@link SharinganEvents}. */
    private static final String READ_UNTIL_TAG = "narutomod:sharingan_read_until";

    /** The last read this class has already turned into a dodge window, so one read arms one dodge. */
    private static final String READ_SEEN_TAG = "narutomod:sharingan_read_seen";

    /** Game time at which the caster is still moving in the eye's picture of the fight. */
    private static final String DODGE_UNTIL_TAG = "narutomod:sharingan_dodge_until";

    /** How long the picture lasts after a read - one clean step, not a shield. */
    private static final int DODGE_WINDOW_TICKS = 10;

    /** Per attacker: stacks of that attacker's style the victim has been shown. */
    private static final String ADAPT_STACKS_PREFIX = "narutomod:adapt_";

    /** Per attacker: the game time its style was last seen, which is what opens and closes a window. */
    private static final String ADAPT_TICK_PREFIX = "narutomod:adapt_tick_";

    /** One observation window: a style seen again inside two seconds is the same style. */
    private static final int ADAPT_WINDOW_TICKS = 40;

    /** How long an untouched pair is remembered before both marks are dropped. */
    private static final int ADAPT_FORGET_TICKS = 200;

    /** What one read blow is worth off the next one, and how many reads a single style can yield. */
    private static final float ADAPT_PER_STACK = 0.03f;
    private static final int ADAPT_MAX_STACKS = 5;

    /** What is left of a blow caught on the ribs of the Susanoo. */
    private static final float SUSANOO_ABSORB = 0.60f;

    /** How much strain the eye carries, and where each threshold sits. */
    private static final String STRAIN_TAG = "narutomod:sharingan_strain";
    private static final String STRAIN_USED_TAG = "narutomod:sharingan_strain_used";
    private static final String STRAIN_SEEN_PREFIX = "narutomod:strain_casts_";
    private static final int STRAIN_MAX = 100;
    private static final int STRAIN_BLIND_AT = 80;
    private static final int STRAIN_CLOSE_AT = 100;
    private static final int STRAIN_AFTER_CLOSE = 60;
    private static final int STRAIN_BLIND_TICKS = 40;

    /** What one cast of a Mangekyo technique costs the eye, on top of the chakra. */
    private static final int STRAIN_PER_CAST = 8;

    /** What a whole second of standing inside the Susanoo costs the eye. */
    private static final int STRAIN_PER_SUSANOO_SECOND = 1;

    /** How long the eye rests between single points of recovery. */
    private static final int STRAIN_RECOVERY_TICKS = 100;

    /** How often the strain ledger is walked; a cast lands within a quarter of a second of this. */
    private static final int STRAIN_PASS_TICKS = 5;

    /** How often the style marks are swept for stale pairs. */
    private static final int ADAPT_SWEEP_TICKS = 20;

    /** How far an illusion pulls a mob's attention away from its caster. */
    private static final double ILLUSORY_TAUNT_RANGE = 16.0d;

    /**
     * The techniques that cost the eye itself. They are the Mangekyo: everything the Sharingan can
     * do that the ordinary eye cannot, and every one of them is paid for twice - in chakra when it is
     * cast, and in sight when it has been cast too often.
     */
    private static final List<ResourceLocation> MANGEKYO = List.of(
            SusanooAbility.ID,
            AmaterasuAbility.ID,
            KagutsuchiAbility.ID,
            TsukuyomiAbility.ID,
            IzanagiAbility.ID,
            IzanamiAbility.ID,
            KotoamatsukamiAbility.ID);

    private SharinganAdvancedEvents() {
    }

    // ---------------------------------------------------------------------------------------------
    // Blows: the dodge window, the ribs, an adapted style, and a decoy that cannot hurt anybody.
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }

        // An illusion has no hands: whatever a tagged copy swings with lands nowhere.
        if (isIllusory(event.getSource().getEntity()) || isIllusory(event.getSource().getDirectEntity())) {
            event.setCanceled(true);
            return;
        }

        if (!(victim instanceof Player player) || player.isSpectator()) {
            return;
        }
        long now = player.level().getGameTime();
        CompoundTag data = player.getPersistentData();

        // The step after a read. It comes before everything else on purpose: a blow inside this
        // window is not reduced, it is somewhere else, so no other rule may take a bite of it first.
        if (!event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && data.getLong(DODGE_UNTIL_TAG) > now) {
            data.remove(DODGE_UNTIL_TAG);
            event.setCanceled(true);
            showDodge(player);
            return;
        }

        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);

        // The ribs of the Susanoo stand between the caster and whatever is coming: the blow is taken
        // in full, but the same amount is drawn from chakra instead of health.
        if (data.getLong(SusanooAbility.UNTIL_TAG) > now) {
            float damage = event.getAmount();
            float chakra = (ninjaData == null) ? 0.0f : ninjaData.getChakra();
            float absorbed = Math.min(damage, chakra);
            if (ninjaData != null) {
                ninjaData.useChakra(absorbed, 0);
            }
            float leftover = damage - absorbed;
            // At most half a heart (1 HP) gets through, even when chakra runs out.
            event.setAmount(Math.min(leftover, 1.0f));
        }

        if (ninjaData == null || !SharinganAbility.isActive(ninjaData)) {
            return;
        }
        adapt(event, player, data, now);
    }

    /**
     * The same hand hitting the same body inside one observation window is a style, and a style that
     * has been read once is read more cheaply every time after. The first blow of a window costs
     * nothing to learn; the fifth read of the same attacker is where the cap sits.
     */
    private static void adapt(LivingHurtEvent event, Player victim, CompoundTag data, long now) {
        Entity attacker = event.getSource().getEntity();
        if (attacker == null || attacker == victim) {
            return;
        }
        String uuid = attacker.getStringUUID();
        String stacksKey = ADAPT_STACKS_PREFIX + uuid;
        String tickKey = ADAPT_TICK_PREFIX + uuid;

        boolean sameWindow = now - data.getLong(tickKey) <= ADAPT_WINDOW_TICKS;
        int stacks = sameWindow
                ? Math.min(ADAPT_MAX_STACKS, data.getInt(stacksKey) + 1)
                : 1;
        data.putInt(stacksKey, stacks);
        data.putLong(tickKey, now);
        event.setAmount(event.getAmount() * (1.0f - ADAPT_PER_STACK * stacks));
    }

    /** @return whether this entity is one of the illusion's copies rather than a body. */
    private static boolean isIllusory(Entity entity) {
        return entity != null && entity.getPersistentData().getBoolean(IllusoryDoubleAbility.MARK_TAG);
    }

    // ---------------------------------------------------------------------------------------------
    // Per tick: arming the dodge window, the strain ledger, and the seams of the illusion.
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.isSpectator()) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        long now = player.level().getGameTime();

        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        boolean eyeOpen = ninjaData != null && SharinganAbility.isActive(ninjaData);
        if (eyeOpen) {
            armDodge(data, now);
        } else {
            // A closed eye neither reads nor waits to step aside: a window banked before the eye
            // shut must not be spendable minutes later.
            data.remove(DODGE_UNTIL_TAG);
        }

        if (player.tickCount % ADAPT_SWEEP_TICKS == 0) {
            forgetStaleAdaptation(data, now);
        }
        // The ledger only matters while the eye is open or still paying for having been open; a
        // ninja who has never used the Sharingan is not walked every five ticks for nothing.
        boolean strained = eyeOpen || data.getInt(STRAIN_TAG) > 0;
        if (strained && ninjaData != null && player.tickCount % STRAIN_PASS_TICKS == 0) {
            tickStrain(player, ninjaData, data, now);
        }
    }

    /**
     * The read that {@link SharinganEvents} performed leaves a cooldown mark behind. The first time
     * this class sees a new mark, the eye has just finished reading a blow: the caster is already
     * moving in the picture it made, and the next blow to arrive inside the window never lands.
     */
    private static void armDodge(CompoundTag data, long now) {
        long readUntil = data.getLong(READ_UNTIL_TAG);
        if (readUntil <= now || readUntil == data.getLong(READ_SEEN_TAG)) {
            return;
        }
        data.putLong(READ_SEEN_TAG, readUntil);
        data.putLong(DODGE_UNTIL_TAG, now + DODGE_WINDOW_TICKS);
    }

    /**
     * Drops the memory of a pair that has not been seen for ten seconds. A fight is long, a body has
     * many attackers, and a mark that is never cleaned would follow the victim for the rest of the
     * world's life.
     */
    private static void forgetStaleAdaptation(CompoundTag data, long now) {
        for (String key : new ArrayList<>(data.getAllKeys())) {
            if (!key.startsWith(ADAPT_TICK_PREFIX) || now - data.getLong(key) <= ADAPT_FORGET_TICKS) {
                continue;
            }
            data.remove(key);
            data.remove(ADAPT_STACKS_PREFIX + key.substring(ADAPT_TICK_PREFIX.length()));
        }
    }

    /**
     * The eye's ledger of strain: every Mangekyo cast is found by its own cast count rather than by a
     * hook in each technique, so a technique added later only needs its id in {@link #MANGEKYO}. The
     * count is read from mastery, which every cast already moves, and the last number this class saw
     * is what turns a difference into one cast.
     */
    private static void tickStrain(Player player, INinjaData ninjaData, CompoundTag data, long now) {
        boolean running = false;
        for (ResourceLocation technique : MANGEKYO) {
            running |= countCast(ninjaData, data, technique, now);
        }
        if (SusanooAbility.isActive(ninjaData)) {
            running = true;
            if (player.tickCount % 20 == 0) {
                addStrain(data, now, STRAIN_PER_SUSANOO_SECOND);
            }
        }

        int strain = data.getInt(STRAIN_TAG);
        if (strain >= STRAIN_BLIND_AT) {
            // Held open above the threshold, the eye stops seeing: refreshed so it stays shut while
            // the strain is still high, and left to run out on its own once it has been rested.
            player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,
                    STRAIN_BLIND_TICKS, 0, false, false));
        }
        if (strain >= STRAIN_CLOSE_AT) {
            closeBurntOutEye(player, ninjaData, data);
            return;
        }

        if (running || now - data.getLong(STRAIN_USED_TAG) < STRAIN_RECOVERY_TICKS) {
            return;
        }
        data.putLong(STRAIN_USED_TAG, now);
        data.putInt(STRAIN_TAG, Math.max(0, strain - 1));
    }

    /**
     * Turns one new cast of a technique into strain.
     *
     * <p>A technique the ledger has never seen is recorded without charge: a save made before the
     * ledger existed already holds a history of casts, and billing it all at once would blind a
     * caster the first time they logged in.</p>
     *
     * @return whether a cast was found, in which case the eye is busy and does not rest.
     */
    private static boolean countCast(INinjaData ninjaData, CompoundTag data,
                                     ResourceLocation technique, long now) {
        int casts = ninjaData.getMasteryData().getCasts(technique);
        String key = STRAIN_SEEN_PREFIX + technique.getPath();
        if (!data.contains(key)) {
            data.putInt(key, casts);
            return false;
        }
        int seen = data.getInt(key);
        if (casts <= seen) {
            return false;
        }
        data.putInt(key, casts);
        addStrain(data, now, STRAIN_PER_CAST * (casts - seen));
        return true;
    }

    /** Adds strain up to the cap, and remembers that the eye was working, which holds recovery back. */
    private static void addStrain(CompoundTag data, long now, int amount) {
        data.putLong(STRAIN_USED_TAG, now);
        data.putInt(STRAIN_TAG, Math.min(STRAIN_MAX, data.getInt(STRAIN_TAG) + amount));
    }

    /**
     * The eye has been burnt to its limit: it shuts itself, the ribs go with it and the caster is
     * left half blind for a while. What is left of the strain stays on the books, so an eye that is
     * forced open again does not start from a clean slate.
     */
    private static void closeBurntOutEye(Player player, INinjaData ninjaData, CompoundTag data) {
        data.putInt(STRAIN_TAG, STRAIN_AFTER_CLOSE);
        data.putLong(STRAIN_USED_TAG, player.level().getGameTime());
        player.displayClientMessage(Component.translatable("jutsu.sharingan.strain")
                .withStyle(ChatFormatting.RED), true);
        closeStance(player, ninjaData, SusanooAbility.ID);
        closeStance(player, ninjaData, SharinganAbility.ID);
    }

    /** Shuts one stance on the caster's data, whichever system is asking. */
    private static void closeStance(Player player, INinjaData ninjaData, ResourceLocation abilityId) {
        if (!ninjaData.getToggleAbilityData().getAbilitiesHashSet().contains(abilityId)) {
            return;
        }
        Ability ability = NarutoRegistries.ABILITIES.getValue(abilityId);
        if (ability != null) {
            ninjaData.getToggleAbilityData().removeAbilityEnded(player, ninjaData, ability);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The illusion: its lifetime, and the attention it steals.
    // ---------------------------------------------------------------------------------------------

    /** The copy comes apart where it stands once its six seconds are up. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        CompoundTag data = entity.getPersistentData();
        if (!data.getBoolean(IllusoryDoubleAbility.MARK_TAG)) {
            return;
        }
        if (entity.level().getGameTime() < data.getLong(IllusoryDoubleAbility.UNTIL_TAG)) {
            return;
        }
        data.remove(IllusoryDoubleAbility.MARK_TAG);
        data.remove(IllusoryDoubleAbility.UNTIL_TAG);
        if (entity.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CLOUD, entity.getX(),
                    entity.getY() + entity.getBbHeight() / 2.0d, entity.getZ(),
                    16, 0.35d, 0.45d, 0.35d, 0.02d);
        }
        entity.discard();
    }

    /**
     * A creature that was about to lunge at the caster finds the illusion in front of them instead.
     * Only ever a redirection of a choice the mob had already made, never a new grudge: the copy
     * cannot be fought for long, and a mob left without its real quarry when the illusion ends simply
     * picks again.
     */
    @SubscribeEvent
    public static void onTargetChange(LivingChangeTargetEvent event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide) {
            return;
        }
        if (!(event.getNewTarget() instanceof Player owner)) {
            return;
        }
        ShadowCloneEntity illusion = nearestIllusion(mob, owner);
        if (illusion != null) {
            event.setNewTarget(illusion);
        }
    }

    /** @return the closest standing illusion of this caster, or {@code null} when there is none near. */
    private static ShadowCloneEntity nearestIllusion(Mob mob, Player owner) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        UUID caster = owner.getUUID();
        long now = level.getGameTime();
        List<ShadowCloneEntity> copies = level.getEntitiesOfClass(ShadowCloneEntity.class,
                mob.getBoundingBox().inflate(ILLUSORY_TAUNT_RANGE), copy ->
                        copy.isAlive()
                                && copy.getPersistentData().getBoolean(IllusoryDoubleAbility.MARK_TAG)
                                && copy.getPersistentData().getLong(IllusoryDoubleAbility.UNTIL_TAG) > now
                                && copy.getOwnerUUID().map(caster::equals).orElse(false));
        return copies.isEmpty() ? null : copies.get(0);
    }

    // ---------------------------------------------------------------------------------------------
    // The little pictures the two new rules need.
    // ---------------------------------------------------------------------------------------------

    /** The caster is a blur where the blow was aimed, and the miss is heard as a miss. */
    private static void showDodge(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                4, 0.5d, 0.3d, 0.5d, 0.0d);
        level.sendParticles(ParticleTypes.CRIT, player.getX(),
                player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                10, 0.4d, 0.4d, 0.4d, 0.03d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.7f, 1.7f);
    }
}
