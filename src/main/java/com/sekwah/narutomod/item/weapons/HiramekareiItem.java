package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.friends.Friends;
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
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Hiramekarei: a two-handed paired blade that stores chakra in its edge.
 *
 * <p>Every successful hit adds chakra to the blade. Right-click releases it as a wide light-hammer
 * blast. Blocking with the blade absorbs most of an incoming blow and feeds the absorbed chakra
 * back into the store. The blade remembers every kill it takes, and its capacity grows with them.</p>
 */
public class HiramekareiItem extends SwordItem {

    /** Chakra stored in the blade. */
    public static final String CHAKRA_TAG = "Chakra";

    /** Kills taken by this blade. */
    public static final String KILLS_TAG = "Kills";

    /** Chakra gained per successful hit. */
    public static final float CHARGE_PER_HIT = 10.0f;

    /** Base storage capacity. */
    public static final float MAX_CHAKRA_BASE = 200.0f;

    /** Extra capacity per kill. */
    public static final float MAX_CHAKRA_PER_KILL = 10.0f;

    /** Players currently releasing the light hammer, so its hits do not recharge the blade. */
    private static final Set<UUID> RELEASING = new HashSet<>();

    public static boolean isReleasing(Player player) {
        return RELEASING.contains(player.getUUID());
    }

    public HiramekareiItem(Tier tier, int attackDamage, float attackSpeed, Properties properties) {
        super(tier, attackDamage, attackSpeed, properties);
    }

    public static float getChakra(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0.0f : tag.getFloat(CHAKRA_TAG);
    }

    public static void setChakra(ItemStack stack, float value) {
        float max = getMaxChakra(stack);
        float clamped = Math.min(Math.max(value, 0.0f), max);
        stack.getOrCreateTag().putFloat(CHAKRA_TAG, clamped);
    }

    public static int getKills(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KILLS_TAG);
    }

    public static void addKill(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        tag.putInt(KILLS_TAG, getKills(stack) + 1);
    }

    public static int getMaxChakra(ItemStack stack) {
        return (int) (MAX_CHAKRA_BASE + MAX_CHAKRA_PER_KILL * getKills(stack));
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BLOCK;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                releaseHammer(player, stack);
            }
            return InteractionResultHolder.success(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    private static void releaseHammer(Player player, ItemStack stack) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        float stored = getChakra(stack);
        if (stored <= 0.0f) {
            player.displayClientMessage(Component.translatable("jutsu.fail.hiramekarei.empty"), true);
            return;
        }
        float ratio = stored / Math.max(1.0f, getMaxChakra(stack));
        float damage = 4.0f + 26.0f * ratio;
        double radius = 6.0 + 4.0 * ratio;
        float factor = 0.6f + 0.4f * ratio;

        // Spend the whole charge.
        setChakra(stack, 0.0f);
        RELEASING.add(player.getUUID());

        // Damage and knock back every enemy in range.
        Vec3 centre = player.position();
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(radius),
                e -> e.isAlive() && e != player);
        for (LivingEntity target : targets) {
            if (target instanceof Player other && (other.isCreative() || other.isSpectator())) {
                continue;
            }
            if (Friends.isOwnSide(player, target)) {
                continue;
            }
            if (target instanceof com.sekwah.narutomod.entity.ShadowCloneEntity clone
                    && clone.isOwnedBy(player)) {
                continue;
            }
            double dist = target.position().distanceTo(centre);
            if (dist > radius) {
                continue;
            }
            target.hurt(player.damageSources().playerAttack(player), damage);
            Vec3 away = target.position().subtract(centre);
            if (away.lengthSqr() < 0.01d) {
                away = player.getLookAngle().reverse();
            }
            away = new Vec3(away.x, 0.0d, away.z).normalize().scale(1.2d * factor);
            target.push(away.x, 0.4d * factor, away.z);
            target.hurtMarked = true;
            target.fallDistance = 0.0f;
        }

        // Particles: light hammer and the wind-divergence ring.
        int ringCount = Math.max(1, (int) (36 * factor));
        for (int i = 0; i < ringCount; i++) {
            double angle = i * Math.PI * 2.0d / ringCount;
            double x = centre.x + Math.cos(angle) * radius;
            double z = centre.z + Math.sin(angle) * radius;
            level.sendParticles(ParticleTypes.END_ROD, x, centre.y + 1.0d, z,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, centre.y + 1.0d, z,
                    1, 0.0d, 0.0d, 0.0d, 0.0d);
            level.sendParticles(ParticleTypes.CLOUD, x, centre.y + 0.5d, z,
                    Math.max(1, (int) (2 * factor)), 0.2d, 0.1d, 0.2d, 0.02d);
        }
        // Radial spokes from the centre out to the ring.
        int spokeCount = Math.max(1, (int) (12 * factor));
        int spokeSteps = Math.max(1, (int) (9 * factor));
        for (int i = 0; i < spokeCount; i++) {
            double angle = i * Math.PI * 2.0d / spokeCount;
            for (int r = 1; r <= spokeSteps; r++) {
                double rad = r;
                double x = centre.x + Math.cos(angle) * rad;
                double z = centre.z + Math.sin(angle) * rad;
                level.sendParticles(ParticleTypes.CLOUD, x, centre.y + 0.5d, z,
                        1, 0.05d, 0.05d, 0.05d, 0.0d);
            }
        }
        // Centre burst.
        level.sendParticles(ParticleTypes.END_ROD, centre.x, centre.y + 1.0d, centre.z,
                Math.max(1, (int) (24 * factor)), 0.6d, 0.8d, 0.6d, 0.05d);
        level.sendParticles(ParticleTypes.SWEEP_ATTACK, centre.x, centre.y + 1.0d, centre.z,
                Math.max(1, (int) (6 * factor)), 0.8d, 0.4d, 0.8d, 0.0d);
        level.sendParticles(ParticleTypes.CLOUD, centre.x, centre.y + 0.5d, centre.z,
                Math.max(1, (int) (20 * factor)), 0.8d, 0.3d, 0.8d, 0.03d);

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.0f, 1.2f);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0f, 0.8f);

        RELEASING.remove(player.getUUID());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.hiramekarei.charge",
                (int) getChakra(stack), getMaxChakra(stack))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.narutomod.hiramekarei.kills",
                getKills(stack)).withStyle(ChatFormatting.GOLD));
        float stored = getChakra(stack);
        float ratio = stored / Math.max(1.0f, getMaxChakra(stack));
        float damage = 4.0f + 26.0f * ratio;
        double radius = 6.0 + 4.0 * ratio;
        tooltip.add(Component.translatable("item.narutomod.hiramekarei.release",
                (int) damage, (int) radius).withStyle(ChatFormatting.LIGHT_PURPLE));
    }
}