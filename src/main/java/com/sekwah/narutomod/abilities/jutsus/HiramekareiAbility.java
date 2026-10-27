package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.JutsuEffects;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.DropExperienceBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Hiramekarei: the great hammer-blade held over the shoulder and brought down with the whole body.
 *
 * <p>The sword is not a sword in the usual sense. It is a slab of a blade wide enough to be a shield,
 * and what it is for is weight: everything the caster lands while it is up in both hands comes down
 * half again as hard, because the mass behind the blow is the technique. A ninja who can hold it up
 * long enough to matter can also swing it into the ground, and anything standing on stone or ore
 * loses the ground it was standing on.</p>
 *
 * <p>The technique belongs to the sword now: right-clicking a Hiramekarei raises it for ten seconds,
 * and the node on the tree is what teaches the move. There is no combo to remember and no row in
 * the jutsu menu.</p>
 */
public class HiramekareiAbility extends Ability {

    /** A slab of a blade coming down is heard across a courtyard. */
    @Override
    public int noiseRadius() {
        return 14;
    }

    /** No combo: the move lives on the sword's right-click. */
    public static final long COMBO = -1L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "hiramekarei");

    /** The mark the melee hook reads: kept a few ticks ahead for as long as the hammer is up. */
    public static final String UNTIL_TAG = "narutomod:hiramekarei_until";

    /** What the hammer does to a blow: the weight behind it. */
    public static final float DAMAGE_FACTOR = 1.5f;

    /** What raising it costs, and how long the arm can keep it up. */
    private static final float OPEN_COST = 50.0f;
    private static final int HOLD_TICKS = 200;

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
    public boolean handleCost(Player player, INinjaData ninjaData, int chargeAmount) {
        return false;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
    }

    /**
     * Raises the hammer from the sword's right-click. The node on the kenjutsu branch is the gate,
     * and the price is paid once for the whole ten-second hold.
     *
     * @return true when the blade actually went up.
     */
    public static boolean raise(Player player) {
        INinjaData ninjaData = player.getCapability(
                com.sekwah.narutomod.capabilities.NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
        if (ninjaData == null || !AbilityUnlocks.isUnlocked(ninjaData, ID)) {
            return false;
        }
        if (!(player.getMainHandItem().getItem() instanceof SwordItem) || isUp(player)) {
            return false;
        }
        float cost = Mastery.cost(ninjaData, NarutoAbilities.HIRAMEKAREI.get(), OPEN_COST);
        if (ninjaData.getChakra() < cost) {
            return false;
        }
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        player.getPersistentData().putLong(UNTIL_TAG, player.level().getGameTime() + HOLD_TICKS);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if (player.level() instanceof ServerLevel level) {
            Vec3 hand = JutsuEffects.handPosition(player);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, hand.x, hand.y, hand.z,
                    18, 0.3d, 0.4d, 0.3d, 0.02d);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    net.minecraft.sounds.SoundEvents.ANVIL_LAND,
                    net.minecraft.sounds.SoundSource.PLAYERS, 0.8f, 0.6f);
        }
        return true;
    }

    /** @return whether the hammer is up right now, asked by the melee hook. */
    public static boolean isUp(Player player) {
        return player.getPersistentData().getLong(UNTIL_TAG) > player.level().getGameTime();
    }

    /**
     * The hammer brought down into the ground under a target: the block a body is standing on is
     * broken out from beneath it, which is the second use of a blade this heavy.
     */
    public static void crushUnderfoot(Player caster, LivingEntity victim) {
        if (!(caster.level() instanceof ServerLevel level)) {
            return;
        }
        BlockPos pos = victim.getOnPos();
        BlockState state = level.getBlockState(pos);
        if (!breakable(state) || !level.mayInteract(caster, pos)) {
            return;
        }
        level.destroyBlock(pos, true, caster);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.getX() + 0.5d, pos.getY() + 0.5d,
                pos.getZ() + 0.5d, 8, 0.3d, 0.2d, 0.3d, 0.02d);
    }

    /** @return whether the block is stone or ore, the two things a hammer of this size breaks. */
    private static boolean breakable(BlockState state) {
        if (state.isAir() || state.hasBlockEntity()) {
            return false;
        }
        return state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(BlockTags.STONE_ORE_REPLACEABLES)
                || state.getBlock() instanceof DropExperienceBlock;
    }
}
