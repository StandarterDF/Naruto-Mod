package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.entity.projectile.ShurikenEntity;
import com.sekwah.narutomod.events.ShadowShurikenEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;

/**
 * Shadow shuriken: one thrown blade becomes two.
 *
 * <p>The caster never lets go of anything - the copies are chakra shaped like a shuriken, thrown
 * along the same line their hand would have taken. Each one lands for a little less than a real
 * blade, so the technique is not a way to throw twice as hard; it is a way to make a single opening
 * count, because a target cannot see which of the two is worth blocking.</p>
 *
 * <p>The copies follow the crosshair: two real spinning blades leave the caster's hand along the
 * same line, so a target sees steel coming rather than an instant wound. The first blade carries the
 * real trick - wherever it lands, it vanishes and leaves a one-health shadow clone standing in its
 * place, armed with whatever the caster is holding at that moment. The copy fights like any combat
 * clone; it is not a worker and does not run off to a job.</p>
 */
public class ShurikenShadowAbility extends Ability implements Ability.Cooldown {

    /** B A A V - the throwing branch's blend of the clone and weapon seals. */
    public static final long COMBO = 3123L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "shuriken_shadow");

    /**
     * What a thrown shuriken deals, see {@code ShurikenItem}. Kept here rather than taken from the
     * item so the two copies can be tuned without the projectile following them.
     */
    private static final double SHURIKEN_DAMAGE = 2.5d;

    /** A shadow blade is thinner than the steel it copies, hence a fraction of the damage. */
    private static final double DAMAGE_FRACTION = 0.6d;

    /** One blade in the hand, two in the air. */
    private static final int COPIES = 2;

    private static final float COST = 12.0f;
    private static final int COOLDOWN = 80;

    private static final int CHAKRA_REGEN_DELAY = 20;

    @Override
    public int noiseRadius() {
        return 16;
    }

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
        float cost = this.masteryCost(ninjaData, COST);
        if (ninjaData.getChakra() < cost) {
            player.displayClientMessage(Component.translatable("jutsu.fail.notenoughchakra",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW)), true);
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }

        level.playSound(null, player, SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS, 0.9f, 1.2f);

        // The copies are real flying blades, so they use the mod's own shuriken entity and its
        // spinning renderer rather than an instant hit. The second one is thrown a hair to the side
        // so the two are visibly separate in the air.
        for (int i = 0; i < COPIES; i++) {
            ShurikenEntity blade = new ShurikenEntity(level, player);
            // Each blade leaves its own shadow clone behind.
            blade.getPersistentData().putBoolean(ShadowShurikenEvents.CLONE_TAG, true);
            blade.getPersistentData().putString(ShadowShurikenEvents.OWNER_TAG, player.getStringUUID());
            float spread = i == 0 ? -1.2f : 1.2f;
            blade.shootFromRotation(player, player.getXRot(), player.getYRot() + spread,
                    0.0f, 3.0f, 0.6f);
            blade.setBaseDamage(SHURIKEN_DAMAGE * DAMAGE_FRACTION);
            blade.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
            level.addFreshEntity(blade);
        }
    }
}
