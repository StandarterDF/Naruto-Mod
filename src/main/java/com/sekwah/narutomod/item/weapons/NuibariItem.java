package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.item.NarutoItems;
import com.sekwah.narutomod.item.NinjaTier;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Nuibari: a thin needle of a blade that threads its victims together. A swing pierces everyone
 * standing on the line of sight, marks them with a thread that bleeds for a short while, and the
 * right click fires a reinforced needle without spending the blade or a cooldown.
 */
public class NuibariItem extends SwordItem {

    /** NBT key holding the kill count of the blade. */
    public static final String KILLS_TAG = "narutomod:kills";

    /** Persistent data key marking a threaded victim, game time the thread snaps. */
    public static final String THREAD_TAG = "narutomod:nuibari_thread_until";

    /** How long a thread holds, in ticks. */
    public static final int THREAD_DURATION = 100;

    /** Bleed damage dealt by a thread every 20 ticks. */
    public static final float BLEED_DAMAGE = 2.0f;

    /** Base needle damage on right click. */
    public static final float NEEDLE_BASE_DAMAGE = 6.0f;

    /** Bonus needle damage per kill. */
    public static final float NEEDLE_DAMAGE_PER_KILL = 0.5f;

    /** Reach of both the swing and the needle. */
    public static final double RANGE = 16.0d;

    public NuibariItem(Properties properties) {
        super(NinjaTier.LEGENDARY, 4, -1.8f, properties);
    }

    /** @return how many kills this blade has taken. */
    public static int getKills(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KILLS_TAG);
    }

    /** Records one more kill on the blade. */
    public static void addKill(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(KILLS_TAG, getKills(stack) + 1);
    }

    /** Damage a needle currently deals. */
    public float needleDamage(ItemStack stack) {
        return NEEDLE_BASE_DAMAGE + NEEDLE_DAMAGE_PER_KILL * getKills(stack);
    }

    /**
     * Piercing swing: every valid enemy on the line of sight up to {@link #RANGE} blocks is cut,
     * marked with a thread and bleeds. Friends and the caster's own side are skipped.
     */
    public static void performAttack(Player player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof NuibariItem)) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);

        BlockHitResult wall = player.level().clip(new ClipContext(eye, eye.add(look.scale(RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = RANGE;
        if (wall.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            reach = Math.min(RANGE, wall.getLocation().distanceTo(eye));
        }

        AABB search = new AABB(eye, eye.add(look.scale(reach))).inflate(1.5d);
        List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.isRemoved());

        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity candidate : candidates) {
            if (Friends.isOwnSide(player, candidate)) {
                continue;
            }
            if (candidate instanceof Player other && (other.isCreative() || other.isSpectator())) {
                continue;
            }
            Vec3 centre = candidate.position().add(0.0d, candidate.getBbHeight() * 0.5d, 0.0d);
            Vec3 to = centre.subtract(eye);
            double along = to.dot(look);
            if (along <= 0.0d || along > reach) {
                continue;
            }
            if (to.subtract(look.scale(along)).length() > 1.25d) {
                continue;
            }
            targets.add(candidate);
        }
        targets.sort(Comparator.comparingDouble(e -> e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye).dot(look)));

        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE);
        long now = level.getGameTime();
        for (LivingEntity target : targets) {
            boolean willKill = target.getHealth() <= damage;
            target.hurt(player.damageSources().playerAttack(player), damage);
            target.getPersistentData().putLong(THREAD_TAG, now + THREAD_DURATION);
            if (willKill || !target.isAlive()) {
                addKill(stack);
            }
        }

        // END_ROD trail along the pierced line.
        for (double d = 0.0d; d <= reach; d += 0.5d) {
            Vec3 p = eye.add(look.scale(d));
            level.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }

    /**
     * Right click fires an enhanced needle along the gaze: no item cost, no cooldown.
     */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(stack);
        }

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0F);

        BlockHitResult wall = level.clip(new ClipContext(eye, eye.add(look.scale(RANGE)),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double reach = RANGE;
        if (wall.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            reach = Math.min(RANGE, wall.getLocation().distanceTo(eye));
        }

        AABB search = new AABB(eye, eye.add(look.scale(reach))).inflate(1.0d);
        LivingEntity target = null;
        double nearestAlong = Double.MAX_VALUE;
        for (LivingEntity candidate : serverLevel.getEntitiesOfClass(LivingEntity.class, search,
                e -> e != player && e.isAlive() && !e.isSpectator() && !e.isRemoved())) {
            if (Friends.isOwnSide(player, candidate)) {
                continue;
            }
            if (candidate instanceof Player other && (other.isCreative() || other.isSpectator())) {
                continue;
            }
            Vec3 centre = candidate.position().add(0.0d, candidate.getBbHeight() * 0.5d, 0.0d);
            Vec3 to = centre.subtract(eye);
            double along = to.dot(look);
            if (along <= 0.0d || along > reach) {
                continue;
            }
            if (to.subtract(look.scale(along)).length() > 0.75d) {
                continue;
            }
            if (along < nearestAlong) {
                nearestAlong = along;
                target = candidate;
            }
        }

        for (double d = 0.0d; d <= reach; d += 0.25d) {
            Vec3 p = eye.add(look.scale(d));
            serverLevel.sendParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.02d, 0.02d, 0.02d, 0.0d);
        }

        if (target != null) {
            float damage = needleDamage(stack);
            target.hurt(player.damageSources().playerAttack(player), damage);
        }

        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int kills = getKills(stack);
        tooltip.add(Component.translatable("item.narutomod.nuibari.kills",
                Component.literal(String.valueOf(kills)).withStyle(ChatFormatting.GOLD),
                Component.literal(String.valueOf(Math.round(kills * NEEDLE_DAMAGE_PER_KILL * 10.0f) / 10.0f)).withStyle(ChatFormatting.RED))
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.nuibari.needle",
                Component.literal(String.valueOf(Math.round(needleDamage(stack) * 10.0f) / 10.0f)).withStyle(ChatFormatting.YELLOW))
                .withStyle(ChatFormatting.GRAY));
    }
}
