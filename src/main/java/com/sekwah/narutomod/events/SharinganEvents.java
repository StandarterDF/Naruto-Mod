package com.sekwah.narutomod.events;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The copy wheel reading a blow before it lands.
 *
 * <p>Every hit that comes from a body the caster can see is already moving in the Sharingan's
 * picture a moment before it arrives, and once in a while the caster is simply somewhere else in
 * that moment: the strike is mostly a miss, and the eye is spent for a few seconds while it works
 * out the next one. The reduction is not a shield - it never takes the hit to nothing - and the
 * cooldown is long enough that the eye buys one clean read per exchange rather than permanent
 * damage immunity.</p>
 *
 * <p>The read is better the more the eye has been lived in: mastery shortens nothing here, it
 * deepens the dodge, so a caster who has held the Sharingan for a long time reads more of the blow
 * than a beginner. The state lives in the player's persistent data, so it survives a relog and is
 * not reset by dying.</p>
 */
@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class SharinganEvents {

    /** Game time at which the eye can read another blow. */
    private static final String READ_UNTIL_TAG = "narutomod:sharingan_read_until";

    /** How long the read stays spent after it saves the caster, in ticks. */
    private static final int READ_COOLDOWN = 100;

    /** How much of a read blow is taken off before mastery. */
    private static final float BASE_DODGE = 0.75f;

    /** Extra share of the blow the read takes off at full mastery, on top of {@link #BASE_DODGE}. */
    private static final float MASTERY_DODGE = 0.10f;

    /**
     * A blow that ignores invulnerability - the void, /kill, a scripted execution - is not a blow
     * the eye can read, and neither is anything the caster did to themselves.
     */
    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        if (event.isCanceled() || event.getAmount() <= 0.0f) {
            return;
        }
        DamageSource source = event.getSource();
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return;
        }
        Entity attacker = source.getEntity();
        if (attacker == null || attacker == player) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
            if (!SharinganAbility.isActive(ninjaData)) {
                return;
            }
            long now = player.level().getGameTime();
            CompoundTag data = player.getPersistentData();
            if (data.getLong(READ_UNTIL_TAG) > now) {
                return;
            }

            float mastery = Mastery.level(ninjaData, SharinganAbility.ID);
            float dodge = Math.min(0.9f, BASE_DODGE + MASTERY_DODGE * mastery);
            event.setAmount(event.getAmount() * (1.0f - dodge));
            data.putLong(READ_UNTIL_TAG, now + READ_COOLDOWN);
            showRead(player, attacker);
        });
    }

    /** The little picture of the dodge: the caster's outline blurs, and the hit is heard as a miss. */
    private static void showRead(Player player, Entity attacker) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, player.getX(),
                player.getY() + player.getBbHeight() * 0.5d, player.getZ(),
                3, 0.45d, 0.25d, 0.45d, 0.0d);
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(),
                player.getY() + player.getBbHeight() * 0.65d, player.getZ(),
                8, 0.35d, 0.35d, 0.35d, 0.03d);
        // A small drift of red chakra from the eye towards the body that threw the blow, so the
        // player can tell which attacker was read.
        level.sendParticles(ParticleTypes.CRIT,
                (player.getX() + attacker.getX()) * 0.5d,
                (player.getY() + attacker.getY() + attacker.getBbHeight() * 0.5d) * 0.5d,
                (player.getZ() + attacker.getZ()) * 0.5d,
                6, 0.25d, 0.25d, 0.25d, 0.02d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.6f, 1.4f);
    }
}
