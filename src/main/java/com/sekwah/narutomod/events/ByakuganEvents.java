package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.friends.Friends;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Gentle Fist: the Byakugan's answer to a body that fights with chakra.
 *
 * <p>The eye already sees the network of channels under the skin; a palm strike on that picture
 * closes them for a moment. Every melee hit the caster lands while the eye is open drains chakra
 * from the victim and seals the flow, so a ninja caught in it cannot cast for a few seconds and a
 * beast is left weak and slow. There is no extra damage in it at all - the technique wins by taking
 * the fight away from the other body instead of by out-damaging it.</p>
 *
 * <p>The seal is a game-time stamp in the victim's persistent data, the same shape the other
 * on-body states of the mod use, so it survives a relog and needs no cleanup. The cast packets ask
 * {@link #isSealed(Player)} before a technique starts; closing an eye that is already burning is
 * still allowed, or a sealed caster would be trapped in an upkeep they cannot stop paying.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ByakuganEvents {

    /** Game time at which the sealed channels open again. */
    private static final String SEAL_UNTIL_TAG = "narutomod:tenketsu_until";

    /** How long a struck ninja cannot start a technique, in ticks. */
    private static final int SEAL_TICKS = 60;

    /** Chakra taken out of a struck ninja by one palm, before any resistance. */
    private static final float CHAKRA_DRAIN = 10.0f;

    /** How long a struck mob is left weak, in ticks. */
    private static final int MOB_WEAKNESS_TICKS = 100;

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        DamageSource source = event.getSource();
        // Only the palm itself: a thrown weapon still carries the caster's name, but the fist is
        // what has the channels under it.
        if (!(source.getDirectEntity() instanceof Player attacker) || attacker == victim) {
            return;
        }
        if (victim.isSpectator() || !victim.isAlive()) {
            return;
        }
        if (Friends.areFriends(attacker, victim) || victim.isAlliedTo(attacker)) {
            return;
        }

        INinjaData ninjaData = attacker.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !ByakuganAbility.isActive(ninjaData)) {
            return;
        }

        if (victim instanceof Player sealed) {
            sealed.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(targetData ->
                    targetData.useChakra(Math.min(CHAKRA_DRAIN, targetData.getChakra()), 20));
            seal(sealed, fieldTicks(attacker));
        } else if (victim instanceof Mob mob) {
            mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, MOB_WEAKNESS_TICKS, 0, false, false));
            mob.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, MOB_WEAKNESS_TICKS, 0, false, false));
        }
        showPalm(victim);
    }

    /** Closes the victim's channels for {@link #SEAL_TICKS}, refreshing an older seal. */
    public static void seal(Player victim) {
        seal(victim, SEAL_TICKS);
    }

    /** Closes the victim's channels for {@code ticks}, refreshing a shorter older seal. */
    public static void seal(Player victim, int ticks) {
        CompoundTag data = victim.getPersistentData();
        long until = victim.level().getGameTime() + Math.max(1, ticks);
        if (data.getLong(SEAL_UNTIL_TAG) >= until) {
            return;
        }
        data.putLong(SEAL_UNTIL_TAG, until);
        victim.displayClientMessage(Component.translatable("jutsu.tenketsu.sealed")
                .withStyle(ChatFormatting.RED), true);
    }

    /** Eight Trigrams Field doubles the weight of a palm while the stance is held. */
    private static int fieldTicks(Player attacker) {
        boolean field = attacker.getPersistentData().getLong("narutomod:eight_trigrams_field_until")
                > attacker.level().getGameTime();
        return field ? SEAL_TICKS * 2 : SEAL_TICKS;
    }

    /** @return whether the caster's channels are sealed right now. */
    public static boolean isSealed(Player player) {
        long until = player.getPersistentData().getLong(SEAL_UNTIL_TAG);
        return until > 0L && until > player.level().getGameTime();
    }

    /** @return ticks left on the seal, or 0 when the channels are open. */
    public static int sealedFor(Player player) {
        long until = player.getPersistentData().getLong(SEAL_UNTIL_TAG);
        return (int) Math.max(0L, until - player.level().getGameTime());
    }

    /** A short burst of pale chakra at the struck body, as the channels close. */
    private static void showPalm(LivingEntity victim) {
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 chest = victim.position().add(0.0d, victim.getBbHeight() * 0.55d, 0.0d);
        level.sendParticles(ParticleTypes.END_ROD, chest.x, chest.y, chest.z,
                5, 0.18d, 0.18d, 0.18d, 0.02d);
        level.sendParticles(ParticleTypes.ENCHANT, chest.x, chest.y, chest.z,
                4, 0.25d, 0.25d, 0.25d, 0.03d);
    }
}
