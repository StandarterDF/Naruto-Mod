package com.sekwah.narutomod.item;

import com.sekwah.narutomod.entity.projectile.SmokeBombEntity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * A throwable smoke bomb: the item form of the smoke screen, for ninja who cannot cast it or do not
 * want to spend the chakra.
 *
 * <p>All of the smoke logic lives in {@link SmokeBombEntity} and {@code SmokeCloudEntity}; this is
 * only the throw, so the consumable and the technique cannot drift apart.</p>
 */
public class SmokeBombItem extends Item {

    public SmokeBombItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW,
                SoundSource.PLAYERS, 0.6f, 0.9f);
        if (!level.isClientSide) {
            SmokeBombEntity bomb = new SmokeBombEntity(level, player);
            bomb.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0f, 1.1f, 0.7f);
            level.addFreshEntity(bomb);
        }
        player.getCooldowns().addCooldown(this, 20);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }
}
