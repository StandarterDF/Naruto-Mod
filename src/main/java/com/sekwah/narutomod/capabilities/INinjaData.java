package com.sekwah.narutomod.capabilities;

import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.capabilities.toggleabilitydata.ToggleAbilityData;
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
     * Where a caster who stepped into the Kamui pocket is to be spat back out, see
     * {@code KamuiJutsuAbility}. Set on the way in and cleared on the way out.
     */
    Vec3 getKamuiReturnLoc();
    ResourceLocation getKamuiReturnDimension();
    void setKamuiReturnLoc(Vec3 loc, ResourceLocation dimension);

    DoubleJumpData getDoubleJumpData();

    HashMap<String, CooldownTickEvent> getCooldownEvents();

    ResourceLocation getCurrentlyChanneledAbility();
    int getCurrentlyChanneledTicks();
    void setCurrentlyChanneledAbility(Player player, Ability ability);

    ToggleAbilityData getToggleAbilityData();

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
