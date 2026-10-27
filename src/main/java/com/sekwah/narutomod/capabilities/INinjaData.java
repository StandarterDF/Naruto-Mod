package com.sekwah.narutomod.capabilities;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.absorption.AbsorptionData;
import com.sekwah.narutomod.capabilities.disabledabilitydata.DisabledAbilityData;
import com.sekwah.narutomod.capabilities.mastery.MasteryData;
import com.sekwah.narutomod.capabilities.skills.SkillData;
import com.sekwah.narutomod.capabilities.mastery.MeditationData;
import com.sekwah.narutomod.capabilities.toggleabilitydata.ToggleAbilityData;
import com.sekwah.narutomod.menu.SupplyChestContainer;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.INBTSerializable;

import java.util.HashMap;
import java.util.function.Consumer;

public interface INinjaData extends INBTSerializable<Tag> {
    float getChakra();
    float getMaxChakra();
    float getStamina();

    /**
     * Extra chakra capacity on top of the configured maximum. Currently handed out by the chakra
     * meditation clones, a hundred points per clone, and cleared again as soon as they are gone.
     */
    float getChakraCapacityBonus();
    void setChakraCapacityBonus(float bonus);

    /**
     * Extra chakra capacity that comes from the armour the caster is wearing, kept apart from
     * {@link #getChakraCapacityBonus()} so the two do not overwrite each other. Recomputed every
     * tick from the equipped pieces and not written to disk.
     */
    float getArmorChakraBonus();
    void setArmorChakraBonus(float bonus);

    /**
     * The max health bonus the worn armour last put on the player's attribute, remembered so the
     * modifier is only rebuilt when it actually changes rather than every tick. Server side only.
     */
    float getArmorHealthBonus();
    void setArmorHealthBonus(float bonus);

    /**
     * Extra chakra capacity handed out by an open eye, see {@code RinneganAbility}. Kept apart from
     * the armour and meditation bonuses for the same reason those two are kept apart, and cleared as
     * soon as the eye closes. Recomputed every tick and never written to disk.
     */
    float getEyeChakraBonus();
    void setEyeChakraBonus(float bonus);

    /**
     * Extra nature energy capacity handed out by an open eye, see {@code RinneganAbility}. Same rules
     * as {@link #getEyeChakraBonus()}.
     */
    float getEyeSeninBonus();
    void setEyeSeninBonus(float bonus);

    /**
     * Armour points bought with chakra, spent as they soak up damage. Zero when there is no shield.
     */
    float getShieldPoints();
    void setShieldPoints(float points);

    /**
     * Whether the second breath trick has been paid for and is waiting for a killing blow.
     */
    boolean isSecondBreathArmed();
    void setSecondBreathArmed(boolean armed);

    /**
     * How many of the eight inner gates are currently open, from none to all eight.
     */
    int getGateCount();
    void setGateCount(int gates);

    /**
     * Unpaid fraction of a point of health owed to the open gates, carried between drain ticks.
     */
    float getGateBacklashCarry();
    void setGateBacklashCarry(float carry);

    float getSubstitutionCount();

    /**
     * How many substitution charges the pool can hold: the configured base plus whatever the skill
     * tree has bought. The technique starts at a single charge and grows by one per upgrade node.
     */
    float getMaxSubstitutions();

    float getMaxStamina();
    void setChakra(float amount);
    void setStamina(float amount);
    void useChakra(float amount, int cooldown);
    void useStamina(float amount, int cooldown);
    void useSubstitution(float amount);
    void addChakra(float amount);
    void addStamina(float amount);
    void setInvisibleTicks(int ticks);
    boolean getInvisible();

    Vec3 getSubstitutionLoc();
    ResourceLocation getSubstitutionDimension();
    void setSubstitutionLoc(Vec3 loc, ResourceLocation dimension);

    /**
     * Where the player's chakra marker log currently stands, see {@code ChakraMarkerJutsuAbility}.
     * Null until they have planted one.
     */
    Vec3 getMarkerLoc();
    ResourceLocation getMarkerDimension();
    void setMarkerLoc(Vec3 loc, ResourceLocation dimension);

    /**
     * The caster's supply chest, see {@code StorageChestAbility}: one block-free container that all
     * of the caster's worker clones drop their harvest into. It lives in the caster's own data, so a
     * worker can deposit from anywhere without a chunk being loaded, and it survives a logout.
     */
    SupplyChestContainer getSupplyChest();

    /**
     * Where a caster who stepped into the Kamui pocket is to be spat back out, see
     * {@code KamuiJutsuAbility}. Set on the way in and cleared on the way out.
     */
    Vec3 getKamuiReturnLoc();
    ResourceLocation getKamuiReturnDimension();
    void setKamuiReturnLoc(Vec3 loc, ResourceLocation dimension);

    /**
     * Nature energy gathered by standing still and spent by sage mode, see {@code SageModeAbility}.
     * Synced, because the HUD draws a bar for it.
     */
    float getSeninMana();
    void setSeninMana(float mana);
    void addSeninMana(float mana);
    float getMaxSeninMana();

    /**
     * Whether sage mode is currently up. Server side state with a client copy, and deliberately not
     * written to disk: death and logging out end it.
     */
    boolean isSageMode();
    void setSageMode(boolean sage);

