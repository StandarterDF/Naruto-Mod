package com.sekwah.narutomod.entity;

import com.sekwah.narutomod.entity.ai.CloneFollowOwnerGoal;
import com.sekwah.narutomod.entity.ai.CloneOwnerHurtByTargetGoal;
import com.sekwah.narutomod.entity.ai.CloneOwnerHurtTargetGoal;
import com.sekwah.narutomod.entity.ai.CloneRangedAttackGoal;
import com.sekwah.narutomod.entity.ai.CloneUseShieldGoal;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.Optional;
import java.util.UUID;

/**
 * A chakra copy of the player who cast the clone technique.
 *
 * <p>It looks like its owner (skin, held item and armour are copied), follows them around and
 * fights whatever they fight. It has no loot and no death animation: when it runs out of chakra
 * (its lifetime) or takes a killing blow it simply pops in a cloud of smoke.</p>
 *
 * <p>Clones pick up their caster's fighting style as well. One that spawns holding a shield raises
 * it when something gets close, and one that spawns holding a ranged weapon uses it the way a
 * skeleton, pillager or drowned would: bows and crossbows fire the arrows the caster was carrying
 * (a copy, not a loan, which is spent arrow by arrow until the clone pops), while a trident is
 * simply hurled at whatever it is fighting.</p>
 *
 * <p>Ported from the 1.7.10 {@code EntityShadowClone} of the same mod.</p>
 */
public class ShadowCloneEntity extends PathfinderMob implements RangedAttackMob {

