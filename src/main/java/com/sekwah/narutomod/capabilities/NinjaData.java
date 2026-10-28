package com.sekwah.narutomod.capabilities;

import com.mojang.logging.LogUtils;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.utility.ChakraChargeAbility;
import com.sekwah.narutomod.capabilities.absorption.AbsorptionData;
import com.sekwah.narutomod.capabilities.disabledabilitydata.DisabledAbilityData;
import com.sekwah.narutomod.capabilities.mastery.MasteryData;
import com.sekwah.narutomod.capabilities.mastery.MeditationData;
import com.sekwah.narutomod.capabilities.skills.SkillData;
import com.sekwah.narutomod.capabilities.toggleabilitydata.ToggleAbilityData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.effect.NarutoEffects;
import com.sekwah.narutomod.events.GenjutsuEvents;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.item.ChakraBatteryItem;
import com.sekwah.narutomod.menu.SupplyChestContainer;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.skills.SkillEffect;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.sekclib.capabilitysync.capabilitysync.annotation.Sync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public class NinjaData implements INinjaData, ICapabilityProvider {

    private static final Logger LOGGER = LogUtils.getLogger();

    @Sync(minTicks = 1)
    private float chakra;

    @Sync(minTicks = 1)
    private float stamina;

    @Sync(minTicks = 1)
    private float substitutions;

    @Sync
    private float maxChakra;

    /**
     * Chakra capacity granted on top of the configured maximum, see
     * {@code INinjaData#getChakraCapacityBonus()}. Kept out of the save file: it is recomputed every
     * tick by whatever is handing it out (the meditation clones) and means nothing without them.
     */
    @Sync(minTicks = 1)
    private float chakraCapacityBonus;

    /**
     * Chakra capacity handed out by the armour the player is wearing, see
     * {@code events/NarutoArmorEvents}. Kept apart from {@link #chakraCapacityBonus} so the
     * meditation clones and the armour do not fight over one field, and out of the save file like it.
     */
    @Sync(minTicks = 1)
    private float armorChakraBonus;

    /**
     * Chakra capacity handed out by an open eye, see {@code RinneganAbility}. A third separate field
     * for the same reason the armour has one: the eye and the armour must be able to stop giving
     * their bonus without wiping the other's. Not saved, not synced for the HUD's sake but for the
     * bar's maximum, which the client draws from the same number.
     */
    @Sync(minTicks = 1)
    private float eyeChakraBonus;

    /**
     * Nature energy capacity handed out by an open eye, see {@code RinneganAbility}. Same rules as
     * {@link #eyeChakraBonus}.
     */
    @Sync(minTicks = 1)
    private float eyeSeninBonus;

    /**
     * The max health bonus the worn armour last wrote onto the player's attribute. Server side only,
     * used to tell whether the attribute modifier needs rebuilding.
     */
    private float armorHealthBonus;

    /**
     * Shield bought with chakra, see {@code ChakraShieldAbility}. Shown as armour points and spent
     * point for point as damage comes in, so it is really a second health bar the armour display
     * happens to render.
     */
    @Sync(minTicks = 1)
    private float shieldPoints;

    /**
     * Whether the second breath trick is armed. Synced so the jutsu menu can show the technique as
     * readied without the client having to guess from an effect that only appears on the killing
     * blow.
     */
    @Sync(minTicks = 1)
    private boolean secondBreathArmed;

    /**
     * How many of the eight inner gates are open. Server side only, the client sees the resulting
     * effects instead.
     */
    private int gateCount;

    /**
     * Fraction of a point of health the open gates have ground away but not yet been paid. The
     * backlash is charged in whole points of health, so the remainder is carried here between
     * drain ticks. Server side only and not written to disk, exactly like {@link #gateCount}.
     */
    private float gateBacklashCarry;

    /**
     * If the player should have access to all the ninja shit
     */
    @Sync(syncGlobally = true)
    private boolean ninjaModeEnabled;

    @Sync
    private float maxStamina;

    // Unless the player needs to know the max for rendering, no point in rendering.
    private float maxSubstitutions;

    @Sync
    private Vec3 substitutionLocation;

    @Sync
    private ResourceLocation substitutionDimension;

    /**
     * Where this player's chakra marker log stands. Kept server side only: nothing on the client
     * needs to know, and unlike the substitution marker it is written to disk so a return marker
     * survives logging out.
     */
    private Vec3 markerLocation;

    private ResourceLocation markerDimension;

    /**
     * The supply chest a worker clone deposits into, see {@code StorageChestAbility}. It is not a
     * block: it lives right here in the caster's own data, so a worker can reach it from anywhere and
     * it survives a logout. There is always exactly one.
     */
    private final SupplyChestContainer supplyChest = new SupplyChestContainer(SupplyChestContainer.MIN_SIZE);

    /**
     * Where a caster who walked into the Kamui pocket dimension is to come back out, see
     * {@code KamuiJutsuAbility}. Server side only and written to disk, so logging out inside the
     * void is not a one way trip.
     */
    private Vec3 kamuiReturnLocation;

    private ResourceLocation kamuiReturnDimension;

    /**
     * Nature energy gathered by standing still and burned by sage mode, see {@code SageModeAbility}.
     * Written to disk (a gathered pool should not evaporate on logout) and synced for the HUD.
     */
    @Sync(minTicks = 1)
    private float seninMana;

    /**
     * Whether sage mode is up. Synced for the HUD and the client side effects, and never written to
     * disk: death and logging out both end it.
     */
    @Sync(minTicks = 1)
    private boolean sageMode;

    @Sync(minTicks = 1)
    private int sixPathsOrbs;

    @Sync(minTicks = 20)
    private int sealVesselIntegrity;

    /**
     * Reach of the held chakra sense, or zero while it is not up. Synced because the client is what
     * draws the sense - the glow on creatures and the structure markers - and never written to disk:
     * it is a held state, gone the moment the keys are let go. See {@code ChakraSensingAbility}.
     */
    @Sync(minTicks = 1)
    private float sensingRadius;

    /**
     * Chakra funded night vision left, in ticks. Not written to disk, like {@link #sageMode}; it is
     * synced at a slow cadence so the jutsu menu can show the sensing row as active while the tap is
     * running, even though the effect itself is a vanilla one the client already sees.
     */
    @Sync(minTicks = 20)
    private int nightVisionTicks;

    /**
     * UUID of the player whose form the caster is wearing, or an empty string. Synced because the
     * client renderer is what turns it into a copied skin and name tag, and deliberately not written
     * to disk: logging out drops the disguise.
     */
    @Sync(minTicks = 1, syncGlobally = true)
    private String hengeTarget = "";

    /**
     * Registry id of the mob whose form the caster is wearing, or an empty string for a player (or no
     * disguise at all). Synced for the same reason as {@link #hengeTarget}: the client renderer is
     * the only thing that reads it.
     */
    @Sync(minTicks = 1, syncGlobally = true)
    private String hengeEntityType = "";

    /** Display name that goes with {@link #hengeTarget}, synced for the same reason. */
    @Sync(minTicks = 1, syncGlobally = true)
    private String hengeName = "";

    /** Ticks of disguise left, server side only. */
    private int hengeTicks;

    /**
     * If the player can double jump, will be updated by underlying values server side.
     *
     * Will be true if the player has enough chakra as well as
     */
    @Sync(minTicks = 1)
    private DoubleJumpData doubleJumpData;

    private boolean doubleJumpReady;

    /**
     * The current ability being charged/channeled
     * <p>
     * TODO make this global then expand the channeled logic to be able to handle any visual effects easier.
     * TODO possibly swap the type of this over to an Ability type so that it needs to be looked up less.
     */
    @Sync(minTicks = 1)
    private ResourceLocation currentlyChanneled;

    @Sync(minTicks = 1)
    private int ticksChanneled;

    @Sync(syncGlobally = true)
    private ToggleAbilityData toggleAbilityData;

    /**
     * Per-technique cast counts, the raw material mastery is worked out from, see
     * {@code abilities/mastery/Mastery}. Synced, because the jutsu menu draws a progress bar for
     * every technique from it.
     */
    @Sync(minTicks = 1)
    private MasteryData masteryData = new MasteryData();

    /**
     * The skill tree nodes this caster has bought with skill points, plus the points themselves.
     * Synced because the skill screen and the HUD both read it, and saved so neither is ever lost.
     */
    @Sync(minTicks = 1)
    private SkillData skillData = new SkillData();

    /**
     * Techniques switched off from the jutsu menu, see {@link DisabledAbilityData}. Synced, because
     * the menu greys the rows from it and skips the cast packet, and saved, because a technique the
     * player deliberately put aside should stay aside after a relog.
     */
    @Sync(minTicks = 1)
    private DisabledAbilityData disabledAbilityData = new DisabledAbilityData();

    /**
     * Whether clicks in the jutsu menu may cast any technique, not only the ones that stay on, see
     * {@code MenuClickCommand}. Off by default: the menu is a reference, and a stray click firing a
     * one-shot cast is a nuisance. Synced so the menu itself knows which rows it may mark as
     * clickable, and saved so a player who asked for it keeps it after a relog.
     */
    @Sync(minTicks = 1)
    private boolean menuClickAll;

    /**
     * Chakra swallowed through the palm Rinnegan and not yet spent, per nature. Synced for the HUD
     * and never written to disk: it is a held stance, and dying or logging out drops the stance.
     */
    @Sync(minTicks = 1)
    private AbsorptionData absorptionData = new AbsorptionData();

    /**
     * Bosses whose one-time milestone reward this ninja has already collected, by short id. Kept
     * server side and saved so a milestone cannot be farmed by killing the same boss again.
     */
    private final Set<String> claimedBossMilestones = new HashSet<>();

    /** The ninja's rank id, see {@code ranks/NinjaRank}. Server side only, saved with the rest. */
    private String ninjaRank = "";

    /** The element the ninja was born to, see {@code abilities/Nature}; empty before the choice. */
    @Sync
    private String natureAffinity = "";

    /**
     * Whether the starting handbook has already been handed out. Ninja mode can be switched off and
     * on again, so "became a ninja just now" is not enough to decide: without a flag of its own the
     * awakening path would give a second handbook on every toggle, which used to be a free book
     * printer. See {@code ServerToggleNinjaPacket}.
     */
    private boolean handbookGiven;

    /**
     * The standing meditating clones of this caster, see {@code MeditationData}. Server side only,
     * deliberately neither synced nor saved: a caster who dies or logs out keeps no meditations.
     */
    private final MeditationData meditationData = new MeditationData();


    /**
     * Depending on what's going might want to swap to a potion effect.
     * This will make the player truly invisible.
     */
    @Sync(minTicks = 1, syncGlobally = true)
    private boolean isInvisible = false;

    private int invisibleTicks = 0;


    private ArrayList<DelayedPlayerTickEvent> delayedTickEvents = new ArrayList<>();
    // CooldownTickEvent to follow the same style you have for DelayedPlayerTickEvent
    private HashMap<String, CooldownTickEvent> cooldownTickEvents = new HashMap<>();

    public NinjaData(boolean isServer) {
        if (isServer) {
            this.getConfigData();
            this.stamina = this.maxStamina;
            this.chakra = this.maxChakra;
        }
        this.toggleAbilityData = new ToggleAbilityData();
        this.doubleJumpData = new DoubleJumpData(false);
    }

    static class RegenInfo {
        public int cooldown;

        public RegenInfo() {
        }

        /**
         * Tick down when checked
         *
         * @return if regen should take place
         */
        public boolean canRegen() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            return true;
        }
    }

    private final RegenInfo chakraRegenInfo = new RegenInfo();
    private final RegenInfo staminaRegenInfo = new RegenInfo();

    private static final String CHAKRA_TAG = "chakra";
    private static final String STAMINA_TAG = "stamina";
    private static final String NINJA_MODE_ENABLED = "ninjaModeEnabled";
    private static final String SAVE_TIME = "save_time";
    private static final String COOLDOWN_TAG = "cooldowns";
    private static final String SUBSTITUTION_TAG = "substitutions";
    private static final String MARKER_TAG = "chakraMarker";
    private static final String MARKER_DIMENSION_TAG = "chakraMarkerDimension";
    private static final String SUPPLY_CHEST_TAG = "supplyChest";
    private static final String KAMUI_RETURN_TAG = "kamuiReturn";
    private static final String KAMUI_RETURN_DIMENSION_TAG = "kamuiReturnDimension";
    private static final String SENIN_MANA_TAG = "seninMana";
    /**
     * The bought chakra shield and the armed second breath live in the bar rather than in a block or
     * an effect, so they have to be written down: a caster who spent the whole bar on the shield must
     * not find it gone after a relogin, and the same goes for a readied second breath.
     */
    private static final String SHIELD_TAG = "shieldPoints";
    private static final String SECOND_BREATH_TAG = "secondBreathArmed";
    private static final String MASTERY_TAG = "mastery";
    private static final String SKILLS_TAG = "skills";
    private static final String SKILL_POINTS_TAG = "skillPoints";
    private static final String SCROLLS_TAG = "scrolls";
    private static final String BOSS_MILESTONES_TAG = "bossMilestones";
    private static final String NINJA_RANK_TAG = "ninjaRank";
    private static final String NATURE_AFFINITY_TAG = "natureAffinity";
    private static final String HANDBOOK_GIVEN_TAG = "handbookGiven";
    private static final String DISABLED_ABILITIES_TAG = "disabledAbilities";
    private static final String MENU_CLICK_ALL_TAG = "menuClickAll";

    /** Below this share of the pool the caster is running on fumes and moves slower. */
    private static final float EXHAUSTION_THRESHOLD = 0.15f;

    /** Short and refreshed every tick, so it drops the moment the pool recovers. */
    private static final int EXHAUSTION_TICKS = 40;

    /** How much faster the passive trickle runs while the chakra flow effect is up. */
    private static final float CHAKRA_FLOW_MULTIPLIER = 4.0f;

    /**
     * Creative casts are not free, they are fast: the pool still empties, it just comes back ten
     * times quicker. That keeps creative mode useful for testing a technique's cost without making
     * every bar meaningless.
     */
    private static final float CREATIVE_REGEN_MULTIPLIER = 10.0f;    private final LazyOptional<INinjaData> holder = LazyOptional.of(() -> this);

    @Override
    public float getChakra() {
        return this.chakra;
    }

    @Override
    public float getMaxChakra() {
        return this.maxChakra + this.chakraCapacityBonus + this.armorChakraBonus + this.eyeChakraBonus
                + ChakraChargeAbility.masteryChakraBonus(this)
                + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.MAX_CHAKRA)
                + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.ALL_ROUNDED);
    }

    @Override
    public float getChakraCapacityBonus() {
        return this.chakraCapacityBonus;
    }

    @Override
    public void setChakraCapacityBonus(float bonus) {
        this.chakraCapacityBonus = bonus;
    }

    @Override
    public float getArmorChakraBonus() {
        return this.armorChakraBonus;
    }

    @Override
    public void setArmorChakraBonus(float bonus) {
        this.armorChakraBonus = bonus;
    }

    @Override
    public float getArmorHealthBonus() {
        return this.armorHealthBonus;
    }

    @Override
    public void setArmorHealthBonus(float bonus) {
        this.armorHealthBonus = bonus;
    }

    @Override
    public float getEyeChakraBonus() {
        return this.eyeChakraBonus;
    }

    @Override
    public void setEyeChakraBonus(float bonus) {
        this.eyeChakraBonus = bonus;
    }

    @Override
    public float getEyeSeninBonus() {
        return this.eyeSeninBonus;
    }

    @Override
    public void setEyeSeninBonus(float bonus) {
        this.eyeSeninBonus = bonus;
    }

    @Override
    public float getShieldPoints() {
        return this.shieldPoints;
    }

    @Override
    public void setShieldPoints(float points) {
        this.shieldPoints = points;
    }

    @Override
    public boolean isSecondBreathArmed() {
        return this.secondBreathArmed;
    }

    @Override
    public void setSecondBreathArmed(boolean armed) {
        this.secondBreathArmed = armed;
    }

    @Override
    public int getGateCount() {
        return this.gateCount;
    }

    @Override
    public void setGateCount(int gates) {
        this.gateCount = gates;
    }

    @Override
    public float getGateBacklashCarry() {
        return this.gateBacklashCarry;
    }

    @Override
    public void setGateBacklashCarry(float carry) {
        this.gateBacklashCarry = carry;
    }

    @Override
    public float getStamina() {
        return this.stamina;
    }

    @Override
    public float getSubstitutionCount() {
        return this.substitutions;
    }

    @Override
    public float getMaxSubstitutions() {
        return this.maxSubstitutions
                + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.MAX_SUBSTITUTIONS);
    }

    @Override
    public float getMaxStamina() {
        return this.maxStamina
                + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.MAX_STAMINA)
                + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.ALL_ROUNDED);
    }

    @Override
    public void setChakra(float chakra) {
        this.chakra = chakra;
    }

    @Override
    public void setStamina(float stamina) {
        this.stamina = stamina;
    }

    @Override
    public void useChakra(float amount, int cooldown) {
        this.chakra -= amount;
        this.chakraRegenInfo.cooldown = Math.max(cooldown, this.chakraRegenInfo.cooldown);
    }

    /** Extra stamina every action costs, from the training weights the body is carrying. */
    private float staminaCostMultiplier = 1.0f;

    @Override
    public void useStamina(float amount, int cooldown) {
        this.stamina -= amount * this.staminaCostMultiplier;
        this.staminaRegenInfo.cooldown = Math.max(cooldown, this.staminaRegenInfo.cooldown);
    }

    @Override
    public void useSubstitution(float amount) {
        this.substitutions -= amount;
    }

    @Override
    public void addChakra(float amount) {
        this.chakra = Math.min(Math.max(this.chakra + amount, 0), this.getMaxChakra());
    }

    @Override
    public void addStamina(float amount) {
        this.stamina = Math.min(Math.max(this.stamina + amount, 0), this.getMaxStamina());
    }

    @Override
    public void setInvisibleTicks(int ticks) {
        this.invisibleTicks = ticks;
    }

    @Override
    public boolean getInvisible() {
        return this.isInvisible;
    }

    @Override
    public Vec3 getSubstitutionLoc() {
        return this.substitutionLocation;
    }

    @Override
    public ResourceLocation getSubstitutionDimension() {
        return this.substitutionDimension;
    }

    @Override
    public void setSubstitutionLoc(Vec3 loc, ResourceLocation dimension) {
        this.substitutionLocation = loc;
        this.substitutionDimension = dimension;
    }

    @Override
    public Vec3 getMarkerLoc() {
        return this.markerLocation;
    }

    @Override
    public ResourceLocation getMarkerDimension() {
        return this.markerDimension;
    }

    @Override
    public void setMarkerLoc(Vec3 loc, ResourceLocation dimension) {
        this.markerLocation = loc;
        this.markerDimension = dimension;
    }

    @Override
    public SupplyChestContainer getSupplyChest() {
        return this.supplyChest;
    }

    @Override
    public Vec3 getKamuiReturnLoc() {
        return this.kamuiReturnLocation;
    }

    @Override
    public ResourceLocation getKamuiReturnDimension() {
        return this.kamuiReturnDimension;
    }

    @Override
    public void setKamuiReturnLoc(Vec3 loc, ResourceLocation dimension) {
        this.kamuiReturnLocation = loc;
        this.kamuiReturnDimension = dimension;
    }

    @Override
    public float getSeninMana() {
        return this.seninMana;
    }

    @Override
    public void setSeninMana(float mana) {
        this.seninMana = Math.min(Math.max(mana, 0.0f), this.getMaxSeninMana());
    }

    @Override
    public void addSeninMana(float mana) {
        this.setSeninMana(this.seninMana + mana);
    }

    @Override
    public float getMaxSeninMana() {
        // Nature energy is learned, not innate: with no gathering technique there is no pool, so a
        // fresh ninja has no senin bar and cannot stock nature energy before buying the sage branch.
        if (!AbilityUnlocks.hasSenjutsu(this)) {
            return 0.0f;
        }
        return NarutoConfig.maxSeninMana + this.eyeSeninBonus
                + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.SENIN_MANA);
    }

    @Override
    public boolean isSageMode() {
        return this.sageMode;
    }

    @Override
    public void setSageMode(boolean sage) {
        this.sageMode = sage;
    }

    @Override
    public int getSixPathsOrbs() {
        return this.sixPathsOrbs;
    }

    @Override
    public void setSixPathsOrbs(int orbs) {
        this.sixPathsOrbs = orbs;
    }

    @Override
    public int getSealVesselIntegrity() {
        return this.sealVesselIntegrity;
    }

    @Override
    public void setSealVesselIntegrity(int integrity) {
        this.sealVesselIntegrity = integrity;
    }

    @Override
    public float getSensingRadius() {
        return this.sensingRadius;
    }

    @Override
    public void setSensingRadius(float radius) {
        this.sensingRadius = Math.max(radius, 0f);
    }

    @Override
    public int getNightVisionTicks() {
        return this.nightVisionTicks;
    }

    @Override
    public void setNightVisionTicks(int ticks) {
        this.nightVisionTicks = Math.max(ticks, 0);
    }

    @Override
    public String getHengeTarget() {
        return this.hengeTarget == null ? "" : this.hengeTarget;
    }

    @Override
    public void setHengeTarget(String uuid) {
        this.hengeTarget = uuid == null ? "" : uuid;
    }

    @Override
    public String getHengeEntityType() {
        return this.hengeEntityType == null ? "" : this.hengeEntityType;
    }

    @Override
    public void setHengeEntityType(String entityType) {
        this.hengeEntityType = entityType == null ? "" : entityType;
    }

    @Override
    public String getHengeName() {
        return this.hengeName == null ? "" : this.hengeName;
    }

    @Override
    public void setHengeName(String name) {
        this.hengeName = name == null ? "" : name;
    }

    @Override
    public int getHengeTicks() {
        return this.hengeTicks;
    }

    @Override
    public void setHengeTicks(int ticks) {
        this.hengeTicks = Math.max(ticks, 0);
    }

    @Override
    public boolean isHenged() {
        return this.hengeTicks > 0
                && (!this.getHengeTarget().isEmpty() || !this.getHengeEntityType().isEmpty());
    }

    @Override
    public DoubleJumpData getDoubleJumpData() {
        if (this.doubleJumpData == null) {
            this.doubleJumpData = new DoubleJumpData(false);
        }
        return this.doubleJumpData;
    }

    @Override
    public ResourceLocation getCurrentlyChanneledAbility() {
        return this.currentlyChanneled;
    }

    @Override
    public int getCurrentlyChanneledTicks() {
        return this.ticksChanneled;
    }

    @Override
    public void setCurrentlyChanneledAbility(Player player, Ability ability) {
        if (ability != null) {
            if (ability.castingSound() != null) {
                player.level().playSound(null, player, ability.castingSound(), SoundSource.PLAYERS, 0.5f, 1.0f);
            }
            ability.emitNoise(player);

            if(!(ability instanceof Ability.Channeled channeled && channeled.hideChannelMessages())) {
                if (ability instanceof Ability.Channeled channeled && channeled.useChargedMessages()) {
                    player.displayClientMessage(Component.translatable("jutsu.charge.start", Component.translatable(ability.getTranslationKey(this, 1)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GREEN), true);
                } else {
                    player.displayClientMessage(Component.translatable("jutsu.channel.start", Component.translatable(ability.getTranslationKey(this, 1)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GREEN), true);
                }
            }

            NarutoRegistries.ABILITIES.getResourceKey(ability)
                    .ifPresent(abilityResourceKey -> this.currentlyChanneled = abilityResourceKey.location());
        } else {
            if (this.currentlyChanneled != null) {
                Ability currentAbility = NarutoRegistries.ABILITIES.getValue(this.currentlyChanneled);
                if( currentAbility != null) {
                    if(!(currentAbility instanceof Ability.Channeled channeled && channeled.hideChannelMessages())) {
                        if (currentAbility instanceof Ability.Channeled channeled && channeled.useChargedMessages()) {
                            player.displayClientMessage(Component.translatable("jutsu.cast", Component.translatable(currentAbility.getTranslationKey(this, this.ticksChanneled - 1)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.GREEN), true);
                        } else {
                            player.displayClientMessage(Component.translatable("jutsu.channel.stop", Component.translatable(currentAbility.getTranslationKey(this)).withStyle(ChatFormatting.YELLOW)).withStyle(ChatFormatting.RED), true);
                        }
                    }
                }
            }
            this.currentlyChanneled = null;
        }
        this.ticksChanneled = 0;
    }

    /*
     * The nested data below is contractually never null, and every getter repairs a missing object
     * instead of handing it out.
     *
     * sekclib replays a player's globally synced fields to whoever starts tracking them, and it
     * replays the raw tracker values - a tracker that has not ticked yet sends "no data", which the
     * packet spells as null. The client handler then writes that null straight into the field through
     * a method handle, so a player who logs in during the same tick another player starts watching
     * them arrives with these objects wiped. A null here used to fall straight into a renderer and
     * take the game down (see SixPathsOrbRenderer). Starting from an empty object is safe: the next
     * real update of the field replaces it.
     */

    @Override
    public ToggleAbilityData getToggleAbilityData() {
        if (this.toggleAbilityData == null) {
            this.toggleAbilityData = new ToggleAbilityData();
        }
        return this.toggleAbilityData;
    }

    @Override
    public MasteryData getMasteryData() {
        if (this.masteryData == null) {
            this.masteryData = new MasteryData();
        }
        return this.masteryData;
    }

    @Override
    public SkillData getSkillData() {
        if (this.skillData == null) {
            this.skillData = new SkillData();
        }
        return this.skillData;
    }

    @Override
    public AbsorptionData getAbsorptionData() {
        if (this.absorptionData == null) {
            this.absorptionData = new AbsorptionData();
        }
        return this.absorptionData;
    }

    @Override
    public DisabledAbilityData getDisabledAbilityData() {
        if (this.disabledAbilityData == null) {
            this.disabledAbilityData = new DisabledAbilityData();
        }
        return this.disabledAbilityData;
    }

    @Override
    public boolean isAbilityDisabled(ResourceLocation abilityId) {
        if (abilityId == null || !this.disabledAbilityData.contains(abilityId)) {
            return false;
        }
        // Only techniques the menu controls can be put aside. A save from an older build may list
        // anything, and a stale entry must not silently block a fireball.
        Ability ability = NarutoRegistries.ABILITIES.getValue(abilityId);
        return ability != null && ability.isMenuControllable();
    }

    @Override
    public boolean isMenuClickAll() {
        return this.menuClickAll;
    }

    @Override
    public void setMenuClickAll(boolean enabled) {
        this.menuClickAll = enabled;
    }

    @Override
    public boolean hasClaimedBossMilestone(String bossId) {
        return this.claimedBossMilestones.contains(bossId);
    }

    @Override
    public void claimBossMilestone(String bossId) {
        this.claimedBossMilestones.add(bossId);
    }

    @Override
    public String getNinjaRank() {
        return this.ninjaRank;
    }

    @Override
    public void setNinjaRank(String rank) {
        this.ninjaRank = rank == null ? "" : rank;
    }

    @Override
    public String getNatureAffinity() {
        return this.natureAffinity;
    }

    @Override
    public void setNatureAffinity(String affinity) {
        this.natureAffinity = affinity == null ? "" : affinity;
    }

    @Override
    public boolean isHandbookGiven() {
        return this.handbookGiven;
    }

    @Override
    public void setHandbookGiven(boolean given) {
        this.handbookGiven = given;
    }

    @Override
    public MeditationData getMeditationData() {
        return this.meditationData;
    }

    /**
     * The soft price of an empty pool: below {@link #EXHAUSTION_THRESHOLD} the caster moves a little
     * slower. It is refreshed every tick and removes itself as soon as the pool is back above the
     * line, so it never becomes a death spiral, and it is never applied in creative or spectator.
     */
    private void updateExhaustion(Player player) {
        boolean exhausted = NarutoConfig.chakraExhaustionEnabled
                && this.chakra < this.getMaxChakra() * EXHAUSTION_THRESHOLD
                && !player.isCreative() && !player.isSpectator();
        if (exhausted) {
            player.addEffect(new MobEffectInstance(NarutoEffects.CHAKRA_EXHAUSTION.get(),
                    EXHAUSTION_TICKS, 0, true, false));
        } else {
            player.removeEffect(NarutoEffects.CHAKRA_EXHAUSTION.get());
        }
    }

    @Override
    public void updateDataServer(Player player) {
        if(this.invisibleTicks > 0) {
            this.invisibleTicks--;
            this.isInvisible = true;
        } else {
            this.isInvisible = false;
        }

        if(!this.isNinjaModeEnabled()) {
            return;
        }

        this.getConfigData();
        Iterator<DelayedPlayerTickEvent> iterator = this.delayedTickEvents.iterator();
        while (iterator.hasNext()) {
            DelayedPlayerTickEvent event = iterator.next();
            event.tick();
            if (event.shouldRun()) {
                event.run(player);
                iterator.remove();
            }
        }

        // Compile list of keys from cooldown map
        ArrayList<String> completeList = new ArrayList<>(cooldownTickEvents.keySet());
        //  loop through to tick and then remove cooldown if complete
        for (String name : completeList) {
            CooldownTickEvent event = cooldownTickEvents.get(name);
            event.tick();
            if (event.isComplete()) {
                cooldownTickEvents.remove(name);
            }
        }

        this.staminaCostMultiplier = com.sekwah.narutomod.item.TrainingWeightsItem
                .staminaMultiplier(player);
        // A sage's body is held up by nature: everything costs half the stamina it should.
        if (this.isSageMode() && NarutoConfig.sageBuffsEnabled) {
            this.staminaCostMultiplier *= 0.5f;
        }
        if (this.staminaRegenInfo.canRegen()) {
            // Open gates burn stamina faster than it can be made back: while any gate is open the
            // bar only goes down, which is the price of the stance.
            if (this.getGateCount() <= 0) {
                float staminaRegen = NarutoConfig.staminaRegen
                        + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.STAMINA_REGEN) / 20.0f;
                // Running dry leaves the body winded for a few seconds: the bar comes back at half rate,
                // which is what makes spending the last point a decision rather than a formality.
                if (this.stamina <= 0.0f) {
                    player.getPersistentData().putLong("narutomod:winded_until",
                            player.level().getGameTime() + 100L);
                }
                if (player.getPersistentData().getLong("narutomod:winded_until")
                        > player.level().getGameTime()) {
                    staminaRegen *= 0.5f;
                }
                // Weight is real: every point of armour takes a little off the recovery. A sage
                // does not feel the weight - nature carries it - so the penalty is waived in sage mode.
                if (!(this.isSageMode() && NarutoConfig.sageBuffsEnabled)) {
                    staminaRegen *= 1.0f - Math.min(0.3f, player.getArmorValue() * 0.01f);
                }
                if (player.isCreative()) {
                    staminaRegen *= CREATIVE_REGEN_MULTIPLIER;
                }
                this.stamina += staminaRegen;
            }
        }
        if (this.chakraRegenInfo.canRegen()
                && player.getPersistentData().getLong("narutomod:node_rupture_until")
                <= player.level().getGameTime()
                && !GenjutsuEvents.isUnderGenjutsu(player)) {
            // Open gates burn chakra faster than it can be made back: while any gate is open the
            // pool only goes down, which is the price of the stance.
            if (this.getGateCount() <= 0) {
                float regen = NarutoConfig.chakraRegen
                        + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.CHAKRA_REGEN) / 20.0f;
                // A freshly filled pool keeps trickling in faster for a while, which is what makes
                // topping the bar up before a fight worth the time, see ChakraChargeAbility.
                if (player.hasEffect(NarutoEffects.CHAKRA_FLOW.get()) && NarutoConfig.chakraFlowEnabled) {
                    regen *= CHAKRA_FLOW_MULTIPLIER;
                }
                // Creative is faster rather than free, see CREATIVE_REGEN_MULTIPLIER.
                if (player.isCreative()) {
                    regen *= CREATIVE_REGEN_MULTIPLIER;
                }
                this.chakra += regen;
            }
        }
        this.substitutions += NarutoConfig.substitutionRegenRate
                * (1.0f + SkillTree.bonus(this.skillData.getUnlocked(), SkillEffect.SUBSTITUTION_REGEN));
        this.substitutions = Math.min(Math.max(this.substitutions, 0), this.getMaxSubstitutions());
        this.stamina = Math.min(Math.max(this.stamina, 0), this.getMaxStamina());
        this.chakra = Math.min(Math.max(this.chakra, 0), this.getMaxChakra());
        this.updateExhaustion(player);

        if (this.currentlyChanneled != null) {
            Ability ability = NarutoRegistries.ABILITIES.getValue(this.currentlyChanneled);
            if (ability != null && ability.activationType() == Ability.ActivationType.CHANNELED) {
                // A cast held open is paid for tick by tick too, so it also gets to run on battery
                // power before its upkeep check decides the caster cannot afford it any more.
                ChakraBatteryItem.fuelUpkeep(player, this);
                if(ability.handleCost(player, this, this.ticksChanneled)) {
                    if (ability instanceof Ability.Channeled channeled) {
                        channeled.handleChannelling(player, this, this.ticksChanneled);
                    }
                } else {
                    // A channel that could not even pay its first tick must end quietly: calling
                    // performServer(0) here would hand the caster the release effect (a free earth
                    // wall, a free rasengan hit) for a cast the server just refused, and leaving the
                    // channel set would keep re-checking it every tick.
                    if (this.ticksChanneled > 0) {
                        int finalTicksChanneled = this.ticksChanneled - 1;
                        ability.performServer(player, this, finalTicksChanneled);
                    }
                    this.setCurrentlyChanneledAbility(player, null);
                }
            } else {
                LOGGER.error("Somehow non channeled ability has been set to ninja data {}", this.currentlyChanneled);
            }
            this.ticksChanneled++;
        }

        if(player.onGround()) {
            this.doubleJumpData.canDoubleJumpServer = true;
        }
    }

    private void getConfigData() {
        this.maxChakra = NarutoConfig.maxChakra;
        this.maxStamina = NarutoConfig.maxStamina;
        this.maxSubstitutions = NarutoConfig.maxSubstitutions;
    }

    @Override
    public void scheduleDelayedTickEvent(Consumer<Player> consumer, int tickDelay) {
        this.delayedTickEvents.add(new DelayedPlayerTickEvent(consumer, tickDelay));
    }

    @Override
    public void updateDataClient(Player player) {
        this.doubleJumpData.stuckCheck();
    }

    @Override
    public void setIsNinja(boolean enableNinja) {
        this.ninjaModeEnabled = enableNinja;
    }

    @Override
    public boolean isNinjaModeEnabled() {
        return this.ninjaModeEnabled;
    }

    @Override
    public Tag serializeNBT() {
        final CompoundTag nbt = new CompoundTag();
        nbt.putFloat(CHAKRA_TAG, this.chakra);
        nbt.putFloat(STAMINA_TAG, this.stamina);
        nbt.putBoolean(NINJA_MODE_ENABLED, this.ninjaModeEnabled);
        long currentTime = System.currentTimeMillis();
        nbt.putLong(SAVE_TIME, currentTime);
        final CompoundTag cooldownData = new CompoundTag();
        for (String key : this.cooldownTickEvents.keySet()) {
            CooldownTickEvent event = this.cooldownTickEvents.get(key);
            cooldownData.putInt(key, event.ticks);
        }
        nbt.put(COOLDOWN_TAG, cooldownData);
        nbt.putFloat(SUBSTITUTION_TAG, this.substitutions);
        if (this.markerLocation != null) {
            CompoundTag marker = new CompoundTag();
            marker.putDouble("x", this.markerLocation.x);
            marker.putDouble("y", this.markerLocation.y);
            marker.putDouble("z", this.markerLocation.z);
            nbt.put(MARKER_TAG, marker);
        }
        if (this.markerDimension != null) {
            nbt.putString(MARKER_DIMENSION_TAG, this.markerDimension.toString());
        }
        CompoundTag supplyChestTag = new CompoundTag();
        this.supplyChest.save(supplyChestTag);
        nbt.put(SUPPLY_CHEST_TAG, supplyChestTag);
        if (this.kamuiReturnLocation != null) {
            CompoundTag kamui = new CompoundTag();
            kamui.putDouble("x", this.kamuiReturnLocation.x);
            kamui.putDouble("y", this.kamuiReturnLocation.y);
            kamui.putDouble("z", this.kamuiReturnLocation.z);
            nbt.put(KAMUI_RETURN_TAG, kamui);
        }
        if (this.kamuiReturnDimension != null) {
            nbt.putString(KAMUI_RETURN_DIMENSION_TAG, this.kamuiReturnDimension.toString());
        }
        nbt.putFloat(SENIN_MANA_TAG, this.seninMana);
        nbt.putFloat(SHIELD_TAG, this.shieldPoints);
        nbt.putBoolean(SECOND_BREATH_TAG, this.secondBreathArmed);
        CompoundTag masteryTag = new CompoundTag();
        this.masteryData.getCasts().forEach((ability, casts) -> masteryTag.putInt(ability.toString(), casts));
        nbt.put(MASTERY_TAG, masteryTag);
        CompoundTag skillTag = new CompoundTag();
        for (ResourceLocation skill : this.skillData.getUnlocked()) {
            skillTag.putBoolean(skill.toString(), true);
        }
        nbt.put(SKILLS_TAG, skillTag);
        nbt.putInt(SKILL_POINTS_TAG, this.skillData.getPoints());
        CompoundTag scrollTag = new CompoundTag();
        for (ResourceLocation knowledge : this.skillData.getScrolls()) {
            scrollTag.putBoolean(knowledge.toString(), true);
        }
        nbt.put(SCROLLS_TAG, scrollTag);
        CompoundTag bossTag = new CompoundTag();
        for (String boss : this.claimedBossMilestones) {
            bossTag.putBoolean(boss, true);
        }
        nbt.put(BOSS_MILESTONES_TAG, bossTag);
        nbt.putString(NINJA_RANK_TAG, this.ninjaRank);
        nbt.putString(NATURE_AFFINITY_TAG, this.natureAffinity);
        nbt.putBoolean(HANDBOOK_GIVEN_TAG, this.handbookGiven);
        CompoundTag disabledTag = new CompoundTag();
        for (ResourceLocation id : this.disabledAbilityData.getAbilities()) {
            disabledTag.putBoolean(id.toString(), true);
        }
        nbt.put(DISABLED_ABILITIES_TAG, disabledTag);
        nbt.putBoolean(MENU_CLICK_ALL_TAG, this.menuClickAll);
        return nbt;
    }

    @Override
    public void deserializeNBT(Tag tag) {
        if (tag instanceof CompoundTag compoundTag) {
            long currentTime = System.currentTimeMillis();
            long saveTime = compoundTag.getLong(SAVE_TIME);
            int ticksPassed = Math.max((int) ((currentTime - saveTime) / 1000 * 20), 0);
            this.chakra = compoundTag.getFloat(CHAKRA_TAG);
            this.stamina = compoundTag.getFloat(STAMINA_TAG);
            this.ninjaModeEnabled = compoundTag.getBoolean(NINJA_MODE_ENABLED);
            CompoundTag cooldownData = compoundTag.getCompound(COOLDOWN_TAG);
            for (String key : cooldownData.getAllKeys()) {
                this.cooldownTickEvents.put(key, new CooldownTickEvent(cooldownData.getInt(key) - ticksPassed));
            }
            this.substitutions = compoundTag.getFloat(SUBSTITUTION_TAG);
            if (compoundTag.contains(MARKER_TAG)) {
                CompoundTag marker = compoundTag.getCompound(MARKER_TAG);
                this.markerLocation = new Vec3(marker.getDouble("x"), marker.getDouble("y"),
                        marker.getDouble("z"));
            }
            if (compoundTag.contains(MARKER_DIMENSION_TAG)) {
                this.markerDimension = new ResourceLocation(compoundTag.getString(MARKER_DIMENSION_TAG));
            }
            Tag supplyChestTag = compoundTag.get(SUPPLY_CHEST_TAG);
            if (supplyChestTag instanceof CompoundTag supplyChestCompound) {
                this.supplyChest.load(supplyChestCompound);
            } else if (supplyChestTag instanceof ListTag supplyChestList) {
                // Contents written before the chest grew amounts, still a plain item list.
                this.supplyChest.loadLegacy(supplyChestList);
            }
            if (compoundTag.contains(KAMUI_RETURN_TAG)) {
                CompoundTag kamui = compoundTag.getCompound(KAMUI_RETURN_TAG);
                this.kamuiReturnLocation = new Vec3(kamui.getDouble("x"), kamui.getDouble("y"),
                        kamui.getDouble("z"));
            }
            if (compoundTag.contains(KAMUI_RETURN_DIMENSION_TAG)) {
                this.kamuiReturnDimension = new ResourceLocation(compoundTag.getString(KAMUI_RETURN_DIMENSION_TAG));
            }
            this.seninMana = compoundTag.getFloat(SENIN_MANA_TAG);
            this.shieldPoints = compoundTag.getFloat(SHIELD_TAG);
            this.secondBreathArmed = compoundTag.getBoolean(SECOND_BREATH_TAG);
            CompoundTag masteryTag = compoundTag.getCompound(MASTERY_TAG);
            // Counts are authoritative, so a load replaces whatever was there rather than adding to it.
            this.masteryData.getCasts().clear();
            for (String key : masteryTag.getAllKeys()) {
                ResourceLocation ability = ResourceLocation.tryParse(key);
                if (ability != null) {
                    this.masteryData.setCasts(ability, masteryTag.getInt(key));
                }
            }
            CompoundTag skillTag = compoundTag.getCompound(SKILLS_TAG);
            // Learned nodes are authoritative too, so a load replaces the set.
            this.skillData.reset();
            for (String key : skillTag.getAllKeys()) {
                ResourceLocation skill = ResourceLocation.tryParse(key);
                if (skill != null) {
                    this.skillData.unlock(skill);
                }
            }
            this.skillData.addPoints(compoundTag.getInt(SKILL_POINTS_TAG));
            CompoundTag scrollTag = compoundTag.getCompound(SCROLLS_TAG);
            for (String key : scrollTag.getAllKeys()) {
                ResourceLocation knowledge = ResourceLocation.tryParse(key);
                if (knowledge != null) {
                    this.skillData.learnScroll(knowledge);
                }
            }
            // Clamped once the learned nodes are back: without the gathering technique the pool is
            // zero, so a value saved by an older build cannot linger as a bar a fresh ninja owns.
            this.setSeninMana(this.seninMana);
            this.claimedBossMilestones.clear();
            CompoundTag bossTag = compoundTag.getCompound(BOSS_MILESTONES_TAG);
            for (String boss : bossTag.getAllKeys()) {
                this.claimedBossMilestones.add(boss);
            }
            this.ninjaRank = compoundTag.getString(NINJA_RANK_TAG);
            this.natureAffinity = compoundTag.getString(NATURE_AFFINITY_TAG);
            this.handbookGiven = compoundTag.getBoolean(HANDBOOK_GIVEN_TAG);
            this.menuClickAll = compoundTag.getBoolean(MENU_CLICK_ALL_TAG);
            this.disabledAbilityData.getAbilities().clear();
            CompoundTag disabledTag = compoundTag.getCompound(DISABLED_ABILITIES_TAG);
            for (String key : disabledTag.getAllKeys()) {
                ResourceLocation id = ResourceLocation.tryParse(key);
                if (id != null) {
                    this.disabledAbilityData.getAbilities().add(id);
                }
            }
            // Older builds let any technique be switched off. Only the ones the menu controls are
            // honoured now, so anything else is dropped instead of lingering as a dead entry.
            this.disabledAbilityData.getAbilities().removeIf(id -> {
                Ability ability = NarutoRegistries.ABILITIES.getValue(id);
                return ability == null || !ability.isMenuControllable();
            });
        }
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        return NinjaCapabilityHandler.NINJA_DATA.orEmpty(cap, holder);
    }

    @Override
    public HashMap<String, CooldownTickEvent> getCooldownEvents() {
        return cooldownTickEvents;
    }
}