    /**
     * How many Truth-Seeking Orbs the Six Paths mode has left. Synced, because the orbs are drawn
     * around the caster on the client. See {@code SixPathsModeAbility}.
     */
    int getSixPathsOrbs();
    void setSixPathsOrbs(int orbs);

    /**
     * Integrity left in the Eight Trigrams vessel, or zero when there is none. Synced, because the
     * HUD reads the vessel from it. Written by the server tick from the vessel's own mark, see
     * {@code TailedBeastEvents}. See {@code SealVesselAbility}.
     */
    int getSealVesselIntegrity();
    void setSealVesselIntegrity(int integrity);

    /**
     * How far the caster's chakra sense reaches, or zero when it is not up. Synced, because the whole
     * technique is a picture drawn on the client: the glow around creatures and the structure markers
     * are rendered from this, see {@code ChakraSensingAbility}.
     */
    float getSensingRadius();
    void setSensingRadius(float radius);

    /**
     * Ticks of chakra funded night vision left, or zero when it is off. Server side only: the client
     * sees the ordinary vanilla effect. See {@code ChakraSensingAbility#maintain}.
     */
    int getNightVisionTicks();
    void setNightVisionTicks(int ticks);

    /**
     * UUID of the player whose form is currently worn as a disguise, or an empty string when the
     * caster is wearing their own face or the form of a mob. Synced, because the only thing that uses
     * it is the client side renderer, see {@code HengeRenderEvents}.
     */
    String getHengeTarget();
    void setHengeTarget(String uuid);

    /**
     * Registry id of the mob whose form is currently worn, or an empty string when the disguise is a
     * player (or when nothing is worn). Synced for the same reason as {@link #getHengeTarget()}.
     */
    String getHengeEntityType();
    void setHengeEntityType(String entityType);

    /** Display name of the copied form, synced so the name tag can lie along with the shape. */
    String getHengeName();
    void setHengeName(String name);

    /** Ticks of disguise left. Server side only: the client reads the target instead. */
    int getHengeTicks();
    void setHengeTicks(int ticks);

    /** @return whether the caster is currently wearing somebody else's form. */
    boolean isHenged();

    DoubleJumpData getDoubleJumpData();

    HashMap<String, CooldownTickEvent> getCooldownEvents();

    ResourceLocation getCurrentlyChanneledAbility();
    int getCurrentlyChanneledTicks();
    void setCurrentlyChanneledAbility(Player player, Ability ability);

    ToggleAbilityData getToggleAbilityData();

    /**
     * How many times each technique has been cast by this player, which is what mastery is worked
     * out from. Synced, because the jutsu menu draws a progress bar per technique from it.
     */
    MasteryData getMasteryData();

    /**
     * The skill tree nodes bought with skill points, and the points themselves. Synced, because the
     * skill screen draws the tree from it, and saved, because points and nodes are permanent.
     */
    SkillData getSkillData();

    /**
     * Techniques swallowed through the palm Rinnegan and not yet handed back, per nature. Synced for
     * the HUD and deliberately not saved: borrowed chakra is a held stance, not a possession. See
     * {@code MomoshikiAbsorptionAbility}.
     */
    AbsorptionData getAbsorptionData();

    /**
     * The techniques this caster has switched off in the jutsu menu. Synced, so the menu can grey
     * them out and refuse to send their cast packets, and saved, so the choice survives a relog.
     */
    DisabledAbilityData getDisabledAbilityData();

    /** Whether a technique is currently switched off, see {@link #getDisabledAbilityData()}. */
    boolean isAbilityDisabled(ResourceLocation abilityId);

    /**
     * Whether a click in the jutsu menu may cast any technique, not only the ones that stay on, see
     * {@code MenuClickCommand}. Off by default.
     */
    boolean isMenuClickAll();

    /** Turns {@link #isMenuClickAll()} on or off. */
    void setMenuClickAll(boolean enabled);

    /**
     * Whether this ninja has already collected the one-time reward for a boss milestone. Bosses are
     * tracked by a short id rather than by entity, because the reward is about the hunt, not the
     * individual mob.
     */
    boolean hasClaimedBossMilestone(String bossId);

    /** Marks a boss milestone as collected, so its reward is paid exactly once. */
    void claimBossMilestone(String bossId);

    /**
     * The ninja's current rank id (see {@code ranks/NinjaRank}), or an empty string before their
     * first promotion. Server side only: the client only needs the advancement screen.
     */
    String getNinjaRank();

    void setNinjaRank(String rank);

    /**
     * The element this ninja was born to, as a {@code Nature} id, or an empty string before the
     * choice is made. Synced because the jutsu menu shows the affinity in its prices.
     */
    String getNatureAffinity();

    void setNatureAffinity(String affinity);

    /**
     * Whether the starting handbook was already given. Saved, so turning ninja mode off and on again
     * cannot be used to print more copies of it.
     */
    boolean isHandbookGiven();

    void setHandbookGiven(boolean given);

    /**
     * The standing meditating clones of this caster, used to feed them chakra at a distance without
     * scanning the world. Server side only. See {@code ChakraMeditationJutsuAbility}.
     */
    MeditationData getMeditationData();

    void updateDataServer(Player player);

    void scheduleDelayedTickEvent(Consumer<Player> consumer, int tickDelay);

    /**
     * Used to update client tracking information
     * @param player
     */
    void updateDataClient(Player player);

    void setIsNinja(boolean enableNinja);

    boolean isNinjaModeEnabled();
}