    private static final EntityDataAccessor<Optional<UUID>> DATA_OWNER =
            SynchedEntityData.defineId(ShadowCloneEntity.class, EntityDataSerializers.OPTIONAL_UUID);
    private static final EntityDataAccessor<String> DATA_OWNER_NAME =
            SynchedEntityData.defineId(ShadowCloneEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Boolean> DATA_CHIBI =
            SynchedEntityData.defineId(ShadowCloneEntity.class, EntityDataSerializers.BOOLEAN);

    /** How long a clone sticks around before it runs out of chakra, in ticks. */
    public static final int DEFAULT_LIFETIME = 20 * 25;

    /**
     * Anything closer than this is melee range. A clone stops shooting at it and starts swinging
     * (and blocking), the same distance the melee goal can actually reach.
     */
    public static final double MELEE_REACH = 3.0d;

    /** Chibi clones are scaled down, both for rendering and for their hitbox. */
    public static final float CHIBI_SCALE = 0.5f;
    private static final EntityDimensions CHIBI_DIMENSIONS =
            EntityDimensions.scalable(0.6f * CHIBI_SCALE, 1.8f * CHIBI_SCALE);

    /** Slots worth copying from the owner. {@code BODY} is deliberately skipped, mobs have no such slot. */
    private static final EquipmentSlot[] COPIED_SLOTS = {
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND,
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private int lifetime = DEFAULT_LIFETIME;

    /**
     * The arrows the clone copied from its caster when it appeared, or empty when the caster had
     * none. A clone only ever shoots while this stack still has arrows in it.
     */
    private ItemStack ammo = ItemStack.EMPTY;

    public ShadowCloneEntity(EntityType<ShadowCloneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        // Clones only ever leave when their timer runs out, never because the player wandered off.
        this.setPersistenceRequired();
    }

    public ShadowCloneEntity(Level level, Player owner, int lifetime, boolean chibi) {
        this(NarutoEntities.SHADOW_CLONE.get(), level);
        this.setOwner(owner);
        this.lifetime = lifetime;
        this.setChibi(chibi);
        this.copyEquipment(owner);
        this.copyAmmo(owner);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 1.0d)
                .add(Attributes.MOVEMENT_SPEED, 0.3d)
                .add(Attributes.ATTACK_DAMAGE, 2.0d)
                .add(Attributes.FOLLOW_RANGE, 40.0d);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        this.entityData.define(DATA_OWNER, Optional.empty());
        this.entityData.define(DATA_OWNER_NAME, "");
        this.entityData.define(DATA_CHIBI, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Both weapon goals are always registered and decide for themselves whether they apply, so
        // a clone that is handed a bow later (or loses one) does not need its goals rebuilt. The
        // ranged goal sits above melee and only runs while the target is too far away to hit with a
        // sword.
        this.goalSelector.addGoal(1, new CloneRangedAttackGoal(this, 1.1d, 20, 20.0f));
        // Combat comes before following, like it did in the 1.7.10 mod: a clone with a target
        // chases it instead of staying glued to its caster.
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2d, false));
        this.goalSelector.addGoal(3, new CloneUseShieldGoal(this));
        this.goalSelector.addGoal(4, new CloneFollowOwnerGoal(this, 1.1d, 10.0f, 2.0f, 20.0f));
        this.goalSelector.addGoal(5, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Targets are strictly reactive, the same as the 1.7.10 clone: a clone fights what hit its
        // caster, what its caster hit, or whatever hit the clone itself. It never picks a fight on
        // its own, so it will not run off to beat on a piglin the caster was peacefully trading with.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new CloneOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(3, new CloneOwnerHurtTargetGoal(this));
    }

    /**
     * Applies the per-technique stats. Damage and speed differ between the normal, chibi and multi
     * clone versions, but an entity type only gets one attribute supplier.
     *
     * <p>Health is deliberately not one of them: a shadow clone always pops in a single hit, the
     * same as the 1.7.10 version which gave every clone one point of health.</p>
     */
    public void applyVariantAttributes(double attackDamage, double movementSpeed) {
        this.setAttributeValue(Attributes.ATTACK_DAMAGE, attackDamage);
        this.setAttributeValue(Attributes.MOVEMENT_SPEED, movementSpeed);
    }

    private void setAttributeValue(Attribute attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    private void copyEquipment(Player owner) {
        for (EquipmentSlot slot : COPIED_SLOTS) {
            ItemStack stack = owner.getItemBySlot(slot);
            if (!stack.isEmpty()) {
                this.setItemSlot(slot, stack.copy());
            }
            // Nothing may drop if the clone is somehow killed the normal way.
            this.setDropChance(slot, 0.0f);
        }
    }

    /**
     * Copies the caster's arrows: the same arrow type (so tipped and spectral arrows keep their
     * effects) and the same amount, so a clone runs out at the same pace its caster would.
     *
     * <p>The arrows are a copy, the caster keeps theirs. Only the first arrow stack decides the
     * type, the rest only add to the count.</p>
     */
    private void copyAmmo(Player owner) {
        ItemStack arrows = ItemStack.EMPTY;
        int total = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            if (stack.getItem() instanceof ArrowItem) {
                if (arrows.isEmpty()) {
                    arrows = stack.copy();
                }
                total += stack.getCount();
            }
        }
        if (!arrows.isEmpty() && total > 0) {
            arrows.setCount(total);
            this.ammo = arrows;
        }
    }

    /**
     * Fires whatever ranged weapon the clone borrowed from its caster, the same way the mob that
     * normally carries that weapon would.
     *
     * <p>A bow and a crossbow spend the arrows the clone copied from its caster, and when the last
     * one leaves the weapon the clone has used up its chakra and pops. A trident is simply hurled,
     * like a drowned does, and is not consumed.</p>
     */
    @Override
    public void performRangedAttack(LivingEntity target, float velocity) {
        InteractionHand hand = this.getRangedWeaponHand();
        if (hand == null) {
            return;
        }
        ItemStack weapon = this.getItemInHand(hand);

        if (weapon.getItem() instanceof CrossbowItem) {
            this.shootCrossbow(hand, weapon, velocity);
        } else if (weapon.getItem() instanceof TridentItem) {
            this.throwTrident(target, weapon);
        } else {
            this.shootArrow(target, weapon, velocity);
        }
    }

    /**
     * Loose a copied arrow, exactly like a player would with the bow the clone is holding: the arrow
     * keeps its type and the bow's enchantments (Power, Punch, Flame) apply.
     */
    private void shootArrow(LivingEntity target, ItemStack bow, float velocity) {
        if (!this.hasAmmo()) {
            return;
        }

        AbstractArrow arrow = ProjectileUtil.getMobArrow(this, this.ammo, velocity);
        if (bow.getItem() instanceof BowItem bowItem) {
            arrow = bowItem.customArrow(arrow);
        }

        double x = target.getX() - this.getX();
        double y = target.getY(0.3333333333333333d) - arrow.getY();
        double z = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(x * x + z * z);
        arrow.shoot(x, y + horizontal * 0.2d, z, 1.6f,
                (float) (14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));
        this.level().addFreshEntity(arrow);

        this.spendArrow();
    }

    /**
     * Fires the charged crossbow. Vanilla does all the work, it only asks the shooter for a
     * projectile - {@link #getProjectile} answers with the copied arrows, so the bolts really are
     * the caster's arrows and multishot and piercing still apply.
     */
    private void shootCrossbow(InteractionHand hand, ItemStack crossbow, float velocity) {
        if (!this.hasAmmo()) {
            return;
        }

        CrossbowItem.performShooting(this.level(), this, hand, crossbow, velocity,
                (float) (14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.CROSSBOW_SHOOT, 1.0f, 1.0f / (this.getRandom().nextFloat() * 0.4f + 0.8f));

        this.spendArrow();
    }

    /**
     * Hurls the trident the way a drowned does. The trident is a weapon rather than ammunition, so
     * it is not spent and the clone can keep throwing it.
     *
     * <p>The thrown copy is only collectable in creative: it is chakra, not loot, and letting it be
     * picked up would turn clones into a trident farm.</p>
     */
    private void throwTrident(LivingEntity target, ItemStack trident) {
        ThrownTrident thrown = new ThrownTrident(this.level(), this, trident.copy());
        thrown.pickup = AbstractArrow.Pickup.CREATIVE_ONLY;
        double x = target.getX() - this.getX();
        double y = target.getY(0.3333333333333333d) - thrown.getY();
        double z = target.getZ() - this.getZ();
        double horizontal = Math.sqrt(x * x + z * z);
        thrown.shoot(x, y + horizontal * 0.2d, z, 1.6f,
                (float) (14 - this.level().getDifficulty().getId() * 4));
        this.playSound(SoundEvents.TRIDENT_THROW, 1.0f, 1.0f);
        this.level().addFreshEntity(thrown);
    }

    /**
     * Spends one arrow, and pops the clone when the quiver is empty: a clone that could shoot
     * forever would never run out of chakra.
     */
    private void spendArrow() {
        this.ammo.shrink(1);
        if (!this.hasAmmo()) {
            this.dispel();
        }
    }

    /**
     * The projectiles a clone's ranged weapon is allowed to fire. Vanilla's crossbow asks its shooter
     * for these, which is how a clone's crossbow ends up loaded with the arrows its caster carried.
     */
    @Override
    public ItemStack getProjectile(ItemStack weapon) {
        return this.hasAmmo() ? this.ammo : ItemStack.EMPTY;
    }

    public ItemStack getAmmo() {
        return this.ammo;
    }

    public boolean hasAmmo() {
        return !this.ammo.isEmpty() && this.ammo.getCount() > 0;
    }

    /**
     * The hand holding the caster's ranged weapon (bow, crossbow or trident), or {@code null} when
     * the clone is not armed with one. The main hand wins, so a clone mirrors whichever of the two
     * weapons its caster was actually swinging.
     */
    public InteractionHand getRangedWeaponHand() {
        if (isRangedWeapon(this.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        return isRangedWeapon(this.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    private static boolean isRangedWeapon(ItemStack stack) {
        return stack.getItem() instanceof BowItem
                || stack.getItem() instanceof CrossbowItem
                || stack.getItem() instanceof TridentItem;
    }

    /**
     * True for the weapons that shoot arrows out of the caster's quiver. A trident is thrown rather
     * than loaded, so it never needs one.
     */
    public boolean weaponNeedsArrows(ItemStack weapon) {
        return weapon.getItem() instanceof BowItem || weapon.getItem() instanceof CrossbowItem;
    }

    /**
     * True while the clone should be shooting: it holds a ranged weapon it can actually use (a bow
     * or crossbow with arrows left) and its target is too far away to hit with a sword.
     *
     * <p>The ranged and shield goals both ask this, which is what keeps them from fighting over the
     * clone's hands: a clone that wants to shoot never raises its shield, and one that cannot shoot
     * is free to.</p>
     */
    public boolean wantsToShoot() {
        InteractionHand hand = this.getRangedWeaponHand();
        if (hand == null) {
            return false;
        }
        if (this.weaponNeedsArrows(this.getItemInHand(hand)) && !this.hasAmmo()) {
            return false;
        }
        LivingEntity target = this.getTarget();
        return target != null
                && target.isAlive()
                && this.canAttack(target)
                && this.distanceToSqr(target) > MELEE_REACH * MELEE_REACH;
    }

    public boolean isUsingRangedWeapon() {
        return this.isUsingItem() && isRangedWeapon(this.getUseItem());
    }

    public boolean isUsingShield() {
        return this.isUsingItem() && this.getUseItem().getItem() instanceof ShieldItem;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }

        if (this.tickCount == 1) {
            // The puff of smoke every clone appears in.
            this.playSound(NarutoSounds.SHADOW_CLONE_POOF.get(), 0.35f, this.isChibi() ? 1.3f : 1.0f);
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(),
                        this.getY() + this.getBbHeight() / 2.0d, this.getZ(),
                        12, 0.3d, 0.4d, 0.3d, 0.01d);
            }
        }

        if (--this.lifetime <= 0) {
            this.dispel();
        }
    }

    /**
     * Pops the clone: a puff of smoke and the classic poof sound, then it is gone.
     */
    public void dispel() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(),
                    this.getY() + this.getBbHeight() / 2.0d, this.getZ(),
                    30, 0.4d, 0.5d, 0.4d, 0.02d);
            this.playSound(NarutoSounds.SHADOW_CLONE_POOF.get(), 0.6f, this.isChibi() ? 1.3f : 1.0f);
        }
        this.discard();
    }

