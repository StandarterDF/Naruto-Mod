package com.sekwah.narutomod.abilities;

import com.mojang.logging.LogUtils;
import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.abilities.jutsus.FireballJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.FireBreathJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterBreathAbility;
import com.sekwah.narutomod.abilities.jutsus.WindBreathAbility;
import com.sekwah.narutomod.abilities.jutsus.WoodBreathAbility;
import com.sekwah.narutomod.abilities.jutsus.SubstitutionJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraSensingAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraThreadAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraAdhesionAbility;
import com.sekwah.narutomod.abilities.jutsus.BodyAdaptationAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraFeedingAbility;
import com.sekwah.narutomod.abilities.jutsus.SmokeScreenAbility;
import com.sekwah.narutomod.abilities.jutsus.SilenceAbility;
import com.sekwah.narutomod.abilities.jutsus.CloneRecallAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterBulletJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraMarkerJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraMeditationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ShadowCloneJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraRegenerationAbility;
import com.sekwah.narutomod.abilities.jutsus.CampfireJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShieldAbility;
import com.sekwah.narutomod.abilities.jutsus.EightGatesAbility;
import com.sekwah.narutomod.abilities.jutsus.KamuiJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.KamuiPhaseAbility;
import com.sekwah.narutomod.abilities.jutsus.OreExcavationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.OreSenseAbility;
import com.sekwah.narutomod.abilities.jutsus.WideDigAbility;
import com.sekwah.narutomod.abilities.jutsus.RasenganJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.RasenshurikenJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.SecondBreathAbility;
import com.sekwah.narutomod.abilities.jutsus.SageModeAbility;
import com.sekwah.narutomod.abilities.jutsus.SenjutsuChargingAbility;
import com.sekwah.narutomod.abilities.jutsus.GenjutsuJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.HengeJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.StorageChestAbility;
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
     * Widens every swing into a three by three plane of blocks while the stance is held. See
     * {@link WideDigAbility} and {@code WideDigEvents}.
     */
    public static final RegistryObject<WideDigAbility> WIDE_DIG =
            ABILITY.register("wide_dig", WideDigAbility::new);

    /**
     * A short pulse that shows ore through the rock. See {@link OreSenseAbility}.
     */
    public static final RegistryObject<OreSenseAbility> ORE_SENSE =
            ABILITY.register("ore_sense", OreSenseAbility::new);

    /**
     * Sets the log the caster is looking at alight, leaving a campfire in its place. See
     * {@link CampfireJutsuAbility}.
     */
    public static final RegistryObject<CampfireJutsuAbility> CAMPFIRE =
            ABILITY.register("campfire", CampfireJutsuAbility::new);

    /**
     * The channelled sphere of chakra. See {@link RasenganJutsuAbility}.
     */
    public static final RegistryObject<RasenganJutsuAbility> RASENGAN =
            ABILITY.register("rasengan", RasenganJutsuAbility::new);

    /**
     * The rasengan shaped into a thrown blade of wind. See {@link RasenshurikenJutsuAbility}.
     */
    public static final RegistryObject<RasenshurikenJutsuAbility> RASENSHURIKEN =
            ABILITY.register("rasenshuriken", RasenshurikenJutsuAbility::new);

    /**
     * The eight inner gates, opened one at a time and sealed again by holding the last key of the
     * same combo. See {@link EightGatesAbility}.
     */
    public static final RegistryObject<EightGatesAbility> EIGHT_GATES =
            ABILITY.register("eight_gates", EightGatesAbility::new);

    /**
     * The eye of the void: a pocket dimension the caster can step into, and a hand that drags
     * whatever they are looking at in with them. See {@link KamuiJutsuAbility}.
     */
    public static final RegistryObject<KamuiJutsuAbility> KAMUI =
            ABILITY.register("kamui", KamuiJutsuAbility::new);

    /**
     * The half-in-the-other-world stance: no collisions, no gravity, no damage, paid for per tick.
     * Split off from the Kamui eye so it can be used on its own. See {@link KamuiPhaseAbility}.
     */
    public static final RegistryObject<KamuiPhaseAbility> KAMUI_PHASING =
            ABILITY.register("kamui_phasing", KamuiPhaseAbility::new);

    /**
     * Standing still to gather nature energy, which is what sage mode spends. See
     * {@link SenjutsuChargingAbility}.
     */
    public static final RegistryObject<SenjutsuChargingAbility> SENJUTSU_CHARGING =
            ABILITY.register("senjutsu_charging", SenjutsuChargingAbility::new);

    /**
     * Opens the gathered nature energy up as sage mode. See {@link SageModeAbility}.
     */
    public static final RegistryObject<SageModeAbility> SAGE_MODE =
            ABILITY.register("sage_mode", SageModeAbility::new);

    /**
     * The illusion that stops a mob seeing players and points it at its neighbours instead. See
     * {@link GenjutsuJutsuAbility}.
     */
    public static final RegistryObject<GenjutsuJutsuAbility> GENJUTSU =
            ABILITY.register("genjutsu", GenjutsuJutsuAbility::new);

    /**
     * Wearing somebody else's face: a cosmetic disguise that copies another player's skin and name.
     * See {@link HengeJutsuAbility}.
     */
    public static final RegistryObject<HengeJutsuAbility> HENGE =
            ABILITY.register("henge", HengeJutsuAbility::new);

    /**
     * Summons the chest the caster's worker clones carry their harvest to. See
     * {@link StorageChestAbility}.
     */
    public static final RegistryObject<StorageChestAbility> STORAGE_CHEST =
            ABILITY.register("storage_chest", StorageChestAbility::new);

    /**
     * The held sense of what is around the caster, and the tapped night vision trick it shares a combo
     * with. See {@link ChakraSensingAbility}.
     */
    public static final RegistryObject<ChakraSensingAbility> CHAKRA_SENSING =
            ABILITY.register("chakra_sensing", ChakraSensingAbility::new);

    /**
     * Calls every clone the caster can still reach back to their side, so workers left at a worksite
     * are not lost track of. See {@link CloneRecallAbility}.
     */
    public static final RegistryObject<CloneRecallAbility> CLONE_RECALL =
            ABILITY.register("clone_recall", CloneRecallAbility::new);

    /**
     * A line of chakra that reels the caster towards a block, or a caught creature towards the
     * caster. See {@link ChakraThreadAbility}.
     */
    public static final RegistryObject<ChakraThreadAbility> CHAKRA_THREAD =
            ABILITY.register("chakra_thread", ChakraThreadAbility::new);

    /**
     * A held jet of burning chakra: it hurts whatever is caught in the cone but sets nothing alight,
     * and to the world it dries wet sponges and stokes furnaces. See {@link FireBreathJutsuAbility}.
     */
    public static final RegistryObject<FireBreathJutsuAbility> FIRE_BREATH =
            ABILITY.register("fire_breath", FireBreathJutsuAbility::new);

    /**
     * The opposite breath: a cold jet that snuffs out fire, soaks dry sponges into wet ones and
     * presses creatures back. See {@link WaterBreathAbility}.
     */
    public static final RegistryObject<WaterBreathAbility> WATER_BREATH =
            ABILITY.register("water_breath", WaterBreathAbility::new);

    /**
     * The breath that opens a way: no harm done, but creatures are shoved and cobwebs, vines and
     * tripwires are blown away. See {@link WindBreathAbility}.
     */
    public static final RegistryObject<WindBreathAbility> WIND_BREATH =
            ABILITY.register("wind_breath", WindBreathAbility::new);

    /**
     * The breath that feeds the ground: a spread of bonemeal in front of the caster, growing crops,
     * saplings and grass. See {@link WoodBreathAbility}.
     */
    public static final RegistryObject<WoodBreathAbility> WOOD_BREATH =
            ABILITY.register("wood_breath", WoodBreathAbility::new);

    /**
     * Chakra spread under the hands and feet to walk up a wall. The slow, always-available cousin of
     * the chakra thread. See {@link ChakraAdhesionAbility}.
     */
    public static final RegistryObject<ChakraAdhesionAbility> CHAKRA_ADHESION =
            ABILITY.register("chakra_adhesion", ChakraAdhesionAbility::new);

    /**
     * A survival stance that fits the caster to the place they are standing: breathing water,
     * ignoring lava and powder snow. See {@link BodyAdaptationAbility}.
     */
    public static final RegistryObject<BodyAdaptationAbility> BODY_ADAPTATION =
            ABILITY.register("body_adaptation", BodyAdaptationAbility::new);

    /**
     * A bank of smoke that hides everyone inside, mobs and players alike, and holds lost targets
     * gone. See {@link SmokeScreenAbility}.
     */
    public static final RegistryObject<SmokeScreenAbility> SMOKE_SCREEN =
            ABILITY.register("smoke_screen", SmokeScreenAbility::new);

    /**
     * Turns chakra into food slowly, to keep a ninja on their feet between meals. See
     * {@link ChakraFeedingAbility}.
     */
    public static final RegistryObject<ChakraFeedingAbility> CHAKRA_FEEDING =
            ABILITY.register("chakra_feeding", ChakraFeedingAbility::new);

    /**
     * A shell of chakra that swallows the caster's noise, so sculk sensors, shriekers and the warden
     * hear nothing and mob ears are dulled. See {@link SilenceAbility}.
     */
    public static final RegistryObject<SilenceAbility> SILENCE =
            ABILITY.register("silence", SilenceAbility::new);

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
