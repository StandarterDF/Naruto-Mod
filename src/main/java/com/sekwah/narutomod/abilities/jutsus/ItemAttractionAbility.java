package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The Rinnegan's pull turned on loose things rather than on bodies.
 *
 * <p>The same hand that drags a person off their feet also gathers what a fight leaves on the
 * ground: everything within twelve blocks comes to the caster in one sweep, and the delay that
 * normally keeps a fresh drop from being picked up is dropped with it. That delay is the whole
 * point - without it a caster would pull a pile to their feet and still have to wait for the items
 * to become theirs.</p>
 *
 * <p>It moves only items. A body is the work of {@link UniversalForceAbility}; a thrown item is a
 * smaller, cheaper application of the same attraction, which is why it shares the eye and not the
 * cooldown.</p>
 */
public class ItemAttractionAbility extends Ability implements Ability.Cooldown {

    /** A pull that gathers a field is heard a short way off. */
    @Override
    public int noiseRadius() {
        return 10;
    }

    /** C V V C V V - the Rinnegan (C V V C) and two more keys. */
    public static final long COMBO = 233322L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "item_attraction");

    /** How far around the caster loose items are gathered. */
    private static final double RANGE = 12.0d;

    /** The impulse every picked item leaves with, in blocks per tick. */
    private static final double PULL_SPEED = 0.35d;

    /** How many motes are drawn along each item's path, and how far apart they sit. */
    private static final int TRAIL_POINTS = 8;

    /** What it costs and how long before the hand can sweep the ground again. */
    private static final float COST = 15.0f;
    private static final int COOLDOWN = 60;

    @Override
    public ActivationType activationType() {
        return ActivationType.INSTANT;
    }

    @Override
    public long defaultCombo() {
        return COMBO;
    }

    @Override
    public int getCooldown() {
        return COOLDOWN;
    }

    @Override
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (nearbyItems(player).isEmpty()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.item_attraction.nothing",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, 20);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 hand = JutsuEffects.handPosition(player);
        for (ItemEntity item : nearbyItems(player)) {
            Vec3 towards = hand.subtract(item.position());
            if (towards.lengthSqr() < 0.01d) {
                continue;
            }
            item.setDeltaMovement(towards.normalize().scale(PULL_SPEED));
            item.setNoPickUpDelay();
            item.hurtMarked = true;
            // The item is travelling to the hand by the technique's will, not falling towards it.
            item.fallDistance = 0.0f;
            drawTrail(level, item.position(), hand);
        }
        level.sendParticles(ParticleTypes.END_ROD, player.getX(),
                player.getY() + player.getBbHeight() * 0.6d, player.getZ(),
                10, 0.4d, 0.4d, 0.4d, 0.02d);
    }

    /** Every loose item close enough to be gathered, with the caster's own drops included. */
    private static List<ItemEntity> nearbyItems(Player player) {
        return player.level().getEntitiesOfClass(ItemEntity.class,
                player.getBoundingBox().inflate(RANGE),
                item -> item.isAlive() && !item.getItem().isEmpty()
                        && item.distanceToSqr(player) <= RANGE * RANGE);
    }

    /** A thin line of light from the item to the hand it is travelling towards. */
    private static void drawTrail(ServerLevel level, Vec3 from, Vec3 to) {
        Vec3 span = to.subtract(from);
        for (int i = 1; i <= TRAIL_POINTS; i++) {
            Vec3 at = from.add(span.scale(i / (double) (TRAIL_POINTS + 1)));
            level.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z,
                    1, 0.02d, 0.02d, 0.02d, 0.0d);
        }
    }
}
