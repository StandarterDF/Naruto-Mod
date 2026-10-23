package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The spinning sphere of chakra, shaped in the hand and driven into whatever is in front.
 *
 * <p>It is a channelled technique, which is where the whole thing comes from: the caster holds the
 * keys down and the sphere grows in their hands, eating chakra every tick it is held. Let go and it
 * goes off with as much as it had gathered; hold it too long and it stops getting any bigger, so
 * there is no reason to sit on it forever. A fumbled tap that never got going does nothing at
 * all.</p>
 */
public class RasenganJutsuAbility extends Ability implements Ability.Channeled {
    /**
     * How far a cast of this technique carries as a game event. A blast, heard a long way off.
     */
    @Override
    public int noiseRadius() {
        return 40;
    }


    /** C B B C B */
    public static final long COMBO = 13313L;

    /** Ticks of channelling before the sphere is worth throwing. */
    private static final int MIN_CHARGE = 20;

    /** Ticks at which the sphere is as big as it gets. */
    private static final int FULL_CHARGE = 60;

    /** Chakra burned for every tick it is held. */
    private static final float CHAKRA_PER_TICK = 0.6f;

    private static final float MIN_DAMAGE = 4.0f;
    private static final float MAX_DAMAGE = 16.0f;

    /** How far in front of the caster the sphere reaches at its smallest and at full size. */
    private static final double MIN_REACH = 2.0d;
    private static final double MAX_REACH = 3.5d;

    @Override
    public ActivationType activationType() {
        return ActivationType.CHANNELED;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        float cost = this.masteryCost(ninjaData, CHAKRA_PER_TICK);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    /**
     * The sphere spinning in the caster's hands while it is being held.
     */
    @Override
    public void handleChannelling(Player player, INinjaData ninjaData, int ticksChanneled) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.CLOUD, hand.x, hand.y, hand.z, 4, 0.12d, 0.12d, 0.12d, 0.02d);
        if (ticksChanneled % 5 == 0) {
            level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 3, 0.15d, 0.15d, 0.15d, 0.05d);
        }
    }

    /**
     * Where the sphere sits: at the caster's right hand, worked out from the body rather than from
     * the gaze. See {@link JutsuEffects#handPosition(net.minecraft.world.entity.player.Player)}.
     */
    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // A tap that never charged: nothing to throw.
        if (ticksActive < MIN_CHARGE) {
            level.playSound(null, player, SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.5f, 1.4f);
            return;
        }

        float power = Math.min(ticksActive, FULL_CHARGE) / (float) FULL_CHARGE;
        float damage = MIN_DAMAGE + (MAX_DAMAGE - MIN_DAMAGE) * power;
        double reach = MIN_REACH + (MAX_REACH - MIN_REACH) * power;

        Vec3 look = player.getLookAngle();
        Vec3 centre = player.position()
                .add(look.scale(reach * 0.7d))
                .add(0.0d, player.getBbHeight() * 0.5d, 0.0d);
        AABB area = new AABB(centre, centre).inflate(reach);

        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity != player && !entity.isAlliedTo(player)
                        && !Friends.areFriends(player, entity))) {
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 push = target.position().subtract(player.position());
            if (push.lengthSqr() > 0.0001d) {
                push = push.normalize().scale(0.8d + power);
                target.push(push.x, 0.4d, push.z);
                target.hurtMarked = true;
            }
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.playSound(null, player, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                0.7f, 1.0f + (1.0f - power));
    }
}
