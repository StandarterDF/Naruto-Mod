package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganMarkAbility;
import com.sekwah.narutomod.abilities.jutsus.CircularBlockAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillTree;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;

/**
 * What the Byakugan does to a body that is not the caster's own, and what an open Sharingan is
 * worth to the panel that draws it.
 *
 * <p>Three small rules live here, none of which deserves a class of its own: the circular block
 * that refuses a blow now and then, the pure flow that pushes a weakening effect off a body faster
 * while the eye is reading it, and the upkeep of the mark a Byakugan leaves on somebody. The mark
 * itself is only ever written by {@link ByakuganMarkAbility}; this class keeps the glow honest for
 * as long as the stamp says it should last, and then lets both go.</p>
 *
 * <p>The last part is not a rule at all: {@link #tomoeStage(INinjaData)} and
 * {@link #panelRadius(INinjaData)} are the read-what-the-tree-taught-us helpers the client panel
 * uses. The three tomoe are the existing training nodes of the Sharingan branch - there are no
 * separate stage nodes to keep in sync, so the panel cannot disagree with the tree.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID)
public final class ByakuganAdvancedEvents {

    /** How often the circle reads a blow before it lands: two times in five. */
    private static final float BLOCK_CHANCE = 0.40f;

    /** How hard a refused blow shoves its thrower off the circle, and how high it lifts them. */
    private static final double PUSH_BACK = 0.4d;
    private static final double PUSH_LIFT = 0.05d;

    /** The body's own weakening effects, which the pure flow carries off faster. */
    private static final List<MobEffect> DRAINED = List.of(
            MobEffects.WEAKNESS,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.DIG_SLOWDOWN);

    /** How often the flow is pushed along, in ticks - one extra tick of effect per pass. */
    private static final int PURE_FLOW_INTERVAL = 10;

    /** How far the caster's open eye carries the flow to their own side, in blocks. */
    private static final double PURE_FLOW_RANGE = 8.0d;

    /**
     * The node that lets a body share the flow. Read from the ally's own tree: the technique widens
     * the eye's reading to whoever has learned to read with it, not to whoever happens to stand near.
     */
    private static final String PURE_FLOW_UNLOCK = "mechanic:byakugan_pure_flow";

    /** How often the glow of a mark is renewed, in ticks. */
    private static final int MARK_REFRESH_TICKS = 100;

    /**
     * The tomoe stages of the Sharingan, in the order the eye earns them. These are ordinary
     * training nodes that already exist; a stage is simply the highest one the caster has bought.
     */
    private static final String[] TOMOE_NODES = {
            "sharingan_tomoe",
            "sharingan_mangekyo",
            "sharingan_insight"
    };

    /** What the panel reaches before any tomoe, and what every stage adds to it. */
    private static final int PANEL_BASE_RADIUS = 8;
    private static final int PANEL_RADIUS_PER_STAGE = 8;

    private ByakuganAdvancedEvents() {
    }

    // ---------------------------------------------------------------------------------------------
    // The circular block: a blow that was read before it arrived.
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player caster) || caster.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        if (caster.isSpectator() || caster.isCreative()) {
            return;
        }
        DamageSource source = event.getSource();
        // Falling, fire, the void and their kin have no line to read: only a body and a shot do.
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof Projectile) && !(direct instanceof LivingEntity)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker == null || attacker == caster) {
            return;
        }
        if (!CircularBlockAbility.isActive(caster)) {
            return;
        }
        if (caster.getRandom().nextFloat() >= BLOCK_CHANCE) {
            return;
        }

        event.setCanceled(true);
        pushAttacker(caster, attacker);
        showGuard(caster);
    }

    /** Shoves whoever threw the refused blow back off the circle. */
    private static void pushAttacker(Player caster, Entity attacker) {
        Vec3 away = attacker.position().subtract(caster.position());
        if (away.lengthSqr() < 0.01d) {
            away = caster.getLookAngle().reverse();
        }
        away = new Vec3(away.x, 0.0d, away.z).normalize().scale(PUSH_BACK);
        attacker.push(away.x, PUSH_LIFT, away.z);
        attacker.hurtMarked = true;
    }

    /** The ring flares where the blow was turned away, and the block is heard as a block. */
    private static void showGuard(Player caster) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        double y = caster.getY() + caster.getBbHeight() * 0.55d;
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, caster.getX(), y, caster.getZ(),
                2, 0.5d, 0.2d, 0.5d, 0.0d);
        level.sendParticles(ParticleTypes.END_ROD, caster.getX(), y, caster.getZ(),
                6, 0.45d, 0.25d, 0.45d, 0.02d);
        level.playSound(null, caster.getX(), caster.getY(), caster.getZ(),
                SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.4f, 1.6f);
    }

    // ---------------------------------------------------------------------------------------------
    // The pure flow: an open eye pushes a weakening effect off a body faster.
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        if (player.isSpectator() || player.tickCount % PURE_FLOW_INTERVAL != 0) {
            return;
        }
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !ByakuganAbility.isActive(ninjaData)) {
            return;
        }
        accelerate(player);
        shareFlow(player);
    }

    /**
     * The caster's own reading pushes one more tick off every weakening effect each pass. The
     * effects are re-given one tick shorter rather than edited in place, so the client's copy of the
     * timer is never further from the server's than a single pass.
     */
    private static void accelerate(LivingEntity body) {
        for (MobEffect effect : DRAINED) {
            MobEffectInstance instance = body.getEffect(effect);
            if (instance == null) {
                continue;
            }
            int amplifier = instance.getAmplifier();
            boolean ambient = instance.isAmbient();
            boolean visible = instance.isVisible();
            boolean icon = instance.showIcon();
            int duration = instance.getDuration() - 1;
            body.removeEffect(effect);
            if (duration > 0) {
                body.addEffect(new MobEffectInstance(effect, duration, amplifier, ambient, visible, icon));
            }
        }
    }

    /**
     * The same flow reaches the caster's own side inside eight blocks, but only to a body that has
     * learned to read with the eye itself - see {@link #PURE_FLOW_UNLOCK}.
     */
    private static void shareFlow(Player caster) {
        List<Player> allies = caster.level().getEntitiesOfClass(Player.class,
                caster.getBoundingBox().inflate(PURE_FLOW_RANGE),
                other -> other != caster && Friends.isOwnSide(caster, other));
        for (Player ally : allies) {
            if (ally.distanceToSqr(caster) > PURE_FLOW_RANGE * PURE_FLOW_RANGE) {
                continue;
            }
            INinjaData allyData = ally.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
            if (allyData == null
                    || !SkillTree.hasUnlock(allyData.getSkillData().getUnlocked(), PURE_FLOW_UNLOCK)) {
                continue;
            }
            accelerate(ally);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The mark: a glow that lasts exactly as long as the stamp says it does.
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity body = event.getEntity();
        if (body.level().isClientSide) {
            return;
        }
        CompoundTag data = body.getPersistentData();
        long until = data.getLong(ByakuganMarkAbility.UNTIL_TAG);
        if (until <= 0L) {
            return;
        }
        long now = body.level().getGameTime();
        if (now >= until) {
            // The stamp is informational: nothing is enforced while it is live, so it simply lapses.
            data.remove(ByakuganMarkAbility.UNTIL_TAG);
            data.remove(ByakuganMarkAbility.SOURCE_TAG);
            return;
        }
        if (body.tickCount % MARK_REFRESH_TICKS == 0) {
            // Renewed with what is left rather than a fresh minute, so a stolen or dispelled glow
            // comes back for the rest of the mark and not a tick longer.
            body.addEffect(new MobEffectInstance(MobEffects.GLOWING, (int) (until - now), 0, false, false));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The Sharingan panel: how deep the eye has been lived in.
    // ---------------------------------------------------------------------------------------------

    /**
     * The tomoe stage of an eye, from one to three, or zero when none of the three tomoe nodes has
     * been bought. A stage is the highest node the caster owns, so the panel never shows more than
     * the tree has actually taught.
     */
    public static int tomoeStage(INinjaData ninjaData) {
        if (ninjaData == null) {
            return 0;
        }
        Set<ResourceLocation> unlocked = ninjaData.getSkillData().getUnlocked();
        for (int stage = TOMOE_NODES.length; stage >= 1; stage--) {
            Skill node = SkillTree.byPath(TOMOE_NODES[stage - 1]);
            if (node != null && SkillTree.isUnlocked(unlocked, node)) {
                return stage;
            }
        }
        return 0;
    }

    /** How far the eye's panel reaches with the stages the caster has: eight blocks per tomoe. */
    public static int panelRadius(INinjaData ninjaData) {
        return PANEL_BASE_RADIUS + PANEL_RADIUS_PER_STAGE * tomoeStage(ninjaData);
    }
}