    /**
     * Clones are smoke and chakra, they never leave a body (or loot) behind.
     */
    @Override
    public void die(DamageSource damageSource) {
        this.dispel();
    }

    /**
     * Clones of the same caster do not fight each other, and neither do they attack their caster.
     *
     * <p>Note that this deliberately does not fall back to {@code super}, which would consider any
     * mob with the same mob type an ally and stop clones from fighting creepers. It also only
     * affects targeting: the caster can still walk up to a clone and pop it with a single hit, the
     * same as in the 1.7.10 mod.</p>
     */
    @Override
    public boolean isAlliedTo(Entity entity) {
        if (entity == this || this.isOwnedBy(entity)) {
            return true;
        }
        if (entity instanceof ShadowCloneEntity other) {
            return this.getOwnerUUID().equals(other.getOwnerUUID());
        }
        return false;
    }

    public boolean isOwnedBy(Entity entity) {
        return entity instanceof Player player && this.getOwnerUUID()
                .filter(uuid -> uuid.equals(player.getUUID()))
                .isPresent();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer) {
        return false;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.PLAYER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.PLAYER_DEATH;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return this.isChibi() ? CHIBI_DIMENSIONS : super.getDimensions(pose);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        if (DATA_CHIBI.equals(key)) {
            this.refreshDimensions();
        }
        super.onSyncedDataUpdated(key);
    }

