package com.sekwah.narutomod.entity;

import com.sekwah.narutomod.block.NarutoBlocks;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.entity.ai.CloneDefendOwnerGoal;
import com.sekwah.narutomod.entity.ai.CloneFollowOwnerGoal;
import com.sekwah.narutomod.entity.ai.CloneOwnerHurtByTargetGoal;
import com.sekwah.narutomod.entity.ai.CloneOwnerHurtTargetGoal;
import com.sekwah.narutomod.entity.ai.ClonePickupLootGoal;
import com.sekwah.narutomod.entity.ai.CloneRangedAttackGoal;
import com.sekwah.narutomod.entity.ai.CloneUseShieldGoal;
import com.sekwah.narutomod.entity.ai.WorkerChopGoal;
import com.sekwah.narutomod.entity.ai.WorkerHarvestGoal;
import com.sekwah.narutomod.entity.ai.WorkerHuntGoal;
import com.sekwah.narutomod.entity.ai.WorkerMineGoal;
import com.sekwah.narutomod.entity.ai.WorkerTorchGoal;
import com.sekwah.narutomod.friends.Friends;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.sounds.NarutoSounds;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ShieldItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
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
 * <p>Two more things rub off on a clone. It inherits any potion effects the caster was under when it
 * appeared, and if the caster was holding a paper bomb then so is the clone: when a killing blow
 * pops it, it takes the charge with it, hurting everything around it but neither its caster (or
 * their other clones) nor a single block.</p>
 *
 * <p>A clone can also be made to meditate instead of fight (see
 * {@code ShadowCloneJutsuAbility.CHAKRA_MEDITATION_SETTINGS}): it sits itself down, keeps its hands
 * free for a seal and stays put, feeding chakra back to its caster for as long as they stay close.</p>
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
    private static final EntityDataAccessor<Boolean> DATA_MEDITATING =
            SynchedEntityData.defineId(ShadowCloneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_WORKER =
            SynchedEntityData.defineId(ShadowCloneEntity.class, EntityDataSerializers.BOOLEAN);

    /** How long a clone sticks around before it runs out of chakra, in ticks. */
    public static final int DEFAULT_LIFETIME = 20 * 25;

    /**
     * How long a worker clone works. A worker is summoned by the same techniques as a fighter but
     * with the Worker's Seal in hand, and the whole point of it is to be left behind at a field, a
     * mine or a tree line, so it outlives every combat clone by a wide margin - a minute and a half
     * against the fighters' twenty-five seconds.
     */
    public static final int WORKER_LIFETIME = 20 * 90;

    /**
     * How much health a worker has. A fighting clone pops at the first touch, the same as in 1.7.10,
     * but a worker is left alone to work and deserves a chance to hit back rather than dying to the
     * first stray swipe of a zombie that wandered into its field.
     */
    public static final double WORKER_HEALTH = 4.0d;

    /**
     * How far a clone can drop before its chakra gives out. A clone is chakra shaped like its
     * caster, so a short fall costs it nothing - it lands the way a ninja does and walks on - but
     * anything past this is a drop it cannot hold together through, and it pops on impact.
     */
    public static final float SAFE_FALL_DISTANCE = 10.0f;

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

    /**
     * The most arrows a copied quiver may hold. {@code ItemStack#save} writes the count as a single
     * byte, so anything above this would wrap into a negative stack after a reload.
     */
    private static final int MAX_COPIED_ARROWS = 127;

    /**
     * True when the caster was holding a paper bomb at spawn time. Such a clone goes off when a
     * killing blow pops it instead of just leaving a puff of smoke.
     */
    private boolean explosive;

    /** Where a worker was put down; kept as a record of the summoning spot. */
    private BlockPos workerHome;

    /** What a worker was told to do, decided by the tool in the caster's main hand. */
    private WorkerJob workerJob = WorkerJob.FARM;

    /** Crops a worker has reaped and is carrying home, see {@link #collect(ItemStack)}. */
    private final List<ItemStack> bag = new ArrayList<>();

    /**
     * Torches a worker still has to light the dark it works in, copied from its caster at summon
     * time. The stack in its off hand is only the visible part of this count - the rest is the same
     * trick the arrows use, a whole supply the caster keeps and the clone spends.
     */
    private int workerTorches;

    public ShadowCloneEntity(EntityType<ShadowCloneEntity> type, Level level) {
        super(type, level);
        this.xpReward = 0;
        // Clones only ever leave when their timer runs out, never because the player wandered off.
        this.setPersistenceRequired();
    }

    public ShadowCloneEntity(Level level, Player owner, int lifetime, boolean chibi) {
        this(level, owner, lifetime, chibi, false);
    }

    /**
     * @param meditating a clone that stays put and channels chakra back to its caster instead of
     *                   fighting, see {@code ShadowCloneJutsuAbility.CHAKRA_MEDITATION_SETTINGS}.
     */
    public ShadowCloneEntity(Level level, Player owner, int lifetime, boolean chibi, boolean meditating) {
        this(NarutoEntities.SHADOW_CLONE.get(), level);
        this.setOwner(owner);
        this.lifetime = lifetime;
        this.setChibi(chibi);
        this.setMeditating(meditating);
        if (meditating) {
            // Hands meet in a seal, there is nothing to hold and nothing to copy.
            this.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        } else {
            this.copyEquipment(owner);
            this.copyAmmo(owner);
            this.copyEffects(owner);
            this.explosive = isHoldingPaperBomb(owner);
        }
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
        this.entityData.define(DATA_MEDITATING, false);
        this.entityData.define(DATA_WORKER, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Above every other goal on purpose. Vanilla only lets a goal take the movement flag from a
        // running goal that has a strictly worse priority number, so a loot goal further down would
        // never get a turn: a clone is working, following or fighting nearly all the time, and it
        // would walk straight past anything lying on the ground. This way even a clone in the middle
        // of a fight breaks off to fetch what it sees, then goes back to what it was doing.
        this.goalSelector.addGoal(0, new ClonePickupLootGoal(this));
        // A worker clone works instead of fighting or following, and which shift it works depends
        // on the tool it was summoned with. Each goal refuses every other job (and every other kind
        // of clone), so only the matching one ever runs.
        this.goalSelector.addGoal(1, new WorkerHarvestGoal(this));
        this.goalSelector.addGoal(1, new WorkerMineGoal(this));
        this.goalSelector.addGoal(1, new WorkerChopGoal(this));
        this.goalSelector.addGoal(1, new WorkerHuntGoal(this));
        // A worker lights its own way when it works in the dark. The goal holds no flags, so it runs
        // alongside whatever shift is going on rather than competing for movement.
        this.goalSelector.addGoal(1, new WorkerTorchGoal(this));
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
        this.goalSelector.addGoal(6, new OpenDoorGoal(this, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 1.0d));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(9, new RandomLookAroundGoal(this));

        // Targets are reactive, the same as the 1.7.10 clone: a clone fights what hit its caster,
        // what its caster hit, or whatever hit the clone itself. On top of that it steps in front of
        // a mob that is already hunting its caster, so the caster does not have to take the first hit
        // while their clones stand and watch. It still never picks a fight on its own, so it will not
        // run off to beat on a piglin the caster was peacefully trading with.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new CloneDefendOwnerGoal(this));
        this.targetSelector.addGoal(3, new CloneOwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(4, new CloneOwnerHurtTargetGoal(this));
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
            // A quiver is a plain stack carried in the clone's data, and stacks are written with a
            // byte-sized count. Summing nine stacks of arrows can pass 127, which would wrap at the
            // next save and leave the clone with no arrows at all, so the copy is capped at what a
            // stack can actually hold on disk.
            arrows.setCount(Math.min(total, MAX_COPIED_ARROWS));
            this.ammo = arrows;
        }
    }

    /**
     * Copies every effect the caster is under, with its remaining duration. Drawbacks come along too,
     * which is only fair: a clone of a poisoned caster is just as doomed as the caster is.
     */
    private void copyEffects(Player owner) {
        for (MobEffectInstance effect : owner.getActiveEffects()) {
            this.addEffect(new MobEffectInstance(effect));
        }
    }

    /**
     * A paper bomb in either hand at spawn time is all it takes to make a clone go off when it pops.
     */
    private static boolean isHoldingPaperBomb(Player owner) {
        Item paperBomb = NarutoBlocks.ITEM_PAPER_BOMB.get();
        return owner.getMainHandItem().is(paperBomb) || owner.getOffhandItem().is(paperBomb);
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

        if (this.isMeditating()) {
            // Keep the caster's link to this clone fresh, and its expiry honest, so a meditation
            // reaches as far as its mastery allows without anyone scanning the world for it.
            this.refreshMeditationLink();
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
            return;
        }

        if (this.isMeditating() && this.tickCount % 4 == 0
                && this.level() instanceof ServerLevel serverLevel) {
            // A trickle of chakra, so it is obvious which clones are feeding the caster.
            serverLevel.sendParticles(ParticleTypes.ENCHANT, this.getX(),
                    this.getY() + 1.0d, this.getZ(),
                    3, 0.35d, 0.4d, 0.35d, 0.02d);
        }
    }

    /**
     * Pops the clone when its time runs out (or its quiver does): a puff of smoke and the classic
     * poof sound, then it is gone. A worker hands its bag straight over on the way out.
     */
    public void dispel() {
        this.pop();
    }

    /** Pops the clone: a puff of smoke, a worker's harvest handed to its caster, and it is gone. */
    private void pop() {
        if (this.level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.CLOUD, this.getX(),
                    this.getY() + this.getBbHeight() / 2.0d, this.getZ(),
                    30, 0.4d, 0.5d, 0.4d, 0.02d);
            this.playSound(NarutoSounds.SHADOW_CLONE_POOF.get(), 0.6f, this.isChibi() ? 1.3f : 1.0f);
        }
        this.releaseLoot();
        if (this.isMeditating()) {
            this.dropMeditationLink();
        }
        this.discard();
    }

    /**
     * Clones are smoke and chakra, they never leave a body (or loot) behind.
     *
     * <p>A clone that was made out of a paper bomb takes the charge with it: the killing blow pops
     * it in a blast. Running out of chakra or of arrows is not a killing blow, so those clones go
     * quietly.</p>
     *
     * <p>A worker cut down hands its bag over the same way a timed out one does: at once, so it is
     * plain that every worker carries its own harvest.</p>
     *
     * <p>A clone that was holding a totem of undying in its offhand activates it the same way a
     * player would: the totem is checked and activated before {@code pop()}, restoring health,
     * applying regeneration and absorption, and consuming the totem. The clone then vanishes in
     * smoke as usual. The totem does not protect the clone from its caster: the summoner can
     * always pop a clone by hand, regardless of equipped totems.</p>
     */
    @Override
    public void die(DamageSource damageSource) {
        if (this.explosive && !this.level().isClientSide) {
            this.explode();
        }
        // Totem of undying saves the clone from death, but not from its caster: the summoner
        // can always pop a clone by hand, regardless of whether it has a totem equipped.
        boolean isFromCaster = this.getOwnerUUID().map(uuid -> {
            net.minecraft.world.entity.Entity source = damageSource.getEntity();
            return source instanceof Player caster && uuid.equals(caster.getUUID());
        }).orElse(false);
        // Activate the totem of undying (if any) before popping. Vanilla checks the offhand slot,
        // restores the entity to full health, applies regeneration and absorption, plays the
        // activation sound, spawns particles, and consumes one totem.
        if (!isFromCaster && !this.getOffhandItem().isEmpty()
                && this.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING) {
            this.getOffhandItem().shrink(1);
            this.setHealth(this.getMaxHealth());
            this.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 100, 0));
            this.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0));
            if (this.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, this.getX(), this.getY(), this.getZ(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
                serverLevel.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        this.getX(), this.getY() + 0.5d, this.getZ(),
                        1, 0.5d, 0.5d, 0.5d, 0.0d);
            }
        }
        this.pop();
    }

    /**
     * The blast of an explosive clone. Like a paper bomb it hurts everything nearby, except that it
     * never breaks blocks: clones are too cheap for that. The caster and their other clones are
     * spared, see {@code ExplosionEvents}.
     */
    private void explode() {
        this.level().explode(this, this.getX(), this.getY(0.0625d), this.getZ(),
                NarutoConfig.paperbombExplosionRadius, Level.ExplosionInteraction.NONE);
    }

    public boolean isExplosive() {
        return this.explosive;
    }

    /**
     * Clones land like their caster: a drop up to {@link #SAFE_FALL_DISTANCE} is shrugged off rather
     * than taken on the chin, because these are chakra constructs with the ninja's own conditioning,
     * not clumsy mobs. Past that the fall simply wins - the clone has no stamina left to soften it
     * and pops on the ground, exactly as if it had been struck down.
     */
    @Override
    public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
        if (fallDistance <= SAFE_FALL_DISTANCE) {
            return false;
        }
        return super.causeFallDamage(fallDistance, multiplier, source);
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
            return this.getOwnerUUID().equals(other.getOwnerUUID())
                    || Friends.areFriends(this.getOwner(), other.getOwner());
        }
        if (entity instanceof Player) {
            // A caster's friends are the caster's side: clone bodies and clone sights spare them.
            return Friends.areFriends(this.getOwner(), entity);
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

    /**
     * A clone never blocks its own caster. It copies the caster's offhand, so most clones carry the
     * same shield the caster does, and {@code CloneUseShieldGoal} keeps it up for as long as the
     * clone is fighting anything. Without this, a caster walking up to a clone in the middle of a
     * fight would find every swing eaten by the shield the clone borrowed from them - and a clone
     * that blocks takes no damage, so it would not pop either. Everything else (a zombie, another
     * player, a stray arrow) still has to get through the shield the normal way.
     */
    @Override
    public boolean isDamageSourceBlocked(DamageSource damageSource) {
        return !this.isCasterBlow(damageSource) && super.isDamageSourceBlocked(damageSource);
    }

    /** Whether this damage is the caster's own hand (or what they are holding) landing on the clone. */
    private boolean isCasterBlow(DamageSource damageSource) {
        Entity attacker = damageSource.getEntity();
        Entity direct = damageSource.getDirectEntity();
        return this.isOwnedBy(attacker) || this.isOwnedBy(direct);
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

    /**
     * A meditating clone sits still and hands chakra back to its caster instead of fighting.
     */
    public boolean isMeditating() {
        return this.entityData.get(DATA_MEDITATING);
    }

    public void setMeditating(boolean meditating) {
        this.entityData.set(DATA_MEDITATING, meditating);
    }

    /**
     * Writes this clone into its caster's list of standing meditations, refreshing the moment it is
     * due to pop, so the caster can be fed from any distance without the world being scanned, see
     * {@code MeditationData}.
     */
    private void refreshMeditationLink() {
        Player owner = this.getOwner();
        if (owner == null) {
            return;
        }
        owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData ->
                ninjaData.getMeditationData().register(this.getUUID(),
                        this.level().dimension().location(), this.position(),
                        this.level().getGameTime() + Math.max(1, this.lifetime)));
    }

    /** Takes this clone back out of its caster's list when it goes, so the link does not linger. */
    private void dropMeditationLink() {
        Player owner = this.getOwner();
        if (owner == null) {
            return;
        }
        owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData ->
                ninjaData.getMeditationData().remove(this.getUUID()));
    }

    /**
     * A worker clone does not fight and does not follow: it works the ground around the spot it was
     * put down on, doing whatever its {@link WorkerJob} says, see
     * {@code com.sekwah.narutomod.entity.ai.WorkerBlockGoal}. (The one exception is a hunter, which
     * goes after game as part of its shift.)
     */
    public boolean isWorker() {
        return this.entityData.get(DATA_WORKER);
    }

    public void setWorker(boolean worker) {
        this.entityData.set(DATA_WORKER, worker);
        if (worker) {
            // A worker is not a front line fighter, but it should not pop at the first touch either:
            // it gets a few points of health so it can take a hit and give one back before it goes.
            // Only a whole clone is topped up, so a worker that was wounded and saved comes back as
            // wounded as it left.
            boolean wasWhole = this.getHealth() >= this.getMaxHealth();
            this.setAttributeValue(Attributes.MAX_HEALTH, WORKER_HEALTH);
            if (wasWhole) {
                this.setHealth((float) WORKER_HEALTH);
            }
        }
    }

    /**
     * Workers do not pick fights through the vanilla target system: a farmhand that wandered off to
     * kill the cows its caster happened to hit would look broken, so a target handed to a worker by
     * its caster - or by another clone's alarm - is dropped. The one exception is self defence: a
     * worker that is struck may hit back at whatever struck it, and only at that - and never at its
     * own caster or a fellow clone of the same caster, who are allies even when they swing first. (A
     * hunting worker does go after animals, but it does so through its own goal, not through
     * {@code setTarget}.)
     */
    @Override
    public void setTarget(LivingEntity target) {
        // A friend of the caster is never a target, not even in self defence against that friend.
        if (target != null && Friends.areFriends(this.getOwner(), target)) {
            return;
        }
        if (this.isWorker() && target != null
                && (target != this.getLastHurtByMob() || this.isAlliedTo(target))) {
            return;
        }
        super.setTarget(target);
    }

    /** @return the shift this worker was summoned for; meaningless on a non-worker clone. */
    public WorkerJob getWorkerJob() {
        return this.workerJob;
    }

    public void setWorkerJob(WorkerJob job) {
        this.workerJob = job;
    }

    /**
     * Gives the worker the tool it is supposed to swing and empties its other hand.
     *
     * <p>The constructor copies both of the caster's hands, which for a worker means the tool in the
     * main hand and the seal in the off hand. A worker holds the tool (where a mob swings from) but
     * not the seal, so the off hand is cleared.</p>
     */
    public void setWorkerTool(ItemStack tool) {
        this.setItemSlot(EquipmentSlot.MAINHAND, tool.copy());
        this.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
    }

    /**
     * Takes a copy of the caster's torches, so a worker can light the dark it works in. Like the
     * arrows, this is a copy of everything the caster carries rather than a loan, and the caster's
     * own stock is untouched.
     */
    public void copyWorkerTorches(Player owner) {
        int total = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot);
            if (stack.is(Items.TORCH)) {
                total += stack.getCount();
            }
        }
        this.setWorkerTorches(total);
    }

    /** Sets the torch stock outright, keeping the off-hand display in step. */
    public void setWorkerTorches(int torches) {
        this.workerTorches = Math.max(0, torches);
        this.updateTorchDisplay();
    }

    /** How many torches the worker has left to light its way with. */
    public int getWorkerTorches() {
        return this.workerTorches;
    }

    /** Burns one torch from the stock. @return whether there was one to burn. */
    public boolean consumeTorch() {
        if (this.workerTorches <= 0) {
            return false;
        }
        this.workerTorches--;
        this.updateTorchDisplay();
        return true;
    }

    /**
     * Keeps the off-hand stack showing as much of the torch stock as one stack holds. The stock
     * itself can be larger than 64, so this is only the visible face of it.
     */
    private void updateTorchDisplay() {
        int shown = Math.min(this.workerTorches, Items.TORCH.getMaxStackSize());
        this.setItemSlot(EquipmentSlot.OFFHAND, shown > 0 ? new ItemStack(Items.TORCH, shown) : ItemStack.EMPTY);
    }

    /** The middle of a worker's field: where it was placed. */
    public BlockPos getWorkerHome() {
        return this.workerHome;
    }

    public void setWorkerHome(BlockPos home) {
        this.workerHome = home;
    }

    /**
     * Takes what a worker just picked up. It goes straight into the caster's supply chest once the
     * caster has learned to open it; anything that will not fit - or a caster who is not around, or
     * has not learned the chest yet - falls back into the bag, which is handed over when the worker
     * pops, so nothing is lost either way.
     */
    public void collect(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack leftover = this.depositInSupplyChest(stack);
        if (!leftover.isEmpty()) {
            this.bag.add(leftover);
        }
    }

    /**
     * Whether this clone is allowed to gather loot at all.
     *
     * <p>Nothing is picked up until the caster has learned to open the supply chest: a clone hoovering
     * items into a chest its caster cannot reach is worse than leaving them on the ground, where the
     * caster can still walk over and take them. A clone with no caster around gathers nothing either,
     * since there is nobody to hand it to.</p>
     */
    public boolean canGatherLoot() {
        Player owner = this.getOwner();
        if (owner == null) {
            return false;
        }
        INinjaData ninjaData = owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        return ninjaData != null
                && SkillTree.hasUnlock(ninjaData.getSkillData().getUnlocked(), "ability:storage_chest");
    }

    /**
     * Puts one stack into the caster's supply chest, directly. The chest lives in the caster's own
     * data rather than in a block, so there is no distance or chunk to worry about.
     *
     * @return what would not fit, or the whole stack when the caster is not around or has not learned
     *         the supply chest yet.
     */
    private ItemStack depositInSupplyChest(ItemStack stack) {
        if (!this.canGatherLoot()) {
            return stack.copy();
        }
        Player owner = this.getOwner();
        INinjaData ninjaData = owner.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        if (ninjaData == null) {
            return stack.copy();
        }
        return ninjaData.getSupplyChest().insert(stack);
    }

    /**
     * Hands a popped worker's harvest to its caster at once.
     *
     * <p>There is deliberately no walk-home delay: a worker's bag used to be held back for a while,
     * but that made it look as if the harvest had been lost when the clone vanished. The bag goes to
     * the caster the moment the clone pops, whether it ran out of time or was cut down. If the caster
     * is not around to take it the harvest is simply dropped where the clone stood, so nothing is
     * ever lost to a logout.</p>
     */
    private void releaseLoot() {
        if (this.bag.isEmpty()) {
            return;
        }
        List<ItemStack> loot = new ArrayList<>(this.bag);
        this.bag.clear();
        Player owner = this.getOwner();
        if (owner == null) {
            for (ItemStack stack : loot) {
                this.spawnAtLocation(stack);
            }
            return;
        }
        deliverLoot(owner, loot);
    }

    private static void deliverLoot(Player player, List<ItemStack> loot) {
        for (ItemStack stack : loot) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
        player.displayClientMessage(Component.translatable("jutsu.worker.harvest")
                .withStyle(ChatFormatting.GREEN), true);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        this.getOwnerUUID().ifPresent(uuid -> tag.putUUID("Owner", uuid));
        tag.putString("OwnerName", this.getOwnerName());
        tag.putBoolean("Chibi", this.isChibi());
        tag.putBoolean("Meditating", this.isMeditating());
        tag.putInt("Lifetime", this.lifetime);
        tag.put("Ammo", this.ammo.save(new CompoundTag()));
        tag.putBoolean("Explosive", this.explosive);
        tag.putBoolean("Worker", this.isWorker());
        tag.putString("WorkerJob", this.workerJob.name());
        if (this.workerHome != null) {
            tag.putLong("WorkerHome", this.workerHome.asLong());
        }
        tag.putInt("WorkerTorches", this.workerTorches);
        if (!this.bag.isEmpty()) {
            ListTag bagTag = new ListTag();
            for (ItemStack stack : this.bag) {
                bagTag.add(stack.save(new CompoundTag()));
            }
            tag.put("WorkerBag", bagTag);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        // The worker flag goes on before the health is read back: a worker has a higher maximum than
        // a fighting clone, and vanilla clamps saved health to the maximum, so the flag has to be in
        // place first or every worker would come back from a save at one point of health.
        this.setWorker(tag.getBoolean("Worker"));
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("Owner")) {
            this.entityData.set(DATA_OWNER, Optional.of(tag.getUUID("Owner")));
        }
        this.entityData.set(DATA_OWNER_NAME, tag.getString("OwnerName"));
        this.setChibi(tag.getBoolean("Chibi"));
        this.setMeditating(tag.getBoolean("Meditating"));
        this.lifetime = tag.contains("Lifetime") ? tag.getInt("Lifetime") : DEFAULT_LIFETIME;
        this.ammo = tag.contains("Ammo") ? ItemStack.of(tag.getCompound("Ammo")) : ItemStack.EMPTY;
        this.explosive = tag.getBoolean("Explosive");
        this.workerJob = parseWorkerJob(tag.getString("WorkerJob"));
        if (tag.contains("WorkerHome")) {
            this.workerHome = BlockPos.of(tag.getLong("WorkerHome"));
        }
        this.setWorkerTorches(tag.getInt("WorkerTorches"));
        this.bag.clear();
        ListTag bagTag = tag.getList("WorkerBag", 10);
        for (int i = 0; i < bagTag.size(); i++) {
            ItemStack stack = ItemStack.of(bagTag.getCompound(i));
            if (!stack.isEmpty()) {
                this.bag.add(stack);
            }
        }
        // Mob#readAdditionalSaveData resets this from the tag, which would let clones despawn.
        this.setPersistenceRequired();
    }

    /** Reads a saved job name, falling back to farming for tags written before jobs existed. */
    private static WorkerJob parseWorkerJob(String name) {
        try {
            return WorkerJob.valueOf(name);
        } catch (IllegalArgumentException | NullPointerException ignored) {
            return WorkerJob.FARM;
        }
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
