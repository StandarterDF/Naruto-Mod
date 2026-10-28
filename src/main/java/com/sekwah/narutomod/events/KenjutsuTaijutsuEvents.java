package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.abilities.jutsus.CamelliaDanceAbility;
import com.sekwah.narutomod.abilities.jutsus.InnerStrikeAbility;
import com.sekwah.narutomod.abilities.jutsus.PalmBlockAbility;
import com.sekwah.narutomod.abilities.jutsus.ParryAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.item.ComboSwordItem;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * What the kenjutsu and taijutsu stances do that an ability cannot do for itself.
 *
 * <p>A technique knows how to start, how to stop and what it costs; it does not know that a blow is
 * about to land on the caster, and it cannot see the arrow already in the air. The two guards in this
 * pack are therefore split in half: the abilities hold the stance and write the mark, and the rolls,
 * the stamina spent per parry and the answer to the attacker are made here, from the mark, on the
 * server.</p>
 *
 * <p>The same is true of the two timed consequences this block leaves behind. A bleeding wound is a
 * mark on the victim rather than a task owned by the blade that made it, and a creature whose mind
 * was knocked out by an inner strike has to be given it back when the count runs out. Both are
 * enforced here so that they survive the caster walking away, logging out or dying.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KenjutsuTaijutsuEvents {

    /** What an open wound costs its bearer, and how often. */
    private static final float BLEED_DAMAGE = 1.0f;
    private static final int BLEED_INTERVAL = 40;

    private KenjutsuTaijutsuEvents() {
    }

    /**
     * A blow that is about to land on a guarded caster: the blade or the palms may turn it aside, and
     * either answer costs stamina and comes back at the attacker.
     *
     * <p>Only blows with a body behind them are considered - an ordinary swing or something thrown -
     * because the guards are made of a hand and an edge, and neither meets falling damage, fire or a
     * potion. A parried projectile is knocked out of the air and nothing more: there is no arm on the
     * other end of an arrow to cut back.</p>
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide || !victim.isAlive()) {
            return;
        }
        // A technique the caster's own hand threw is not something a guard can meet.
        if (event.getSource().getEntity() == victim) {
            return;
        }
        Entity direct = event.getSource().getDirectEntity();
        Entity sourceEntity = event.getSource().getEntity();
        LivingEntity attacker = sourceEntity instanceof LivingEntity living && living != victim
                ? living
                : null;
        boolean projectile = direct instanceof Projectile;
        if (attacker == null && !projectile) {
            return;
        }

        // The sword first, because it is the better guard when it is up; the two can never both be
        // up at once, since one needs a blade in the hand and the other needs the hands empty.
        // A raised blade is a deliberate move: it turns the next blow aside outright and is then
        // spent, so timing matters rather than luck.
        if (victim instanceof Player p && ParryAbility.isActive(p) && ParryAbility.hasSword(p)
                && payBlock(p, ParryAbility.ID, ParryAbility.BLOCK_STAMINA,
                ParryAbility.BLOCK_REGEN_DELAY)) {
            ParryAbility.consume(p);
            event.setCanceled(true);
            guardSparks(p, true);
            if (attacker != null && attacker.isAlive()) {
                // The counter is the caster's own cut, so it carries their name rather than nobody's.
                attacker.hurt(p.damageSources().thrown(p, p), ParryAbility.COUNTER_DAMAGE);
            }
            return;
        }

        // Great swords held up in a guard (right mouse button) take the edge off a blow instead of
        // turning it aside outright. The heavier blades also shrug off more of it and may send an
        // arrow back the way it came. Clones do the same with a blade in hand.
        if (victim.getMainHandItem().getItem() instanceof ComboSwordItem) {
            boolean holding = victim instanceof Player p && p.isUsingItem()
                    && p.getUseItem().getItem() instanceof ComboSwordItem;
            boolean cloneBlock = victim instanceof ShadowCloneEntity
                    && victim.getRandom().nextFloat() < 0.5f;
            if (holding || cloneBlock) {
                if (direct instanceof Projectile proj) {
                    if (victim.getRandom().nextFloat() < 0.35f) {
                        Vec3 motion = proj.getDeltaMovement();
                        proj.setDeltaMovement(motion.x * -1.0d, motion.y * -1.0d, motion.z * -1.0d);
                        proj.setOwner(victim);
                        event.setCanceled(true);
                        guardSparks(victim, true);
                        return;
                    }
                }
                // Absorb most of the hit, but never all of it.
                event.setAmount(event.getAmount() * 0.35f);
                guardSparks(victim, true);
            }
        }

        if (!(victim instanceof Player p) || attacker == null || !PalmBlockAbility.isActive(p) || !PalmBlockAbility.hasEmptyHands(p)
                || p.getRandom().nextFloat() >= PalmBlockAbility.BLOCK_CHANCE
                || !payBlock(p, PalmBlockAbility.ID, PalmBlockAbility.BLOCK_STAMINA,
                PalmBlockAbility.BLOCK_REGEN_DELAY)) {
            return;
        }
        event.setCanceled(true);
        guardSparks(p, false);
        if (attacker.isAlive()) {
            attacker.hurt(p.damageSources().thrown(p, p), PalmBlockAbility.COUNTER_DAMAGE);
        }
    }

    /**
     * Runs the timed marks left on bodies: a wound that keeps bleeding, and a mind that has to come
     * back when the inner strike's count runs out.
     *
     * <p>The bleed is driven off the world clock rather than a per-entity timer. A wound mark is the
     * only state there is, so every body can be asked the same question - is the mark still in the
     * future, and has the clock reached the next beat - without anything having to remember when the
     * wound was made.</p>
     */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.level().isClientSide) {
            return;
        }
        long now = entity.level().getGameTime();
        CompoundTag data = entity.getPersistentData();

        if (data.contains(InnerStrikeAbility.UNTIL_TAG) && now >= data.getLong(InnerStrikeAbility.UNTIL_TAG)) {
            data.remove(InnerStrikeAbility.UNTIL_TAG);
            if (entity instanceof Mob mob) {
                mob.setNoAi(false);
            }
        }

        long bleedUntil = data.getLong(CamelliaDanceAbility.BLEED_TAG);
        if (bleedUntil <= 0L) {
            return;
        }
        if (bleedUntil <= now) {
            data.remove(CamelliaDanceAbility.BLEED_TAG);
            return;
        }
        if (now % BLEED_INTERVAL == 0L && entity.isAlive() && !entity.isSpectator()) {
            entity.hurt(entity.damageSources().magic(), BLEED_DAMAGE);
        }
    }

    /**
     * Takes the stamina one parry or one caught blow costs, after mastery, and refuses the block when
     * the bar cannot pay it - a guard is a thing the caster can be too spent to keep.
     */
    private static boolean payBlock(Player player, ResourceLocation abilityId, float baseCost, int regenDelay) {
        INinjaData ninjaData = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null) {
            return false;
        }
        Ability ability = NarutoRegistries.ABILITIES == null ? null : NarutoRegistries.ABILITIES.getValue(abilityId);
        float cost = ability == null ? baseCost : Mastery.cost(ninjaData, ability, baseCost);
        if (ninjaData.getStamina() < cost) {
            return false;
        }
        ninjaData.useStamina(cost, regenDelay);
        return true;
    }

    /** The clash itself: a spark where the blow met the guard, and what a meeting like that sounds like. */
    private static void guardSparks(LivingEntity player, boolean blade) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A blade guards at the hand that holds it; a palm guard is both palms, so the spark is
        // thrown from the middle of the chest where the two of them meet.
        Vec3 guard = blade && player instanceof Player p
                ? JutsuEffects.handPosition(p)
                : player.position().add(0.0d, player.getBbHeight() * 0.75d, 0.0d);
        level.sendParticles(ParticleTypes.CRIT, guard.x, guard.y, guard.z,
                10, blade ? 0.2d : 0.35d, 0.2d, blade ? 0.2d : 0.35d, 0.05d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                blade ? SoundEvents.SHIELD_BLOCK : SoundEvents.PLAYER_ATTACK_NODAMAGE,
                SoundSource.PLAYERS, 0.8f, blade ? 1.2f : 1.0f);
    }
}
