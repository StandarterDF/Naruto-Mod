package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.item.ComboSwordItem;
import com.sekwah.narutomod.item.NinjaTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.Level.ExplosionInteraction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Shibuki: the blast sword, an endless scroll of explosive tags on a handle.
 *
 * <p>Every swing detonates a controlled explosion at the point of impact without breaking blocks.
 * Right-click slams the ground with a ring of explosions on an eight-second cooldown; both the
 * number of blasts and their power grow with every kill the blade takes.</p>
 */
public class ShibukiItem extends ComboSwordItem {

    /** NBT key holding the kill count of this blade. */
    public static final String KILLS_TAG = "Kills";

    /** Controlled explosion power on a melee hit. */
    private static final float HIT_POWER = 2.0f;

    /** Cooldown of the ground slam, in ticks (8 seconds). */
    private static final int SLAM_COOLDOWN_TICKS = 160;

    /** Radius of the ground-slam ring and its damage reach. */
    private static final double SLAM_RADIUS = 7.0d;

    public ShibukiItem(Tier tier, int attackDamage, float attackSpeed, net.minecraft.world.item.Item.Properties properties,
                       float damagePerStep, int maxCombo, int comboWindow, int kenjutsuPointsRequired) {
        super(tier, attackDamage, attackSpeed, properties, damagePerStep, maxCombo, comboWindow, 0.0f,
                kenjutsuPointsRequired);
    }

    public static int getKills(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KILLS_TAG);
    }

    public static void addKill(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(KILLS_TAG, getKills(stack) + 1);
    }

    /** Number of blasts in the ground slam. */
    public static int getBlastCount(int kills) {
        return Math.min(10, 3 + kills / 5);
    }

    /** Power of each blast in the ground slam. */
    public static float getBlastPower(int kills) {
        return Math.min(20.0f, 6.0f + kills * 0.2f);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        if (!attacker.level().isClientSide && attacker instanceof Player player) {
            explodeOnHit(player, target);
        }
        return result;
    }

    private static void explodeOnHit(Player player, LivingEntity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // Keep the owner and their allies out of the blast.
        List<LivingEntity> protectedEntities = new ArrayList<>();
        player.setInvulnerable(true);
        protectedEntities.add(player);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(8.0d))) {
            if (entity == player || entity == target) {
                continue;
            }
            if (isProtected(player, entity)) {
                entity.setInvulnerable(true);
                protectedEntities.add(entity);
            }
        }

        player.setLastHurtMob(target);
        Vec3 pos = target.position();
        level.explode(player, pos.x, pos.y, pos.z, HIT_POWER, ExplosionInteraction.NONE);

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + target.getBbHeight() * 0.5d, pos.z,
                1, 0.0d, 0.0d, 0.0d, 0.0d);
        level.sendParticles(ParticleTypes.EXPLOSION, pos.x, pos.y + target.getBbHeight() * 0.5d, pos.z,
                3, 0.4d, 0.4d, 0.4d, 0.0d);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS,
                1.0f, 1.2f);

        for (LivingEntity entity : protectedEntities) {
            entity.setInvulnerable(false);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) {
            return super.use(level, player, hand);
        }
        ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);
        if (offHand.getItem() instanceof ShieldItem || stack.getItem() instanceof ShieldItem) {
            return InteractionResultHolder.pass(stack);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.consume(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }
        slam(player, serverLevel, stack);
        player.getCooldowns().addCooldown(this, SLAM_COOLDOWN_TICKS);
        player.swing(hand);
        return InteractionResultHolder.consume(stack);
    }

    private static void slam(Player player, ServerLevel level, ItemStack stack) {
        int kills = getKills(stack);
        int count = getBlastCount(kills);
        float power = getBlastPower(kills);

        // Silence every blast's damage first; we re-apply it only to valid enemies below.
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(12.0d));
        for (LivingEntity entity : nearby) {
            entity.setInvulnerable(true);
        }

        double baseY = player.getY();
        for (int i = 0; i < count; i++) {
            double angle = 2.0d * Math.PI * i / Math.max(1, count);
            double x = player.getX() + Math.cos(angle) * SLAM_RADIUS;
            double z = player.getZ() + Math.sin(angle) * SLAM_RADIUS;
            level.explode(player, x, baseY, z, power, ExplosionInteraction.NONE);
        }

        // Ring of particles.
        for (int i = 0; i < 36; i++) {
            double angle = 2.0d * Math.PI * i / 36.0d;
            double x = player.getX() + Math.cos(angle) * SLAM_RADIUS;
            double z = player.getZ() + Math.sin(angle) * SLAM_RADIUS;
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, x, baseY + 0.5d, z,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
            level.sendParticles(ParticleTypes.EXPLOSION, x, baseY + 0.5d, z,
                    1, 0.2d, 0.1d, 0.2d, 0.0d);
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 0.8f);

        for (LivingEntity entity : nearby) {
            entity.setInvulnerable(false);
        }

        // Re-apply the damage only to valid enemies within reach, recording the kill source.
        for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(SLAM_RADIUS))) {
            if (!isValidEnemy(player, target)) {
                continue;
            }
            if (target.position().distanceTo(player.position()) > SLAM_RADIUS) {
                continue;
            }
            player.setLastHurtMob(target);
            target.hurt(player.damageSources().playerAttack(player), power);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int kills = getKills(stack);
        tooltip.add(Component.translatable("item.narutomod.shibuki.kills", kills)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.narutomod.shibuki.power", Math.round(getBlastPower(kills)))
                .withStyle(ChatFormatting.RED));
    }

    private static boolean isProtected(Player player, LivingEntity target) {
        if (target == player) {
            return true;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return true;
        }
        if (target.isAlliedTo(player) || Friends.areFriends(player, target)) {
            return true;
        }
        return target instanceof ShadowCloneEntity clone && clone.isOwnedBy(player);
    }

    private static boolean isValidEnemy(Player player, LivingEntity target) {
        if (target == player || target.isRemoved() || !target.isAlive() || target.isSpectator()) {
            return false;
        }
        if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
            return false;
        }
        if (target.isAlliedTo(player) || Friends.areFriends(player, target)) {
            return false;
        }
        if (target instanceof ShadowCloneEntity clone && clone.isOwnedBy(player)) {
            return false;
        }
        return true;
    }
}
