package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.phys.Vec3;

/**
 * The edge drawn back into shape between fights.
 *
 * <p>A blade that has been carried through a campaign is a blade with a chipped edge, and a ninja
 * who only ever takes a grindstone out of the pouch cannot repair the one thing they rely on
 * without a bench and an anvil. This is the field answer: chakra run along the steel, which pulls
 * the nicks closed and leaves a quarter of the blade's life in the metal again.</p>
 *
 * <p>It is deliberately slow to come back - ten seconds of work on a weapon worth carrying - and it
 * refuses quietly: a caster with nothing damageable in hand is turned away before any stamina is
 * spent, so a wasted press never costs the bar a fight's worth of regen delay.</p>
 */
public class ChakraWhetstoneAbility extends Ability implements Ability.Cooldown {

    /** Maintenance, not a technique anyone hears across a courtyard. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** 1 3 2 2 2 1 - the kenjutsu seals and the two keys of the whetstone. */
    public static final long COMBO = 132221L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "chakra_whetstone");

    /** What the pass over the edge costs, and how long the arm needs before another. */
    private static final float COST = 20.0f;
    private static final int STAMINA_REGEN_DELAY = 10;
    private static final int COOLDOWN = 600;

    /** How much of the blade's life one pass puts back, as a share of its full durability. */
    private static final float REPAIR_SHARE = 0.25f;

    private static final int REPAIR_PARTICLES = 24;

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
        ItemStack held = player.getMainHandItem();
        if (!(held.getItem() instanceof SwordItem)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chakra_whetstone.sword",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        // Nothing damageable, or nothing to mend: the pass would be paid for and do nothing, so it
        // is refused here rather than after the stamina is gone.
        if (!held.isDamageableItem() || held.getDamageValue() <= 0) {
            player.displayClientMessage(Component.translatable("jutsu.fail.chakra_whetstone.whole",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getStamina() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughstamina",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useStamina(cost, STAMINA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!held.isDamageableItem() || held.getDamageValue() <= 0) {
            return;
        }
        int mended = Math.round(held.getMaxDamage() * REPAIR_SHARE);
        // The damage value is the wear on the blade, so a repair subtracts from it and never goes
        // below an untouched edge.
        held.setDamageValue(Math.max(0, held.getDamageValue() - mended));

        Vec3 hand = JutsuEffects.handPosition(player);
        level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z,
                REPAIR_PARTICLES, 0.3d, 0.2d, 0.3d, 0.4d);
        level.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.PLAYERS, 0.7f, 1.6f);
    }
}
