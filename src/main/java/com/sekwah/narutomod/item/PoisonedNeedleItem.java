package com.sekwah.narutomod.item;

import com.sekwah.narutomod.util.Targeting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobType;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A senbon dipped in something unpleasant, for ninja who would rather not spend the chakra.
 *
 * <p>It is thrown at whatever the caster is looking at within a short reach and leaves a little
 * poison behind. The poison is a shinobi's, not a spider's: it does nothing to the dead, who have no
 * blood left to spoil, nor to spiders, whose own venom it is. Everything else gets a few seconds of
 * it, which is enough to make a fleeing target stumble.</p>
 *
 * <p>Like every thrown item in the mod the needle is spent whether or not it finds a body, and the
 * short cooldown keeps a stack of them from being emptied in a single breath.</p>
 */
public class PoisonedNeedleItem extends Item {

    /** How far a needle can be placed, a little past arm's reach. */
    private static final double RANGE = 8.0d;

    /** About a senbon's worth of damage, so the poison is the point rather than the prick. */
    private static final float DAMAGE = 3.0f;

    private static final int POISON_TICKS = 100;

    private static final int COOLDOWN = 20;

    public PoisonedNeedleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5f, 1.4f);

        if (level instanceof ServerLevel serverLevel) {
            Entity looked = Targeting.lookedAtEntity(player, RANGE);
            if (looked instanceof LivingEntity target && target != player) {
                strike(serverLevel, player, target);
            }
        }

        player.getCooldowns().addCooldown(this, COOLDOWN);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Lands the needle: the wound, the poison where it will take, and the splash of it. */
    private static void strike(ServerLevel level, Player player, LivingEntity target) {
        target.hurt(player.damageSources().thrown(player, player), DAMAGE);
        if (target.getMobType() != MobType.UNDEAD && !(target instanceof Spider)) {
            target.addEffect(new MobEffectInstance(MobEffects.POISON, POISON_TICKS, 0));
        }
        level.sendParticles(ParticleTypes.ITEM_SLIME, target.getX(), target.getY(0.5d), target.getZ(),
                8, 0.25d, 0.35d, 0.25d, 0.02d);
    }
}