    public Optional<UUID> getOwnerUUID() {
        return this.entityData.get(DATA_OWNER);
    }

    public String getOwnerName() {
        return this.entityData.get(DATA_OWNER_NAME);
    }

    public void setOwner(Player owner) {
        this.entityData.set(DATA_OWNER, Optional.of(owner.getUUID()));
        this.entityData.set(DATA_OWNER_NAME, owner.getGameProfile().getName());
    }

    /**
     * Server side only, the client resolves the owner through the player list when rendering.
     */
    public Player getOwner() {
        if (this.level().isClientSide) {
            return null;
        }
        MinecraftServer server = this.level().getServer();
        return this.getOwnerUUID()
                .map(uuid -> server == null ? null : server.getPlayerList().getPlayer(uuid))
                .orElse(null);
    }

    public boolean isChibi() {
        return this.entityData.get(DATA_CHIBI);
    }

    public void setChibi(boolean chibi) {
        this.entityData.set(DATA_CHIBI, chibi);
        this.refreshDimensions();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.getOwnerUUID().ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putString("OwnerName", this.getOwnerName());
        tag.putBoolean("Chibi", this.isChibi());
        tag.putInt("Lifetime", this.lifetime);
        tag.put("Ammo", this.ammo.save(new CompoundTag()));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        }
        this.entityData.set(DATA_OWNER_NAME, tag.getString("OwnerName"));
        this.setChibi(tag.getBoolean("Chibi"));
        this.lifetime = tag.contains("Lifetime") ? tag.getInt("Lifetime") : DEFAULT_LIFETIME;
        this.ammo = tag.contains("Ammo") ? ItemStack.of(tag.getCompound("Ammo")) : ItemStack.EMPTY;
        // Mob#readAdditionalSaveData resets this from the tag, which would let clones despawn.
        this.setPersistenceRequired();
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
