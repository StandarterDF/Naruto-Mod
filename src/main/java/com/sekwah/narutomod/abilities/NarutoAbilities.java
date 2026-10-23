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
import com.sekwah.narutomod.abilities.jutsus.EarthWallJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.KamuiJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.KamuiPhaseAbility;
import com.sekwah.narutomod.abilities.jutsus.OreExcavationJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.OreSenseAbility;
import com.sekwah.narutomod.abilities.jutsus.WideDigAbility;
import com.sekwah.narutomod.abilities.jutsus.KirinAbility;
import com.sekwah.narutomod.abilities.jutsus.RasenganJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.RasenshurikenJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.SecondBreathAbility;
import com.sekwah.narutomod.abilities.jutsus.SageModeAbility;
import com.sekwah.narutomod.abilities.jutsus.SenjutsuChargingAbility;
import com.sekwah.narutomod.abilities.jutsus.GenjutsuJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.HengeJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneganAbility;
import com.sekwah.narutomod.abilities.jutsus.UniversalForceAbility;
import com.sekwah.narutomod.abilities.jutsus.ChibakuTenseiAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.abilities.jutsus.AmaterasuAbility;
import com.sekwah.narutomod.abilities.jutsus.KagutsuchiAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.abilities.jutsus.RotationAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganAbility;
import com.sekwah.narutomod.abilities.jutsus.DimensionStepAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraSeverAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganFlightAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganSightAbility;
import com.sekwah.narutomod.abilities.jutsus.MomoshikiAbsorptionAbility;
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

    /**
     * A real lightning bolt called down in a storm, stronger against soaked targets and able to run
     * on to other wet creatures nearby. See {@link KirinAbility}.
     */
    public static final RegistryObject<KirinAbility> KIRIN =
            ABILITY.register("kirin", KirinAbility::new);

    /**
     * A barrier of real blocks raised out of the ground, which sinks back after a while. See
     * {@link EarthWallJutsuAbility}.
     */
    public static final RegistryObject<EarthWallJutsuAbility> EARTH_WALL =
            ABILITY.register("earth_wall", EarthWallJutsuAbility::new);

    /**
     * The eye of the sage of six paths, opened as a stance: it adds chakra and nature energy
     * capacity while it is held, which is what pays for the powers built on it. See
     * {@link RinneganAbility}.
     */
    public static final RegistryObject<RinneganAbility> RINNEGAN =
            ABILITY.register("rinnegan", RinneganAbility::new);

    /**
     * One hand pulls, the other pushes: the Rinnegan's plainest power, tapped to drag a looked-at
     * creature in and sneaked to throw everything around the caster out. See
     * {@link UniversalForceAbility}.
     */
    public static final RegistryObject<UniversalForceAbility> UNIVERSAL_FORCE =
            ABILITY.register("universal_force", UniversalForceAbility::new);

    /**
     * The ball of a battlefield's own ground, lifted into the sky and dropped. See
     * {@link ChibakuTenseiAbility}.
     */
    public static final RegistryObject<ChibakuTenseiAbility> CHIBAKU_TENSEI =
            ABILITY.register("chibaku_tensei", ChibakuTenseiAbility::new);

    /**
     * The palm Rinnegan: a stance that swallows an incoming technique whole and hands its nature back
     * through the caster's own next technique of that nature. See
     * {@link MomoshikiAbsorptionAbility} and {@code AbsorptionEvents}.
     */
    public static final RegistryObject<MomoshikiAbsorptionAbility> MOMOSHIKI_ABSORPTION =
            ABILITY.register("momoshiki_absorption", MomoshikiAbsorptionAbility::new);

    /**
     * The copy wheel, opened as a stance: it reads whoever the caster looks at and shrugs off the
     * blind of an illusion, but hands out no capacity. See {@link SharinganAbility}.
     */
    public static final RegistryObject<SharinganAbility> SHARINGAN =
            ABILITY.register("sharingan", SharinganAbility::new);

    /**
     * The black flame willed onto a looked-at target, cast through an open Sharingan. See
     * {@link AmaterasuAbility}.
     */
    public static final RegistryObject<AmaterasuAbility> AMATERASU =
            ABILITY.register("amaterasu", AmaterasuAbility::new);

    /**
     * The shaping hand of the black flame: gather scattered Amaterasu onto one target, or snuff it
     * out. Cast through an open Sharingan. See {@link KagutsuchiAbility}.
     */
    public static final RegistryObject<KagutsuchiAbility> KAGUTSUCHI =
            ABILITY.register("kagutsuchi", KagutsuchiAbility::new);

    /**
     * The all-seeing eye, opened as a stance: every living body in a short bubble is shown through
     * the walls. See {@link ByakuganAbility}.
     */
    public static final RegistryObject<ByakuganAbility> BYAKUGAN =
            ABILITY.register("byakugan", ByakuganAbility::new);

    /**
     * The spinning defence of the Byakugan: projectiles are torn apart and bodies thrown out. See
     * {@link RotationAbility}.
     */
    public static final RegistryObject<RotationAbility> ROTATION =
            ABILITY.register("rotation", RotationAbility::new);

    /**
     * The pure eye, opened as a stance: hostile chakra is lit up and the caster's reserves grow
     * while it is held. See {@link JouganAbility}.
     */
    /**
     * The far eye: the cheapest stance in the mod, worth nothing in a fight and everything for
     * looking over a shoulder. See {@link SenriganAbility}.
     */
    public static final RegistryObject<SenriganAbility> SENRIGAN =
            ABILITY.register("senrigan", SenriganAbility::new);

    /**
     * Steps the caster's viewpoint through their own shadow clones, one cast per clone. See
     * {@link SenriganSightAbility}.
     */
    public static final RegistryObject<SenriganSightAbility> SENRIGAN_SIGHT =
            ABILITY.register("senrigan_sight", SenriganSightAbility::new);

    /**
     * Holds the caster in the air on chakra while any eye is open: the one piece of movement the
     * Doujutsu branches were missing. See {@link SenriganFlightAbility}.
     */
    public static final RegistryObject<SenriganFlightAbility> SENRIGAN_FLIGHT =
            ABILITY.register("senrigan_flight", SenriganFlightAbility::new);

    public static final RegistryObject<JouganAbility> JOUGAN =
            ABILITY.register("jougan", JouganAbility::new);

    /**
     * A step along the look ray that ignores whatever stands in between, see
     * {@link DimensionStepAbility}.
     */
    public static final RegistryObject<DimensionStepAbility> DIMENSION_STEP =
            ABILITY.register("dimension_step", DimensionStepAbility::new);

    /**
     * A look that strips a target's boons, drains a ninja's chakra and leaves the body weak. See
     * {@link ChakraSeverAbility}.
     */
    public static final RegistryObject<ChakraSeverAbility> CHAKRA_SEVER =
            ABILITY.register("chakra_sever", ChakraSeverAbility::new);

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
