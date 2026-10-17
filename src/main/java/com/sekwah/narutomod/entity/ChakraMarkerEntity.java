package com.sekwah.narutomod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * The log a ninja leaves behind for their return technique, see {@code ChakraMarkerJutsuAbility}.
 *
 * <p>It is scenery rather than a mob: it never moves, cannot be pushed, hurt or despawned, and is
 * marked as belonging to the player who left it. A player only ever has one of these planted at a
 * time, planting a new one erases the old.</p>
 *
 * <p>Rendered with the substitution log's model and texture, which is the same thing the technique
 * leaves behind when it swaps places with its caster.</p>
 */
public class ChakraMarkerEntity extends Mob {

    private static final String OWNER_TAG = "Owner";

    /** How often the marker gives off a wisp of chakra smoke, in ticks. */
    private static final int PARTICLE_INTERVAL = 4;

    @Nullable
    private UUID owner;

    public ChakraMarkerEntity(EntityType<ChakraMarkerEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        this.setInvulnerable(true);
        this.setNoGravity(true);
        // Like a clone it stays until it is deliberately removed, never because nobody is watching.
        this.setPersistenceRequired();
    }

    public ChakraMarkerEntity(Level level, Player owner) {
        this(NarutoEntities.CHAKRA_MARKER.get(), level);
        this.owner = owner.getUUID();
        this.setYRot(owner.getYRot());
        this.yRotO = this.getYRot();
        this.setYHeadRot(this.getYRot());
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.0d);
    }

    public boolean isOwnedBy(Player player) {
        return this.owner != null && this.owner.equals(player.getUUID());
    }

    @Override
    public void tick() {
        super.tick();
        if (this.tickCount % PARTICLE_INTERVAL == 0 && this.level() instanceof ServerLevel serverLevel) {
            // Enough of a shimmer to spot the log across a clearing, and to tell it apart from a
            // log somebody simply dropped.
            serverLevel.sendParticles(ParticleTypes.PORTAL, this.getX(), this.getY() + 0.7d, this.getZ(),
                    2, 0.2d, 0.4d, 0.2d, 0.02d);
        }
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    /**
     * Nothing gets to hurt the marker: it is scenery, not a mob.
     *
     * <p>Marking it invulnerable is not enough by itself. A player in creative mode walks straight
     * through invulnerability, and so does the generic kill damage behind {@code /kill}, which would
     * let anyone else take the caster's way home away from them.</p>
     */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return true;
    }

    /**
     * {@code /kill} is not a way to erase a marker either. The only thing that removes one is its
     * owner planting a new log, which erases the old one on purpose.
     */
    @Override
    public void kill() {
    }

    @Override
    public void die(DamageSource source) {
        // Unreachable while hurt is refused, but the marker must not fall over for any reason: the
        // caster would lose the way back with it.
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.owner != null) {
            tag.putUUID(OWNER_TAG, this.owner);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID(OWNER_TAG)) {
            this.owner = tag.getUUID(OWNER_TAG);
        }
        // Mob#readAdditionalSaveData resets this from the tag, which would let markers despawn.
        this.setPersistenceRequired();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
