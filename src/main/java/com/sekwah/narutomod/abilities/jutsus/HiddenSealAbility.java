package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hidden Seal: a Byakugan trap that is not there until it is stepped on.
 *
 * <p>The eye sees chakra, and a Hyuga who can see it can also leave a little of their own where it
 * will not be noticed: a seal pressed flat against a wall or a floor, invisible to everything except
 * another Byakugan. It is the ambush half of the gentle fist - the caster does not have to be in the
 * room when the fight starts, they only have to have been there first.</p>
 *
 * <p>The seal is a marker armour stand rather than a block or a custom entity: it must not be
 * minable, must not block a corridor, must not be pushed and must not collide with anything, which
 * is exactly what an invisible marker stand is. It carries {@link #SEAL_TAG}, {@link #UNTIL_TAG} and
 * {@link #OWNER_TAG} so that the parent's trigger hook can find it and know whose it is, and so a
 * seal left in an unloaded room can be swept up after its time.</p>
 */
public class HiddenSealAbility extends Ability implements Ability.Cooldown {

    /** Pressing a seal onto stone is silent. */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** V B V B V V - the eye's seal with the last key doubled, as a seal is pressed twice. */
    public static final long COMBO = 12313L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "hidden_seal");

    /** Set on every seal entity, with the tick it expires at and the caster it belongs to. */
    public static final String SEAL_TAG = "narutomod:hidden_seal";
    public static final String UNTIL_TAG = "narutomod:hidden_seal_until";
    public static final String OWNER_TAG = "narutomod:hidden_seal_owner";

    /** Chakra paid to press the seal on, and how long before another can be laid. */
    private static final float COST = 30.0f;
    private static final int COOLDOWN = 400;

    private static final int CHAKRA_REGEN_DELAY = 20;

    /** How far the caster can reach to press the seal on something. */
    private static final double RANGE = 6.0d;

    /** How long a seal waits for a body: one minute. */
    private static final int LIFETIME = 1200;

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
        if (!NarutoConfig.byakuganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!ByakuganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.byakugan.required",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            player.displayClientMessage(Component.translatable("jutsu.fail.hidden_seal.notarget",
                    Component.translatable(this.getTranslationKey(ninjaData)).withStyle(ChatFormatting.YELLOW))
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        BlockPos spot = spotOf(hit);
        if (!isFree(player, spot)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.hidden_seal.nospace",
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // The look is rebuilt here rather than carried over from the cost check, so what is pressed
        // onto the world is where the caster is aiming now and not where they aimed a packet ago.
        BlockHitResult hit = Targeting.lookedAtBlock(player, RANGE);
        if (hit == null) {
            return;
        }
        BlockPos spot = spotOf(hit);
        if (!isFree(player, spot)) {
            return;
        }
        ArmorStand seal = EntityType.ARMOR_STAND.create(level);
        if (seal == null) {
            return;
        }
        seal.moveTo(spot.getX() + 0.5d, spot.getY() + 0.05d, spot.getZ() + 0.5d, 0.0f, 0.0f);
        seal.setInvisible(true);
        seal.setNoGravity(true);
        seal.setNoBasePlate(true);
        seal.setSilent(true);
        // The marker flag has no public setter in 1.20.1; it is one bit of the stand's client flags,
        // and setting the synched data directly is what the private setter does anyway - the entity
        // reacts to the change by collapsing its own hitbox.
        SynchedEntityData standData = seal.getEntityData();
        standData.set(ArmorStand.DATA_CLIENT_FLAGS,
                (byte) (standData.get(ArmorStand.DATA_CLIENT_FLAGS) | ArmorStand.CLIENT_FLAG_MARKER));
        // A stray arrow must not spend somebody else's seal.
        seal.setInvulnerable(true);

        CompoundTag data = seal.getPersistentData();
        data.putBoolean(SEAL_TAG, true);
        data.putLong(UNTIL_TAG, level.getGameTime() + LIFETIME);
        data.putUUID(OWNER_TAG, player.getUUID());

        level.addFreshEntity(seal);
        level.sendParticles(ParticleTypes.ENCHANT, spot.getX() + 0.5d, spot.getY() + 0.5d, spot.getZ() + 0.5d,
                1, 0.02d, 0.02d, 0.02d, 0.0d);
    }

    /** The block the seal is pressed into: the empty space on the face the caster is looking at. */
    private static BlockPos spotOf(BlockHitResult hit) {
        return hit.getBlockPos().relative(hit.getDirection());
    }

    /** Whether a seal may be pressed at this spot: nothing solid there, and no seal already on it. */
    private static boolean isFree(Player player, BlockPos spot) {
        if (!player.level().getBlockState(spot).getCollisionShape(player.level(), spot).isEmpty()) {
            return false;
        }
        return player.level().getEntitiesOfClass(ArmorStand.class, new AABB(spot).inflate(0.5d),
                stand -> stand.getPersistentData().getBoolean(SEAL_TAG)).isEmpty();
    }
}
