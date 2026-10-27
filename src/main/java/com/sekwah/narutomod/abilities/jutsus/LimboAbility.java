package com.sekwah.narutomod.abilities.jutsus;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.NarutoEntities;
import com.sekwah.narutomod.entity.ShadowCloneEntity;
import com.sekwah.narutomod.util.Targeting;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Limbo: a copy of the caster left standing in the border between worlds.
 *
 * <p>The shadow is not an illusion and not a clone in the ordinary sense: it is the caster's own
 * body displaced half a step out of the world, which is why nothing ordinary can see it. It is put
 * down where the caster is looking, holds its ground for eight seconds and strikes whatever stands
 * next to it every second. Only someone looking through a dojutsu can pick it out of the air, and
 * even then only for as long as they keep their eye open (see {@code SixPathsEsotericEvents}).</p>
 *
 * <p>The body is a {@link ShadowCloneEntity} built without a caster, so it copies no gear, carries
 * no seal and pops to a single touch. Its persistence, its hits and its expiry are all run from the
 * world hook, which reads the tags written here; this class only puts the shadow down.</p>
 */
public class LimboAbility extends Ability implements Ability.Cooldown {

    /**
     * How far a cast of this technique carries as a game event. A shadow that nobody can see does
     * not announce itself.
     */
    @Override
    public int noiseRadius() {
        return 0;
    }

    /** C C C C C B - the last key of the Six Paths block. */
    public static final long COMBO = 2232L;

    public static final ResourceLocation ID = new ResourceLocation(NarutoMod.MOD_ID, "limbo");

    /**
     * Marks a clone as a limbo shadow. The world hook that keeps it invisible, lit for dojutsu eyes
     * and striking reads this, so the name is part of the contract rather than an implementation
     * detail.
     */
    public static final String LIMBO_TAG = "narutomod:limbo_clone";

    /** Game time at which the shadow is taken back out of the world. */
    public static final String UNTIL_TAG = "narutomod:limbo_until";

    /** The caster the shadow belongs to; it is discarded when that player is gone. */
    public static final String OWNER_TAG = "narutomod:limbo_owner";

    /** How long the shadow stands, in ticks. */
    public static final int DURATION = 160;

    /** Ticks between two blows of the shadow. */
    public static final int STRIKE_INTERVAL = 20;

    /** How close a body has to stand to be struck. */
    public static final double STRIKE_RANGE = 2.0d;

    /** What one blow of the shadow takes off, dealt as plain magic damage. */
    public static final float STRIKE_DAMAGE = 6.0f;

    /** How far down the look ray the shadow can be placed. */
    private static final double RANGE = 8.0d;

    /** How far above the aimed point the shadow may be lifted to find open air. */
    private static final int SPAWN_LIFT = 2;

    /** What the shadow costs, and how long before the eye can put another one down. */
    private static final float COST = 60.0f;
    private static final int COOLDOWN = 160;

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
        if (!NarutoConfig.rinneganEnabled) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.disabled")
                    .withStyle(ChatFormatting.RED), true);
            return false;
        }
        if (!RinneganAbility.isActive(ninjaData)) {
            player.displayClientMessage(Component.translatable("jutsu.fail.rinnegan.required",
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
        ninjaData.useChakra(cost, CHAKRA_REGEN_DELAY);
        return true;
    }

    @Override
    public void performServer(Player player, INinjaData ninjaData, int ticksActive) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 at = spawnPoint(player);
        ShadowCloneEntity shadow = this.spawnShadow(level, player, at);
        level.addFreshEntity(shadow);

        level.sendParticles(ParticleTypes.REVERSE_PORTAL, at.x, at.y + shadow.getBbHeight() * 0.5d, at.z,
                30, 0.3d, 0.7d, 0.3d, 0.08d);
        // Quiet on purpose: the shadow appears without a crack of displaced air.
        level.playSound(null, at.x, at.y, at.z, SoundEvents.ENDERMAN_AMBIENT, SoundSource.PLAYERS,
                0.35f, 0.6f);
    }

    /**
     * Where the shadow is set down: the face of the block under the crosshair, the feet of the body
     * under it, or a point along the look ray when it is aimed at open air.
     */
    private static Vec3 spawnPoint(Player player) {
        BlockHitResult block = Targeting.lookedAtBlock(player, RANGE);
        if (block != null) {
            return block.getLocation();
        }
        Entity looked = Targeting.lookedAtEntity(player, RANGE);
        if (looked != null) {
            return looked.position();
        }
        return player.getEyePosition().add(player.getViewVector(1.0f).scale(RANGE));
    }

    /**
     * Builds the shadow. The bare entity constructor is used on purpose: the fighting-clone
     * constructor copies the caster's gear, arrows and paper bomb, and a limbo shadow carries none
     * of them - it is a body, not a second caster.
     *
     * <p>It is left with no AI and no gravity so it stands exactly where it was put instead of
     * strolling off or sinking through a wall, and it is silenced so the clone's own appearance and
     * death sounds never give it away.</p>
     */
    private ShadowCloneEntity spawnShadow(ServerLevel level, Player player, Vec3 at) {
        ShadowCloneEntity shadow = new ShadowCloneEntity(NarutoEntities.SHADOW_CLONE.get(), level);
        shadow.setOwner(player);
        shadow.setHealth(1.0f);
        shadow.setInvisible(true);
        shadow.setSilent(true);
        shadow.setNoAi(true);
        shadow.setNoGravity(true);
        shadow.setTarget(null);

        CompoundTag data = shadow.getPersistentData();
        data.putBoolean(LIMBO_TAG, true);
        data.putLong(UNTIL_TAG, level.getGameTime() + DURATION);
        data.putUUID(OWNER_TAG, player.getUUID());

        for (int lift = 0; lift <= SPAWN_LIFT; lift++) {
            shadow.moveTo(at.x, at.y + lift, at.z, player.getYRot(), 0.0f);
            if (level.noCollision(shadow, shadow.getBoundingBox())) {
                break;
            }
        }
        return shadow;
    }

    /** @return whether this entity was put down by this technique. */
    public static boolean isLimboShadow(Entity entity) {
        return entity.getPersistentData().getBoolean(LIMBO_TAG);
    }
}
