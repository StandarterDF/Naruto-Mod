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
import com.sekwah.narutomod.abilities.jutsus.OreVeinMiningAbility;
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
import com.sekwah.narutomod.abilities.jutsus.ChakraTransferAbility;
import com.sekwah.narutomod.abilities.jutsus.HeavenWeightAbility;
import com.sekwah.narutomod.abilities.jutsus.SharinganAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraTrailAbility;
import com.sekwah.narutomod.abilities.jutsus.SuggestionAbility;
import com.sekwah.narutomod.abilities.jutsus.TsukuyomiAbility;
import com.sekwah.narutomod.abilities.jutsus.KuraiAbility;
import com.sekwah.narutomod.abilities.jutsus.IzanagiAbility;
import com.sekwah.narutomod.abilities.jutsus.IzanamiAbility;
import com.sekwah.narutomod.abilities.jutsus.KotoamatsukamiAbility;
import com.sekwah.narutomod.abilities.jutsus.PlayerGenjutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.FaceSwapAbility;
import com.sekwah.narutomod.abilities.jutsus.ParryAbility;
import com.sekwah.narutomod.abilities.jutsus.VacuumBladeAbility;
import com.sekwah.narutomod.abilities.jutsus.PalmBlockAbility;
import com.sekwah.narutomod.abilities.jutsus.InnerStrikeAbility;
import com.sekwah.narutomod.abilities.jutsus.GatePeacockAbility;
import com.sekwah.narutomod.abilities.jutsus.GateTigerAbility;
import com.sekwah.narutomod.abilities.jutsus.GateElephantAbility;
import com.sekwah.narutomod.abilities.jutsus.SealVesselAbility;
import com.sekwah.narutomod.abilities.jutsus.DividedCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.SealBreakAbility;
import com.sekwah.narutomod.abilities.jutsus.PerfectCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedBallAbility;
import com.sekwah.narutomod.abilities.jutsus.CloneSparringAbility;
import com.sekwah.narutomod.abilities.jutsus.CloneSacrificeAbility;
import com.sekwah.narutomod.abilities.jutsus.IronGuardianAbility;
import com.sekwah.narutomod.abilities.jutsus.BladeRainAbility;
import com.sekwah.narutomod.abilities.jutsus.ExplosionSilencerAbility;
import com.sekwah.narutomod.abilities.jutsus.KatsuyuNetworkAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraWhetstoneAbility;
import com.sekwah.narutomod.abilities.jutsus.KamaeAbility;
import com.sekwah.narutomod.abilities.jutsus.KusaGiriAbility;
import com.sekwah.narutomod.abilities.jutsus.KesaGiriAbility;
import com.sekwah.narutomod.abilities.jutsus.FlameFistAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterHandAbility;
import com.sekwah.narutomod.abilities.jutsus.HiramekareiAbility;
import com.sekwah.narutomod.abilities.jutsus.ShibukiAbility;
import com.sekwah.narutomod.abilities.jutsus.BleedingBladeAbility;
import com.sekwah.narutomod.abilities.jutsus.ElementalEdgeAbility;
import com.sekwah.narutomod.abilities.jutsus.AmaterasuAbility;
import com.sekwah.narutomod.abilities.jutsus.KagutsuchiAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganAbility;
import com.sekwah.narutomod.abilities.jutsus.RotationAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganAbility;
import com.sekwah.narutomod.abilities.jutsus.KaiAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganStasisAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganUnweaveAbility;
import com.sekwah.narutomod.abilities.jutsus.TenketsuNeedlesAbility;
import com.sekwah.narutomod.abilities.jutsus.TenketsuBlindAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraGripAbility;
import com.sekwah.narutomod.abilities.jutsus.NodeRuptureAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganFarsightAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganMistSightAbility;
import com.sekwah.narutomod.abilities.jutsus.SoshikenAbility;
import com.sekwah.narutomod.abilities.jutsus.PalmLanceAbility;
import com.sekwah.narutomod.abilities.jutsus.MountainCollapseAbility;
import com.sekwah.narutomod.abilities.jutsus.DimensionStepAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraSeverAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganFlightAbility;
import com.sekwah.narutomod.abilities.jutsus.SenriganSightAbility;
import com.sekwah.narutomod.abilities.jutsus.MomoshikiAbsorptionAbility;
import com.sekwah.narutomod.abilities.jutsus.StorageChestAbility;
import com.sekwah.narutomod.abilities.jutsus.DecoyCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.MedicCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.ShurikenShadowAbility;
import com.sekwah.narutomod.abilities.jutsus.ShadowGrabAbility;
import com.sekwah.narutomod.abilities.jutsus.MokutonGrowthAbility;
import com.sekwah.narutomod.abilities.jutsus.WateringAbility;
import com.sekwah.narutomod.abilities.jutsus.WindStepAbility;
import com.sekwah.narutomod.abilities.jutsus.BeastCarryAbility;
import com.sekwah.narutomod.abilities.jutsus.CloneSwapAbility;
import com.sekwah.narutomod.abilities.jutsus.WaterCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.MudCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.ClayCloneAbility;
import com.sekwah.narutomod.abilities.jutsus.CombatStanceAbility;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import net.minecraft.world.entity.player.Player;
import com.sekwah.narutomod.abilities.jutsus.CombatStanceAbility;
import com.sekwah.narutomod.abilities.jutsus.ProjectileCutAbility;
import com.sekwah.narutomod.abilities.jutsus.DrunkenFistAbility;
import com.sekwah.narutomod.abilities.jutsus.LimboAbility;
import com.sekwah.narutomod.abilities.jutsus.AmenominakaAbility;
import com.sekwah.narutomod.abilities.jutsus.YinYangHealingAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealOneAbility;
import com.sekwah.narutomod.abilities.jutsus.CurseSealTwoAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.TailedCloakAbility;
import com.sekwah.narutomod.abilities.jutsus.LightningModeAbility;
import com.sekwah.narutomod.abilities.jutsus.OrochimaruBodyAbility;
import com.sekwah.narutomod.abilities.jutsus.MindTransferAbility;
import com.sekwah.narutomod.abilities.jutsus.SizeExpansionAbility;
import com.sekwah.narutomod.abilities.jutsus.CamelliaDanceAbility;
import com.sekwah.narutomod.abilities.jutsus.BoneBulletsAbility;
import com.sekwah.narutomod.abilities.jutsus.BoneToolsAbility;
import com.sekwah.narutomod.abilities.jutsus.SixPathsModeAbility;
import com.sekwah.narutomod.abilities.jutsus.GudodamaLaunchAbility;
import com.sekwah.narutomod.abilities.jutsus.GudodamaGuardAbility;
import com.sekwah.narutomod.abilities.jutsus.GudodamaFormAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneSharinganAbility;
import com.sekwah.narutomod.abilities.jutsus.SealPortalAbility;
import com.sekwah.narutomod.abilities.jutsus.SewingPocketAbility;
import com.sekwah.narutomod.abilities.jutsus.SewingLockAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShearAbility;
import com.sekwah.narutomod.abilities.jutsus.StructureCutAbility;
import com.sekwah.narutomod.abilities.jutsus.JouganPhaseAbility;
import com.sekwah.narutomod.abilities.jutsus.BlackRodAbility;
import com.sekwah.narutomod.abilities.jutsus.ItemAttractionAbility;
import com.sekwah.narutomod.abilities.jutsus.RinneganLevitationAbility;
import com.sekwah.narutomod.abilities.jutsus.VoidCastAbility;
import com.sekwah.narutomod.abilities.jutsus.MechanismSightAbility;
import com.sekwah.narutomod.abilities.jutsus.HiddenSealAbility;
import com.sekwah.narutomod.abilities.jutsus.DoctorSightAbility;
import com.sekwah.narutomod.abilities.jutsus.SixtyFourPalmsAbility;
import com.sekwah.narutomod.abilities.jutsus.ByakuganMarkAbility;
import com.sekwah.narutomod.abilities.jutsus.AirPalmAbility;
import com.sekwah.narutomod.abilities.jutsus.CircularBlockAbility;
import com.sekwah.narutomod.abilities.jutsus.NarakaPathAbility;
import com.sekwah.narutomod.abilities.jutsus.AnimalPathAbility;
import com.sekwah.narutomod.abilities.jutsus.AsuraMissilesAbility;
import com.sekwah.narutomod.abilities.jutsus.RinboHengokuAbility;
import com.sekwah.narutomod.abilities.jutsus.OuterPathChainsAbility;
import com.sekwah.narutomod.abilities.jutsus.OuterPathArmorAbility;
import com.sekwah.narutomod.abilities.jutsus.SusanooAbility;
import com.sekwah.narutomod.abilities.jutsus.IllusoryDoubleAbility;
import com.sekwah.narutomod.abilities.utility.ChakraChargeAbility;
import com.sekwah.narutomod.abilities.utility.ChakraDashAbility;
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

    /**
     * The body flicker: a short step along the look that stops at the first wall. See
     * {@link ChakraDashAbility}.
     */
    public static final RegistryObject<ChakraDashAbility> CHAKRA_DASH = ABILITY.register("chakra_dash", ChakraDashAbility::new);

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
     * The mastered mining stance: every ore broken by hand pulls its whole vein out for chakra.
     * See {@link OreVeinMiningAbility}.
     */
    public static final RegistryObject<OreVeinMiningAbility> ORE_VEIN_MINING =
            ABILITY.register("ore_vein_mining", OreVeinMiningAbility::new);

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
     * The shadow that roots a body where it stands while the seal is held. See
     * {@link ShadowGrabAbility}.
     */
    public static final RegistryObject<ShadowGrabAbility> SHADOW_GRAB =
            ABILITY.register("shadow_grab", ShadowGrabAbility::new);

    /**
     * The release word that breaks an illusion on the caster and everyone standing with them. See
     * {@link KaiAbility}.
     */
    public static final RegistryObject<KaiAbility> KAI =
            ABILITY.register("kai", KaiAbility::new);

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
     * The eye handing chakra to an ally rather than taking it. See {@link ChakraTransferAbility}.
     */
    public static final RegistryObject<ChakraTransferAbility> CHAKRA_TRANSFER =
            ABILITY.register("chakra_transfer", ChakraTransferAbility::new);

    /**
     * The eye pressing the sky down on everything around the caster. See
     * {@link HeavenWeightAbility}.
     */
    public static final RegistryObject<HeavenWeightAbility> HEAVEN_WEIGHT =
            ABILITY.register("heaven_weight", HeavenWeightAbility::new);

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
     * The eye leaving a mark on a body: the chakra trail the tracking hook follows for thirty
     * seconds. See {@link ChakraTrailAbility}.
     */
    public static final RegistryObject<ChakraTrailAbility> CHAKRA_TRAIL =
            ABILITY.register("chakra_trail", ChakraTrailAbility::new);

    /**
     * The eye taking itself out of a creature's mind for eight seconds. See
     * {@link SuggestionAbility}.
     */
    public static final RegistryObject<SuggestionAbility> SUGGESTION =
            ABILITY.register("suggestion", SuggestionAbility::new);

    /** The moon's illusion: a second of hell held in one mind. See {@link TsukuyomiAbility}. */
    public static final RegistryObject<TsukuyomiAbility> TSUKUYOMI =
            ABILITY.register("tsukuyomi", TsukuyomiAbility::new);

    /** The look that puts out the lights in one mind. See {@link KuraiAbility}. */
    public static final RegistryObject<KuraiAbility> KURAI =
            ABILITY.register("kurai", KuraiAbility::new);

    /** The eye spent to rewrite a death. See {@link IzanagiAbility}. */
    public static final RegistryObject<IzanagiAbility> IZANAGI =
            ABILITY.register("izanagi", IzanagiAbility::new);

    /** The loop that closes on the third blow. See {@link IzanamiAbility}. */
    public static final RegistryObject<IzanamiAbility> IZANAMI =
            ABILITY.register("izanami", IzanamiAbility::new);

    /** The whisper that makes a creature believe the caster is an ally. See
     * {@link KotoamatsukamiAbility}. */
    public static final RegistryObject<KotoamatsukamiAbility> KOTOAMATSUKAMI =
            ABILITY.register("kotoamatsukami", KotoamatsukamiAbility::new);

    /** The illusion turned on another player, behind a server flag. See {@link PlayerGenjutsuAbility}. */
    public static final RegistryObject<PlayerGenjutsuAbility> PLAYER_GENJUTSU =
            ABILITY.register("player_genjutsu", PlayerGenjutsuAbility::new);

    /** The face the victim sees on everyone. See {@link FaceSwapAbility}. */
    public static final RegistryObject<FaceSwapAbility> FACE_SWAP =
            ABILITY.register("face_swap", FaceSwapAbility::new);

    /** A sword held ready. See {@link ParryAbility}. */
    public static final RegistryObject<ParryAbility> BLADE_PARRY =
            ABILITY.register("blade_parry", ParryAbility::new);

    /** The crescent of wind off the blade. See {@link VacuumBladeAbility}. */
    public static final RegistryObject<VacuumBladeAbility> VACUUM_BLADE =
            ABILITY.register("vacuum_blade", VacuumBladeAbility::new);

    /** The empty-hand guard. See {@link PalmBlockAbility}. */
    public static final RegistryObject<PalmBlockAbility> PALM_BLOCK =
            ABILITY.register("palm_block", PalmBlockAbility::new);

    /** The short palm that breaks a hold. See {@link InnerStrikeAbility}. */
    public static final RegistryObject<InnerStrikeAbility> INNER_STRIKE =
            ABILITY.register("inner_strike", InnerStrikeAbility::new);

    /** Morning Peacock. See {@link GatePeacockAbility}. */
    public static final RegistryObject<GatePeacockAbility> GATE_PEACOCK =
            ABILITY.register("gate_peacock", GatePeacockAbility::new);

    /** Daytime Tiger. See {@link GateTigerAbility}. */
    public static final RegistryObject<GateTigerAbility> GATE_TIGER =
            ABILITY.register("gate_tiger", GateTigerAbility::new);

    /** Evening Elephant. See {@link GateElephantAbility}. */
    public static final RegistryObject<GateElephantAbility> GATE_ELEPHANT =
            ABILITY.register("gate_elephant", GateElephantAbility::new);

    /** The cage a tailed beast is written into. See {@link SealVesselAbility}. */
    public static final RegistryObject<SealVesselAbility> SEAL_VESSEL =
            ABILITY.register("seal_vessel", SealVesselAbility::new);

    /** The vessel shared with allies. See {@link DividedCloakAbility}. */
    public static final RegistryObject<DividedCloakAbility> DIVIDED_CLOAK =
            ABILITY.register("divided_cloak", DividedCloakAbility::new);

    /** The seal forced open. See {@link SealBreakAbility}. */
    public static final RegistryObject<SealBreakAbility> SEAL_BREAK =
            ABILITY.register("seal_break", SealBreakAbility::new);

    /** The beast's agreement. See {@link PerfectCloakAbility}. */
    public static final RegistryObject<PerfectCloakAbility> PERFECT_CLOAK =
            ABILITY.register("perfect_cloak", PerfectCloakAbility::new);

    /** The tailed beast bomb. See {@link TailedBallAbility}. */
    public static final RegistryObject<TailedBallAbility> TAILED_BALL =
            ABILITY.register("tailed_ball", TailedBallAbility::new);

    /** Training against your own clone. See {@link CloneSparringAbility}. */
    public static final RegistryObject<CloneSparringAbility> CLONE_SPARRING =
            ABILITY.register("clone_sparring", CloneSparringAbility::new);

    /** Spending a clone in the caster's place. See {@link CloneSacrificeAbility}. */
    public static final RegistryObject<CloneSacrificeAbility> CLONE_SACRIFICE =
            ABILITY.register("clone_sacrifice", CloneSacrificeAbility::new);

    /** The iron guardian. See {@link IronGuardianAbility}. */
    public static final RegistryObject<IronGuardianAbility> IRON_GUARDIAN =
            ABILITY.register("iron_guardian", IronGuardianAbility::new);

    /** The fan of sixteen blades. See {@link BladeRainAbility}. */
    public static final RegistryObject<BladeRainAbility> BLADE_RAIN =
            ABILITY.register("blade_rain", BladeRainAbility::new);

    /** The seal that swallows blasts. See {@link ExplosionSilencerAbility}. */
    public static final RegistryObject<ExplosionSilencerAbility> EXPLOSION_SILENCER =
            ABILITY.register("explosion_silencer", ExplosionSilencerAbility::new);

    /** The Katsuyu healing field. See {@link KatsuyuNetworkAbility}. */
    public static final RegistryObject<KatsuyuNetworkAbility> KATSUYU_NETWORK =
            ABILITY.register("katsuyu_network", KatsuyuNetworkAbility::new);

    /** Registered technique. See {@link ChakraWhetstoneAbility}. */
    public static final RegistryObject<ChakraWhetstoneAbility> CHAKRA_WHETSTONE =
            ABILITY.register("chakra_whetstone", ChakraWhetstoneAbility::new);
    /** Registered technique. See {@link KamaeAbility}. */
    public static final RegistryObject<KamaeAbility> KAMAE =
            ABILITY.register("kamae", KamaeAbility::new);
    /** Registered technique. See {@link KusaGiriAbility}. */
    public static final RegistryObject<KusaGiriAbility> KUSA_GIRI =
            ABILITY.register("kusa_giri", KusaGiriAbility::new);
    /** Registered technique. See {@link KesaGiriAbility}. */
    public static final RegistryObject<KesaGiriAbility> KESA_GIRI =
            ABILITY.register("kesa_giri", KesaGiriAbility::new);
    /** Registered technique. See {@link FlameFistAbility}. */
    public static final RegistryObject<FlameFistAbility> FLAME_FIST =
            ABILITY.register("flame_fist", FlameFistAbility::new);
    /** Registered technique. See {@link WaterHandAbility}. */
    public static final RegistryObject<WaterHandAbility> WATER_HAND =
            ABILITY.register("water_hand", WaterHandAbility::new);
    /** Registered technique. See {@link HiramekareiAbility}. */
    public static final RegistryObject<HiramekareiAbility> HIRAMEKAREI =
            ABILITY.register("hiramekarei", HiramekareiAbility::new);
    /** Registered technique. See {@link ShibukiAbility}. */
    public static final RegistryObject<ShibukiAbility> SHIBUKI =
            ABILITY.register("shibuki", ShibukiAbility::new);
    /** Registered technique. See {@link BleedingBladeAbility}. */
    public static final RegistryObject<BleedingBladeAbility> BLEEDING_BLADE =
            ABILITY.register("bleeding_blade", BleedingBladeAbility::new);
    /** Registered technique. See {@link ElementalEdgeAbility}. */
    public static final RegistryObject<ElementalEdgeAbility> ELEMENTAL_EDGE =
            ABILITY.register("elemental_edge", ElementalEdgeAbility::new);

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

    /**
     * The eye holds every projectile, lit charge and falling block around the caster still. See
     * {@link JouganStasisAbility}.
     */
    public static final RegistryObject<JouganStasisAbility> JOUGAN_STASIS =
            ABILITY.register("jougan_stasis", JouganStasisAbility::new);

    /**
     * The eye strips a disguise off the target and lights the body up. See
     * {@link JouganUnweaveAbility}.
     */
    public static final RegistryObject<JouganUnweaveAbility> JOUGAN_UNWEAVE =
            ABILITY.register("jougan_unweave", JouganUnweaveAbility::new);

    // ----------------------------------------------------------------------------------------
    // Byakugan techniques: the tenketsu line and the eye's far sight.
    // ----------------------------------------------------------------------------------------

    /** Needles of chakra fired into the tenketsu. See {@link TenketsuNeedlesAbility}. */
    public static final RegistryObject<TenketsuNeedlesAbility> TENKETSU_NEEDLES =
            ABILITY.register("tenketsu_needles", TenketsuNeedlesAbility::new);

    /** The palm that closes the channels beside the eyes. See {@link TenketsuBlindAbility}. */
    public static final RegistryObject<TenketsuBlindAbility> TENKETSU_BLIND =
            ABILITY.register("tenketsu_blind", TenketsuBlindAbility::new);

    /** The hold that pins a body by its chakra and drains it. See {@link ChakraGripAbility}. */
    public static final RegistryObject<ChakraGripAbility> CHAKRA_GRIP =
            ABILITY.register("chakra_grip", ChakraGripAbility::new);

    /** The torn tenketsu that stops a body healing. See {@link NodeRuptureAbility}. */
    public static final RegistryObject<NodeRuptureAbility> NODE_RUPTURE =
            ABILITY.register("node_rupture", NodeRuptureAbility::new);

    /** The stance that draws the world closer. See {@link ByakuganFarsightAbility}. */
    public static final RegistryObject<ByakuganFarsightAbility> BYAKUGAN_FARSIGHT =
            ABILITY.register("byakugan_farsight", ByakuganFarsightAbility::new);

    /** The stance that refuses invisibility and disguise. See {@link ByakuganMistSightAbility}. */
    public static final RegistryObject<ByakuganMistSightAbility> BYAKUGAN_MIST_SIGHT =
            ABILITY.register("byakugan_mist_sight", ByakuganMistSightAbility::new);

    /** The chakra-charged palms of the gentle fist. See {@link SoshikenAbility}. */
    public static final RegistryObject<SoshikenAbility> SOSHIKEN =
            ABILITY.register("soshiken", SoshikenAbility::new);

    /** The lunging palm of the gentle fist. See {@link PalmLanceAbility}. */
    public static final RegistryObject<PalmLanceAbility> PALM_LANCE =
            ABILITY.register("palm_lance", PalmLanceAbility::new);

    /** Eight palms walked in one motion. See {@link MountainCollapseAbility}. */
    public static final RegistryObject<MountainCollapseAbility> EIGHT_TRIGRAMS_COLLAPSE =
            ABILITY.register("eight_trigrams_collapse", MountainCollapseAbility::new);

    // ----------------------------------------------------------------------------------------
    // Clones, tools, wood and water, and the wind footwork.
    // ----------------------------------------------------------------------------------------

    /** Two illusory clones left behind to draw the eye. See {@link DecoyCloneAbility}. */
    public static final RegistryObject<DecoyCloneAbility> CLONE_DECOY =
            ABILITY.register("clone_decoy", DecoyCloneAbility::new);

    /** A clone that gives its caster's health back a little at a time. See {@link MedicCloneAbility}. */
    public static final RegistryObject<MedicCloneAbility> CLONE_MEDIC =
            ABILITY.register("clone_medic", MedicCloneAbility::new);

    /** Two shadow copies of a thrown shuriken. See {@link ShurikenShadowAbility}. */
    public static final RegistryObject<ShurikenShadowAbility> SHURIKEN_SHADOW =
            ABILITY.register("shuriken_shadow", ShurikenShadowAbility::new);

    /** Wood Release growth over a whole patch at once. See {@link MokutonGrowthAbility}. */
    public static final RegistryObject<MokutonGrowthAbility> MOKUTON_GROWTH =
            ABILITY.register("mokuton_growth", MokutonGrowthAbility::new);

    /** Water Release irrigation for the fields. See {@link WateringAbility}. */
    public static final RegistryObject<WateringAbility> SUITON_WATERING =
            ABILITY.register("suiton_watering", WateringAbility::new);


    /** The shoulder carry stance. See {@link BeastCarryAbility}. */
    public static final RegistryObject<BeastCarryAbility> BEAST_CARRY =
            ABILITY.register("beast_carry", BeastCarryAbility::new);

    /** Trading places with one of your own clones. See {@link CloneSwapAbility}. */
    public static final RegistryObject<CloneSwapAbility> CLONE_SWAP =
            ABILITY.register("clone_swap", CloneSwapAbility::new);

    /** A body of water wearing the caster's face. See {@link WaterCloneAbility}. */
    public static final RegistryObject<WaterCloneAbility> WATER_CLONE =
            ABILITY.register("water_clone", WaterCloneAbility::new);

    /** Two copies of packed mud that drag at the feet when broken. See {@link MudCloneAbility}. */
    public static final RegistryObject<MudCloneAbility> MUD_CLONE =
            ABILITY.register("mud_clone", MudCloneAbility::new);

    /** A clone packed with clay that goes off when it pops. See {@link ClayCloneAbility}. */
    public static final RegistryObject<ClayCloneAbility> CLAY_CLONE =
            ABILITY.register("clay_clone", ClayCloneAbility::new);

    /**
     * The combat focus stance: with it up, two keys reach the long physical techniques.
     * See {@link CombatStanceAbility}.
     */
    public static final RegistryObject<CombatStanceAbility> COMBAT_STANCE =
            ABILITY.register("combat_stance", CombatStanceAbility::new);

    // ----------------------------------------------------------------------------------------
    // Taijutsu and kenjutsu: the physical techniques traded for stamina.
    // ----------------------------------------------------------------------------------------

    /** The wind scythe that cuts projectiles out of the air. See {@link ProjectileCutAbility}. */
    public static final RegistryObject<ProjectileCutAbility> PROJECTILE_CUT =
            ABILITY.register("projectile_cut", ProjectileCutAbility::new);

    /** The unreadable sway of the drunken fist. See {@link DrunkenFistAbility}. */
    public static final RegistryObject<DrunkenFistAbility> DRUNKEN_FIST =
            ABILITY.register("drunken_fist", DrunkenFistAbility::new);

    // ----------------------------------------------------------------------------------------
    // Modes, seals and the Six Paths esoterica.
    // ----------------------------------------------------------------------------------------

    /** The shadow left in the border between worlds. See {@link LimboAbility}. */
    public static final RegistryObject<LimboAbility> LIMBO =
            ABILITY.register("limbo", LimboAbility::new);


    /** The Six Paths seal that mends an ally. See {@link YinYangHealingAbility}. */
    public static final RegistryObject<YinYangHealingAbility> YIN_YANG_HEAL =
            ABILITY.register("yin_yang_heal", YinYangHealingAbility::new);

    /** The first stage of the curse seal. See {@link CurseSealOneAbility}. */
    public static final RegistryObject<CurseSealOneAbility> CURSE_SEAL_ONE =
            ABILITY.register("curse_seal_one", CurseSealOneAbility::new);

    /** The second stage of the curse seal. See {@link CurseSealTwoAbility}. */
    public static final RegistryObject<CurseSealTwoAbility> CURSE_SEAL_TWO =
            ABILITY.register("curse_seal_two", CurseSealTwoAbility::new);

    /** A shell of chakra with no room for another technique. See {@link ChakraCloakAbility}. */
    public static final RegistryObject<ChakraCloakAbility> CHAKRA_CLOAK =
            ABILITY.register("chakra_cloak", ChakraCloakAbility::new);

    /** The cloak drawn out into a tail. See {@link TailedCloakAbility}. */
    public static final RegistryObject<TailedCloakAbility> TAILED_CLOAK =
            ABILITY.register("tailed_cloak", TailedCloakAbility::new);

    /** The charged mode. See {@link LightningModeAbility}. */
    public static final RegistryObject<LightningModeAbility> LIGHTNING_MODE =
            ABILITY.register("lightning_mode", LightningModeAbility::new);

    /** Shedding a wounded body. See {@link OrochimaruBodyAbility}. */
    public static final RegistryObject<OrochimaruBodyAbility> OROCHIMARU_BODY =
            ABILITY.register("orochimaru_body", OrochimaruBodyAbility::new);

    /** The Yamanaka transfer. See {@link MindTransferAbility}. */
    public static final RegistryObject<MindTransferAbility> MIND_TRANSFER =
            ABILITY.register("mind_transfer", MindTransferAbility::new);

    /** Partial Multi-Size. See {@link SizeExpansionAbility}. */
    public static final RegistryObject<SizeExpansionAbility> SIZE_EXPANSION =
            ABILITY.register("size_expansion", SizeExpansionAbility::new);

    /** The Kaguya bone blade. See {@link CamelliaDanceAbility}. */
    public static final RegistryObject<CamelliaDanceAbility> CAMELLIA_DANCE =
            ABILITY.register("camellia_dance", CamelliaDanceAbility::new);

    /** Five shards of bone. See {@link BoneBulletsAbility}. */
    public static final RegistryObject<BoneBulletsAbility> BONE_BULLETS =
            ABILITY.register("bone_bullets", BoneBulletsAbility::new);

    /** Grows a full set of bone tools. See {@link BoneToolsAbility}. */
    public static final RegistryObject<BoneToolsAbility> BONE_TOOLS =
            ABILITY.register("bone_tools", BoneToolsAbility::new);

    /** The sage's body opened all the way. See {@link SixPathsModeAbility}. */
    public static final RegistryObject<SixPathsModeAbility> SIX_PATHS_MODE =
            ABILITY.register("six_paths_mode", SixPathsModeAbility::new);

    /** One orb thrown along the look. See {@link GudodamaLaunchAbility}. */
    public static final RegistryObject<GudodamaLaunchAbility> GUDODAMA_LAUNCH =
            ABILITY.register("gudodama_launch", GudodamaLaunchAbility::new);

    /** The orbs held as a ring. See {@link GudodamaGuardAbility}. */
    public static final RegistryObject<GudodamaGuardAbility> GUDODAMA_GUARD =
            ABILITY.register("gudodama_guard", GudodamaGuardAbility::new);

    /** An orb shaped into a weapon. See {@link GudodamaFormAbility}. */
    public static final RegistryObject<GudodamaFormAbility> GUDODAMA_FORM =
            ABILITY.register("gudodama_form", GudodamaFormAbility::new);


    /** Sewing every portal shut for half a minute. See {@link SealPortalAbility}. */
    public static final RegistryObject<SealPortalAbility> SEAL_PORTAL =
            ABILITY.register("seal_portal", SealPortalAbility::new);

    /** Cutting an ender chest out of the seam. See {@link SewingPocketAbility}. */
    public static final RegistryObject<SewingPocketAbility> SEWING_POCKET =
            ABILITY.register("sewing_pocket", SewingPocketAbility::new);

    /** Sewing a door to its frame. See {@link SewingLockAbility}. */
    public static final RegistryObject<SewingLockAbility> SEWING_LOCK =
            ABILITY.register("sewing_lock", SewingLockAbility::new);

    /** The stance that cuts chakra out of a body. See {@link ChakraShearAbility}. */
    public static final RegistryObject<ChakraShearAbility> CHAKRA_SHEAR =
            ABILITY.register("chakra_shear", ChakraShearAbility::new);

    /** The cut that takes a panel out of a construction. See {@link StructureCutAbility}. */
    public static final RegistryObject<StructureCutAbility> STRUCTURE_CUT =
            ABILITY.register("structure_cut", StructureCutAbility::new);

    /** Walking out of phase with the world. See {@link JouganPhaseAbility}. */
    public static final RegistryObject<JouganPhaseAbility> JOUGAN_PHASE =
            ABILITY.register("jougan_phase", JouganPhaseAbility::new);

    /** The rod of the outer path. See {@link BlackRodAbility}. */
    public static final RegistryObject<BlackRodAbility> BLACK_ROD =
            ABILITY.register("black_rod", BlackRodAbility::new);

    /** Pulling loose items to the hand. See {@link ItemAttractionAbility}. */
    public static final RegistryObject<ItemAttractionAbility> ITEM_ATTRACTION =
            ABILITY.register("item_attraction", ItemAttractionAbility::new);

    /** Lifting a body off the ground. See {@link RinneganLevitationAbility}. */
    public static final RegistryObject<RinneganLevitationAbility> RINNEGAN_LEVITATION =
            ABILITY.register("rinnegan_levitation", RinneganLevitationAbility::new);

    /** Seeing the hollows behind the stone. See {@link VoidCastAbility}. */
    public static final RegistryObject<VoidCastAbility> VOID_CAST =
            ABILITY.register("void_cast", VoidCastAbility::new);

    /** Seeing plates, tripwires, charges and containers through walls. See {@link MechanismSightAbility}. */
    public static final RegistryObject<MechanismSightAbility> MECHANISM_SIGHT =
            ABILITY.register("mechanism_sight", MechanismSightAbility::new);

    /** The invisible trap left on the ground. See {@link HiddenSealAbility}. */
    public static final RegistryObject<HiddenSealAbility> HIDDEN_SEAL =
            ABILITY.register("hidden_seal", HiddenSealAbility::new);

    /** The healer's read. See {@link DoctorSightAbility}. */
    public static final RegistryObject<DoctorSightAbility> DOCTOR_SIGHT =
            ABILITY.register("doctor_sight", DoctorSightAbility::new);

    /** The kata that closes sixty-four tenketsu in one hold. See {@link SixtyFourPalmsAbility}. */
    public static final RegistryObject<SixtyFourPalmsAbility> SIXTY_FOUR_PALMS =
            ABILITY.register("sixty_four_palms", SixtyFourPalmsAbility::new);

    /** The mark of the watching eye. See {@link ByakuganMarkAbility}. */
    public static final RegistryObject<ByakuganMarkAbility> BYAKUGAN_MARK =
            ABILITY.register("byakugan_mark", ByakuganMarkAbility::new);

    /** The empty palm that pushes a body back. See {@link AirPalmAbility}. */
    public static final RegistryObject<AirPalmAbility> AIR_PALM =
            ABILITY.register("air_palm", AirPalmAbility::new);

    /** The turning guard. See {@link CircularBlockAbility}. */
    public static final RegistryObject<CircularBlockAbility> CIRCULAR_BLOCK =
            ABILITY.register("circular_block", CircularBlockAbility::new);

    /** The court of the king of hell. See {@link NarakaPathAbility}. */
    public static final RegistryObject<NarakaPathAbility> NARAKA_PATH =
            ABILITY.register("naraka_path", NarakaPathAbility::new);


    /** The three seekers. See {@link AsuraMissilesAbility}. */
    public static final RegistryObject<AsuraMissilesAbility> ASURA_MISSILES =
            ABILITY.register("asura_missiles", AsuraMissilesAbility::new);


    /** The chakra chains. See {@link OuterPathChainsAbility}. */
    public static final RegistryObject<OuterPathChainsAbility> OUTER_CHAINS =
            ABILITY.register("outer_chains", OuterPathChainsAbility::new);

    /** The outer-path shell. See {@link OuterPathArmorAbility}. */
    public static final RegistryObject<OuterPathArmorAbility> OUTER_ARMOR =
            ABILITY.register("outer_armor", OuterPathArmorAbility::new);

    /** The ribcage of a god. See {@link SusanooAbility}. */
    public static final RegistryObject<SusanooAbility> SUSANOO =
            ABILITY.register("susanoo", SusanooAbility::new);

    /** The copy that stands where the caster is not. See {@link IllusoryDoubleAbility}. */
    public static final RegistryObject<IllusoryDoubleAbility> ILLUSORY_DOUBLE =
            ABILITY.register("illusory_double", IllusoryDoubleAbility::new);

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

    /**
     * Resolves a short seal through the combat stance before the global combo map. The stance only
     * changes what the keys mean for the caster who has it up; the server still checks the unlock on
     * the ability that comes out of it.
     */
    public static boolean triggerAbility(long combo, Player player) {
        if (player != null) {
            var data = player.getCapability(NinjaCapabilityHandler.NINJA_DATA).resolve().orElse(null);
            var alias = CombatStanceAbility.resolve(player, data, combo);
            if (alias != null) {
                triggerAbility(alias);
                return true;
            }
        }
        return triggerAbility(combo);
    }


    @SubscribeEvent
    public static void clientSetup(FMLCommonSetupEvent event) {
        registerKeyCombos();
    }
}
