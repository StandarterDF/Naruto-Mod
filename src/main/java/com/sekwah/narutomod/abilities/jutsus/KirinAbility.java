package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.util.ElementalStates;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import java.util.List;

/**
 * Kirin: the sky itself answers the caster, but only in a thunderstorm.
 *
 * <p>This is deliberately not a bolt shape drawn by the mod - a real {@link LightningBolt} is put
 * down on each enemy within range, so vanilla does everything a lightning strike does: it burns, it
 * turns a nearby creeper charged, it pulses a lightning rod (see {@code events/LightningEvents}) and
 * it hurts whatever is standing there. The technique only decides which enemies are struck and how
 * it treats soaked targets.</p>
 *
 * <p>Water conducts. A wet creature struck by the bolt takes extra damage. That makes the water
 * breath and this technique a real combination rather than two unrelated elements: soak a crowd,
 * then call the storm.</p>
 *
 * <p>The strike is an area-of-effect attack: one bolt is called down on every enemy within 30 blocks
 * of the caster, with soaked targets taking an additional bonus.</p>
 */
public class KirinAbility extends Ability implements Ability.Cooldown {

    /** B B V V. */
    public static final long COMBO = 111213L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "kirin");

    /** Chakra paid for one strike. */
    private static final float COST = 80.0f;

    /** Cooldown in ticks: half a minute. Mastery shortens it like any other technique. */
    private static final int COOLDOWN = 20 * 30;

    /** Radius of the area-of-effect: one bolt per enemy within this distance of the caster. */
    private static final double AOE_RADIUS = 30.0d;

    /** Extra damage a soaked target takes on top of the vanilla bolt. */
    private static final float WET_BONUS_DAMAGE = 8.0f;

    private static final int CHAKRA_REGEN_DELAY = 20;

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
        if (!player.level().isThundering()) {
            player.displayClientMessage(Component.translatable("jutsu.fail.kirin.nostorm",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level) || !level.isThundering()) {
            return;
        }
        Player caster = player;
        float mastery = Mastery.level(ninjaData, this);
        float damage = 30.0f + 70.0f * mastery;
        List<LivingEntity> enemies = level.getEntitiesOfClass(LivingEntity.class,
                new net.minecraft.world.phys.AABB(caster.position(), caster.position()).inflate(AOE_RADIUS),
                target -> target.isAlive() && target != player && target != caster
                        && !caster.isAlliedTo(target) && !Friends.areFriends(caster, target)
                        && !target.isSpectator()
                        && !(target instanceof Player p && p.isCreative()));
        for (LivingEntity target : enemies) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) {
                continue;
            }
            bolt.moveTo(target.getX(), target.getY(), target.getZ());
            bolt.setCause(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    ? serverPlayer : null);
            level.addFreshEntity(bolt);
            target.hurt(level.damageSources().lightningBolt(), damage);
            if (ElementalStates.isWet(target)) {
                target.hurt(level.damageSources().lightningBolt(), WET_BONUS_DAMAGE);
            }
        }
    }
}
