package com.sekwah.narutomod.abilities;

import com.mojang.logging.LogUtils;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.FireballJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.SubstitutionJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterBulletJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraMarkerJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraMeditationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ShadowCloneJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraRegenerationAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShieldAbility;
import com.sekwah.narutomod.abilities.jutsus.EightGatesAbility;
import com.sekwah.narutomod.abilities.jutsus.GateReleaseAbility;
import com.sekwah.narutomod.abilities.jutsus.OreExcavationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.RasenganJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.SecondBreathAbility;
import com.sekwah.narutomod.abilities.utility.ChakraChargeAbility;
import com.sekwah.narutomod.abilities.utility.ChakraFistAbility;
import com.sekwah.narutomod.abilities.utility.DoubleJumpAbility;
import com.sekwah.narutomod.abilities.utility.LeapAbility;
import com.sekwah.narutomod.abilities.utility.WaterWalkAbility;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerAbilityActivatePacket;
import com.sekwah.narutomod.network.c2s.ServerAbilityChannelPacket;
import com.sekwah.narutomod.registries.NarutoRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Map;

import static com.sekwah.narutomod.NarutoMod.MOD_ID;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NarutoAbilities {

    private static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Ability> ABILITY = DeferredRegister.create(NarutoRegistries.ABILITY_REGISTRY_LOC, MOD_ID);

    public static final Map<Long, ResourceLocation> COMBO_MAP = new HashMap<>();


    public static final RegistryObject<LeapAbility> LEAP = ABILITY.register("leap", LeapAbility::new);

    //public static final RegistryObject<ChakraDashAbility> CHAKRA_DASH = ABILITY.register("chakra_dash", ChakraDashAbility::new);

    public static final RegistryObject<WaterWalkAbility> WATER_WALK = ABILITY.register("water_walk", WaterWalkAbility::new);

    public static final RegistryObject<FireballJutsuAbility> FIREBALL = ABILITY.register("fireball", FireballJutsuAbility::new);

    public static final RegistryObject<WaterBulletJutsuAbility> WATER_BULLET = ABILITY.register("water_bullet", WaterBulletJutsuAbility::new);

    public static final RegistryObject<ChakraChargeAbility> CHAKRA_CHARGE = ABILITY.register("chakra_charge", ChakraChargeAbility::new);

    public static final RegistryObject<DoubleJumpAbility> DOUBLE_JUMP = ABILITY.register("double_jump", DoubleJumpAbility::new);

    /**
     * The chakra fist stance: faster mining, harder punches, paid for by the block and the hit.
     * See {@link ChakraFistAbility}.
     */
    public static final RegistryObject<ChakraFistAbility> CHAKRA_FIST = ABILITY.register("chakra_fist", ChakraFistAbility::new);

    public static final RegistryObject<SubstitutionJutsuAbility> SUBSTITUTION = ABILITY.register("substitution", SubstitutionJutsuAbility::new);

    public static final RegistryObject<ShadowCloneJutsuAbility> SHADOW_CLONE = ABILITY.register("shadow_clone",
            () -> new ShadowCloneJutsuAbility(ShadowCloneJutsuAbility.SHADOW_CLONE_SETTINGS));

    public static final RegistryObject<ShadowCloneJutsuAbility> CHIBI_SHADOW_CLONE = ABILITY.register("chibi_shadow_clone",
            () -> new ShadowCloneJutsuAbility(ShadowCloneJutsuAbility.CHIBI_SHADOW_CLONE_SETTINGS));

    public static final RegistryObject<ShadowCloneJutsuAbility> MULTI_SHADOW_CLONE = ABILITY.register("multi_shadow_clone",
            () -> new ShadowCloneJutsuAbility(ShadowCloneJutsuAbility.MULTI_SHADOW_CLONE_SETTINGS));

    /**
     * Leaves two clones behind to meditate, refilling the caster's chakra and cooling their jutsu
     * down twice as fast while they last. See {@link ChakraMeditationJutsuAbility}.
     */
    public static final RegistryObject<ChakraMeditationJutsuAbility> CHAKRA_MEDITATION =
            ABILITY.register("chakra_meditation", ChakraMeditationJutsuAbility::new);

    /**
     * Plants the one log a ninja gets to leave behind, and swaps places with it on a second cast.
     * See {@link ChakraMarkerJutsuAbility}.
     */
    public static final RegistryObject<ChakraMarkerJutsuAbility> CHAKRA_MARKER =
            ABILITY.register("chakra_marker", ChakraMarkerJutsuAbility::new);

    /**
     * Turns the whole chakra bar into a shield that is eaten away hit by hit. See
     * {@link ChakraShieldAbility}.
     */
    public static final RegistryObject<ChakraShieldAbility> CHAKRA_SHIELD =
            ABILITY.register("chakra_shield", ChakraShieldAbility::new);

    /**
     * Buys one escape from death with the whole chakra bar. See {@link SecondBreathAbility}.
     */
    public static final RegistryObject<SecondBreathAbility> SECOND_BREATH =
            ABILITY.register("second_breath", SecondBreathAbility::new);

    /**
     * Closes wounds with chakra and food rather than saturation. See
     * {@link ChakraRegenerationAbility}.
     */
    public static final RegistryObject<ChakraRegenerationAbility> CHAKRA_REGENERATION =
            ABILITY.register("chakra_regeneration", ChakraRegenerationAbility::new);

    /**
     * Shakes the ore out of the ground around the caster. See {@link OreExcavationJutsuAbility}.
     */
    public static final RegistryObject<OreExcavationJutsuAbility> ORE_EXCAVATION =
            ABILITY.register("ore_excavation", OreExcavationJutsuAbility::new);

    /**
     * The channelled sphere of chakra. See {@link RasenganJutsuAbility}.
     */
    public static final RegistryObject<RasenganJutsuAbility> RASENGAN =
            ABILITY.register("rasengan", RasenganJutsuAbility::new);

    /**
     * The eight inner gates, opened one at a time. See {@link EightGatesAbility}.
     */
    public static final RegistryObject<EightGatesAbility> EIGHT_GATES =
            ABILITY.register("eight_gates", EightGatesAbility::new);

    /**
     * Seals the gates that are open again, any number short of all eight. See
     * {@link GateReleaseAbility}.
     */
    public static final RegistryObject<GateReleaseAbility> GATE_RELEASE =
            ABILITY.register("gate_release", GateReleaseAbility::new);

    public static void register(IEventBus eventBus) {
        ABILITY.register(eventBus);
    }

    /**
     * May change how key combos are handled in the future but these will be default
     */
    public static void registerKeyCombos() {
        ABILITY.getEntries().forEach(abilityEntry -> {
            Ability ability = abilityEntry.get();
            long combo = ability.defaultCombo();
            if (combo > 0) {
                if(COMBO_MAP.containsKey(combo)) {
                    LOGGER.error("Ability already registered with that combo {}", combo);
                } else {
                    NarutoRegistries.ABILITIES.getResourceKey(ability).ifPresent(resourceKey -> COMBO_MAP.put(combo, resourceKey.location()));
                }
            }
        });
    }

    /**
     * Send to the server that the player wants to use a specific ability
     */
    public static void triggerAbility(ResourceLocation ability) {
        PacketHandler.sendToServer(new ServerAbilityActivatePacket(ability));
    }

    public static Ability getAbilityFromCombo(long combo) {
        if(COMBO_MAP.containsKey(combo)) {
            return NarutoRegistries.ABILITIES.getValue(COMBO_MAP.get(combo));
        } else {
            return null;
        }
    }

    public static boolean handleCharging(long combo, ServerAbilityChannelPacket.ChannelStatus channelStatus) {
        if(COMBO_MAP.containsKey(combo)) {
            ResourceLocation abilityResource = COMBO_MAP.get(combo);
            PacketHandler.sendToServer(new ServerAbilityChannelPacket(abilityResource, channelStatus));
            return true;
        } else {
            return false;
        }
    }

    public static boolean triggerAbility(long combo) {
        if(COMBO_MAP.containsKey(combo)) {
            triggerAbility(COMBO_MAP.get(combo));
            return true;
        } else {
            return false;
        }
    }


    @SubscribeEvent
    public static void clientSetup(FMLCommonSetupEvent event) {
        registerKeyCombos();
    }
}
