package com.sekwah.narutomod.capabilities;

import com.mojang.logging.LogUtils;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.toggleabilitydata.ToggleAbilityData;
import com.sekwah.narutomod.config.NarutoConfig;
import com.sekwah.narutomod.gameevents.NarutoGameEvents;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.sekclib.capabilitysync.capabilitysync.annotation.Sync;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
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
import java.util.Iterator;
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
     * Whether the second breath trick is armed. Server side only, the client has no use for it.
     */
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

    /**
     * UUID of the player whose form the caster is wearing, or an empty string. Synced because the
     * client renderer is what turns it into a copied skin and name tag, and deliberately not written
     * to disk: logging out drops the disguise.
     */
    @Sync(minTicks = 1)
    private String hengeTarget = "";

    /**
     * Registry id of the mob whose form the caster is wearing, or an empty string for a player (or no
     * disguise at all). Synced for the same reason as {@link #hengeTarget}: the client renderer is
     * the only thing that reads it.
     */
    @Sync(minTicks = 1)
    private String hengeEntityType = "";

    /** Display name that goes with {@link #hengeTarget}, synced for the same reason. */
    @Sync(minTicks = 1)
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

    @Sync
    private ToggleAbilityData toggleAbilityData;


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
            this.stamina = this.maxChakra;
            this.chakra = this.maxStamina;
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
    private static final String KAMUI_RETURN_TAG = "kamuiReturn";
    private static final String KAMUI_RETURN_DIMENSION_TAG = "kamuiReturnDimension";
    private static final String SENIN_MANA_TAG = "seninMana";

    private final LazyOptional<INinjaData> holder = LazyOptional.of(() -> this);

    @Override
    public float getChakra() {
        return this.chakra;
    }

    @Override
    public float getMaxChakra() {
        return this.maxChakra + this.chakraCapacityBonus + this.armorChakraBonus;
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
    public float getMaxStamina() {
        return this.maxStamina;
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

    @Override
    public void useStamina(float amount, int cooldown) {
        this.stamina -= amount;
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
        this.stamina = Math.min(Math.max(this.stamina + amount, 0), maxStamina);
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
        return NarutoConfig.maxSeninMana;
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
    public String getHengeTarget() {
        return this.hengeTarget;
    }

    @Override
    public void setHengeTarget(String uuid) {
        this.hengeTarget = uuid == null ? "" : uuid;
    }

    @Override
    public String getHengeEntityType() {
        return this.hengeEntityType;
    }

    @Override
    public void setHengeEntityType(String entityType) {
        this.hengeEntityType = entityType == null ? "" : entityType;
    }

    @Override
    public String getHengeName() {
        return this.hengeName;
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
        return this.hengeTicks > 0 && (!this.hengeTarget.isEmpty() || !this.hengeEntityType.isEmpty());
    }

    @Override
    public DoubleJumpData getDoubleJumpData() {
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
                player.level().gameEvent(player, NarutoGameEvents.JUTSU_CASTING.get(), player.position().add(0, player.getEyeHeight() * 0.7, 0));
            }

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

    @Override
    public ToggleAbilityData getToggleAbilityData() {
        return this.toggleAbilityData;
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

        if (this.staminaRegenInfo.canRegen()) {
            this.stamina += NarutoConfig.staminaRegen;
        }
        if (this.chakraRegenInfo.canRegen()) {
            this.chakra += NarutoConfig.chakraRegen;
        }
        this.substitutions += NarutoConfig.substitutionRegenRate;
        this.substitutions = Math.min(Math.max(this.substitutions, 0), this.maxSubstitutions);
        this.stamina = Math.min(Math.max(this.stamina, 0), this.maxStamina);
        this.chakra = Math.min(Math.max(this.chakra, 0), this.getMaxChakra());

        if (this.currentlyChanneled != null) {
            Ability ability = NarutoRegistries.ABILITIES.getValue(this.currentlyChanneled);
            if (ability != null && ability.activationType() == Ability.ActivationType.CHANNELED) {
                if(ability.handleCost(player, this, this.ticksChanneled)) {
                    if (ability instanceof Ability.Channeled channeled) {
                        channeled.handleChannelling(player, this, this.ticksChanneled);
                    }
                } else {
                    if (this.ticksChanneled > 0) {
                        int finalTicksChanneled = this.ticksChanneled - 1;
                        ability.performServer(player, this, finalTicksChanneled);
                        this.setCurrentlyChanneledAbility(player, null);
                    }
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
            if (compoundTag.contains(KAMUI_RETURN_TAG)) {
                CompoundTag kamui = compoundTag.getCompound(KAMUI_RETURN_TAG);
                this.kamuiReturnLocation = new Vec3(kamui.getDouble("x"), kamui.getDouble("y"),
                        kamui.getDouble("z"));
            }
            if (compoundTag.contains(KAMUI_RETURN_DIMENSION_TAG)) {
                this.kamuiReturnDimension = new ResourceLocation(compoundTag.getString(KAMUI_RETURN_DIMENSION_TAG));
            }
            this.seninMana = compoundTag.getFloat(SENIN_MANA_TAG);
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
