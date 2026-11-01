package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.item.NinjaTier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SwordItem;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Kiba, the twin lightning blades: a pair of swords that answer a thunderstorm.
 *
 * <p>While a storm rages, every swing calls a bolt down on the target - extra lightning damage,
 * sparks and thunder - out to sixteen blocks. On a clear day the blades are simply swords. They
 * also keep their wearer quick on their feet: as long as one of them is carried, the ninja moves
 * faster, and the bonus is applied once no matter how many blades are in the pack.</p>
 */
public class KibaItem extends SwordItem {

    /** NBT key holding the kill count of this blade. */
    public static final String KILLS_TAG = "Kills";

    /** Stable id for the passive speed bonus, so it is never stacked. */
    private static final UUID SPEED_UUID =
            UUID.fromString("4b1b2a3c-5d6e-7f80-9a1b-2c3d4e5f6a7b");

    /** How much faster the blades keep their wearer, as a total multiplier. */
    private static final double SPEED_BONUS = 0.15d;

    /** Extra lightning damage dealt to a struck target during a storm. */
    private static final float LIGHTNING_DAMAGE = 6.0f;

    /** How far away a target may be and still be struck by the storm. */
    private static final double STORM_RANGE = 16.0d;

    public KibaItem(Item.Properties properties) {
        super(NinjaTier.LEGENDARY, 4, -2.0f, properties);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        boolean result = super.hurtEnemy(stack, target, attacker);
        Level level = attacker.level();
        if (!level.isClientSide && target.isDeadOrDying()) {
            stack.getOrCreateTag().putInt(KILLS_TAG, getKills(stack) + 1);
        }
        if (attacker.level().isClientSide || !(attacker instanceof Player)) {
            return result;
        }
        if (!level.isThundering()) {
            return result;
        }
        if (attacker.distanceTo(target) > STORM_RANGE) {
            return result;
        }
        int kills = getKills(stack);
        if (level instanceof ServerLevel serverLevel) {
            float damage = lightningDamage(kills);
            target.hurt(level.damageSources().lightningBolt(), damage);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    target.getX(), target.getY() + target.getBbHeight() * 0.5d, target.getZ(),
                    16, 0.4d, 0.4d, 0.4d, 0.05d);
            List<LivingEntity> candidates = level.getEntitiesOfClass(LivingEntity.class,
                    target.getBoundingBox().inflate(8.0d),
                    e -> e.isAlive() && e != target && e != attacker
                            && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                            && !attacker.isAlliedTo(e) && !Friends.areFriends(attacker, e));
            candidates.sort(Comparator.comparingDouble(e -> target.distanceTo(e)));
            int need = maxTargets(kills) - 1;
            int taken = 0;
            for (LivingEntity extra : candidates) {
                if (taken >= need) {
                    break;
                }
                extra.hurt(level.damageSources().lightningBolt(), damage);
                serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        extra.getX(), extra.getY() + extra.getBbHeight() * 0.5d, extra.getZ(),
                        16, 0.4d, 0.4d, 0.4d, 0.05d);
                taken++;
            }
            serverLevel.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.WEATHER, 0.6f, 1.5f);
        }
        return result;
    }

    /** @return how many kills this blade has taken. */
    public static int getKills(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt(KILLS_TAG);
    }

    private static int maxTargets(int kills) {
        return 3 + Math.min(4, kills / 1000);
    }

    private static float lightningDamage(int kills) {
        return 6.0f + 4.0f * Math.min(1.0f, kills / 3000.0f);
    }

    /** @return whether this player is carrying at least one Kiba blade. */
    public static boolean hasKiba(Player player) {
        if (player.getMainHandItem().getItem() instanceof KibaItem) {
            return true;
        }
        if (player.getOffhandItem().getItem() instanceof KibaItem) {
            return true;
        }
        for (ItemStack stack : player.getInventory().items) {
            if (stack.getItem() instanceof KibaItem) {
                return true;
            }
        }
        return false;
    }

    /** Per-tick maintenance: keeps the passive speed bonus in line with what is carried. */
    public static void tick(Player player) {
        if (player.level().isClientSide) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        applyPassive(player);
    }

    private static void applyPassive(Player player) {
        AttributeInstance speed = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            return;
        }
        boolean has = hasKiba(player);
        AttributeModifier existing = speed.getModifier(SPEED_UUID);
        if (!has) {
            if (existing != null) {
                speed.removeModifier(SPEED_UUID);
            }
            return;
        }
        if (existing == null) {
            speed.addTransientModifier(new AttributeModifier(SPEED_UUID,
                    "narutomod:kiba_speed", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        int kills = getKills(stack);
        tooltip.add(Component.translatable("item.narutomod.kiba.storm")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.kiba.speed")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.narutomod.kiba.kills", kills)
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.narutomod.kiba.targets", maxTargets(kills))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("item.narutomod.kiba.damage", Math.round(lightningDamage(kills)))
                .withStyle(ChatFormatting.YELLOW));
    }
}
