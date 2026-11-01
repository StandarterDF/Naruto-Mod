package com.sekwah.narutomod.item.weapons;

import com.sekwah.narutomod.entity.projectile.MinatoKunaiEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class MinatoKunaiItem extends Item {

    public MinatoKunaiItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (player.isShiftKeyDown()) {
            if (!level.isClientSide) {
                MinatoKunaiEntity target = findActive(level, player);
                if (target != null && target.isStuckInGround()) {
                    double x = target.getX();
                    double y = target.getY() + 1.0D;
                    double z = target.getZ();
                    player.teleportTo(x, y, z);
                    player.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    player.fallDistance = 0.0F;
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                                x, y, z, 32, 0.5D, 1.0D, 0.5D, 0.1D);
                        serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL,
                                x, y, z, 32, 0.5D, 1.0D, 0.5D, 0.1D);
                    }
                    level.playSound(null, x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0F, 1.0F);
                    player.getCooldowns().addCooldown(this, 10);
                } else {
                    player.displayClientMessage(Component.translatable("jutsu.minato_kunai.no_target").withStyle(ChatFormatting.RED), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }

        // Throw
        if (!level.isClientSide) {
            // Remove any previously active kunai thrown by this player.
            AABB box = new AABB(-3.0E7D, -64.0D, -3.0E7D, 3.0E7D, 320.0D, 3.0E7D);
            for (MinatoKunaiEntity old : level.getEntitiesOfClass(MinatoKunaiEntity.class, box)) {
                if (old.getOwner() == player) {
                    old.discard();
                }
            }

            MinatoKunaiEntity kunai = new MinatoKunaiEntity(level, player);
            kunai.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 3.0F, 1.0F);
            int rolled = 7 + level.random.nextInt(3); // 7..9
            // AbstractArrow multiplies baseDamage by projectile velocity when computing hit damage,
            // so divide by the throw velocity (3.0) to keep actual damage in the 7..9 range.
            kunai.setBaseDamage(rolled / 3.0D);
            // The item stays in the caster's hand, so a pickable projectile would be a free duplicate.
            kunai.pickup = net.minecraft.world.entity.projectile.AbstractArrow.Pickup.DISALLOWED;
            level.addFreshEntity(kunai);
        }

        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 1.0F,
                1.0F / (level.random.nextFloat() * 0.4F + 1.2F) + 0.5F);
        player.getCooldowns().addCooldown(this, 20);

        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static MinatoKunaiEntity findActive(Level level, Player player) {
        AABB box = new AABB(-3.0E7D, -64.0D, -3.0E7D, 3.0E7D, 320.0D, 3.0E7D);
        MinatoKunaiEntity found = null;
        for (MinatoKunaiEntity e : level.getEntitiesOfClass(MinatoKunaiEntity.class, box)) {
            if (e.getOwner() == player) {
                found = e;
                break;
            }
        }
        return found;
    }

    @Override
    public void appendHoverText(ItemStack stack, Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.narutomod.minato_kunai.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
