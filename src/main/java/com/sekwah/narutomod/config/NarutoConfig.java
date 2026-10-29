package com.sekwah.narutomod.config;

import com.sekwah.narutomod.NarutoMod;
import com.sekwah.narutomod.client.gui.BarDesigns;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = NarutoMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class NarutoConfig {

    public static final String SERVER = "server";
    public static final String CATEGORY_WEAPONS = "weapons";

    // Settings about players
    public static final String CATEGORY_PLAYER = "player";

    public static final String CLIENT = "client";

    public static final String CATEGORY_UI = "ui";
    public static final String ENERGY_BARS = "energy_bars";

    public static final String CATEGORY_KAMUI = "kamui";

    public static final String CATEGORY_DOJUTSU = "dojutsu";

    public static final String CATEGORY_FRIENDS = "friends";

    public static final ForgeConfigSpec MOD_CONFIG;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MAX_CHAKRA;
    public static float maxChakra;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MAX_STAMINA;
    public static float maxStamina;

    private static final ForgeConfigSpec.DoubleValue CONFIG_CHAKRA_REGEN;
    public static float chakraRegen;

    private static final ForgeConfigSpec.DoubleValue CONFIG_STAMINA_REGEN;
    public static float staminaRegen;

    private static final ForgeConfigSpec.IntValue CONFIG_MAX_SUBSTITUTIONS;
    public static int maxSubstitutions;

    private static final ForgeConfigSpec.IntValue CONFIG_SUBSTITUTION_REGEN_TIME;
    public static float substitutionRegenRate;

    private static final ForgeConfigSpec.BooleanValue CONFIG_KUNAI_BLOCK_DAMAGE;
    public static boolean kunaiBlockDamage;

    private static final ForgeConfigSpec.DoubleValue CONFIG_KUNAI_EXPLOSION_RADIUS;
    public static float kunaiExplosionRadius;

    private static final ForgeConfigSpec.DoubleValue CONFIG_PAPERBOMB_EXPLOSION_RADIUS;
    public static float paperbombExplosionRadius;

    private static final ForgeConfigSpec.BooleanValue CONFIG_PAPERBOMB_BLOCK_DAMAGE;
    public static boolean paperbombBlockDamage;

    private static final ForgeConfigSpec.BooleanValue CONFIG_TAILED_BALL_BLOCK_DAMAGE;
    public static boolean tailedBallBlockDamage;

    private static final ForgeConfigSpec.IntValue CONFIG_JUTSU_KEYBIND_HOLD_THRESHOLD;
    public static int jutsuKeybindHoldThreshold;

    private static final ForgeConfigSpec.IntValue CONFIG_JUTSU_CAST_DELAY;
    public static int jutsuCastDelay;

    private static final ForgeConfigSpec.IntValue CONFIG_CHAKRA_BAR_DESIGN;
    public static int chakraBarDesign;

    private static final ForgeConfigSpec.BooleanValue CONFIG_KAMUI_ENABLED;
    public static boolean kamuiEnabled;

    private static final ForgeConfigSpec.IntValue CONFIG_KAMUI_TRANSFER_SECONDS;
    public static int kamuiTransferSeconds;

    private static final ForgeConfigSpec.BooleanValue CONFIG_RINNEGAN_ENABLED;
    public static boolean rinneganEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_CHIBAKU_TENSEI_ENABLED;
    public static boolean chibakuTenseiEnabled;

    private static final ForgeConfigSpec.IntValue CONFIG_CHIBAKU_TENSEI_RADIUS;
    public static int chibakuTenseiRadius;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SHARINGAN_ENABLED;
    public static boolean sharinganEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_BYAKUGAN_ENABLED;
    public static boolean byakuganEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_JOUGAN_ENABLED;
    public static boolean jouganEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SENRIGAN_ENABLED;
    public static boolean senriganEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_FRIENDS_ENABLED;
    public static boolean friendsEnabled;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MAX_SENIN_MANA;
    public static float maxSeninMana;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SAGE_MODE_ENABLED;
    public static boolean sageModeEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SAGE_BUFFS_ENABLED;
    public static boolean sageBuffsEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SAGE_KATA_ENABLED;
    public static boolean sageKataEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_HENGE_ENABLED;
    public static boolean hengeEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_HENGE_REACTIONS_ENABLED;
    public static boolean hengeReactionsEnabled;

    // Mastery: the more a technique is used, the cheaper it becomes.

    private static final ForgeConfigSpec.BooleanValue CONFIG_MASTERY_ENABLED;
    public static boolean masteryEnabled;

    private static final ForgeConfigSpec.IntValue CONFIG_MASTERY_HALFWAY_CASTS;
    public static int masteryHalfwayCasts;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MASTERY_EXPONENT;
    public static double masteryExponent;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MASTERY_COST_REDUCTION;
    public static double masteryCostReduction;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MASTERY_COOLDOWN_REDUCTION;
    public static double masteryCooldownReduction;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MASTERY_SEAL_FREE;
    public static double masterySealFree;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MEDITATION_RADIUS_MAX;
    public static double meditationRadiusMax;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MEDITATION_PASSIVE_THRESHOLD;
    public static double meditationPassiveThreshold;

    private static final ForgeConfigSpec.DoubleValue CONFIG_MEDITATION_PASSIVE_CAPACITY;
    public static double meditationPassiveCapacity;

    private static final ForgeConfigSpec.DoubleValue CONFIG_SENSING_RADIUS_MAX;
    public static double sensingRadiusMax;

    private static final ForgeConfigSpec.DoubleValue CONFIG_SENSING_NIGHT_VISION_COST;
    public static double sensingNightVisionChakraPerSecond;

    private static final ForgeConfigSpec.BooleanValue CONFIG_WIDE_DIG_ENABLED;
    public static boolean wideDigEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_ORE_SENSE_ENABLED;
    public static boolean oreSenseEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_ADHESION_ENABLED;
    public static boolean adhesionEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_ADAPTATION_ENABLED;
    public static boolean adaptationEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SMOKE_ENABLED;
    public static boolean smokeEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SMOKE_BLIND_PLAYERS;
    public static boolean smokeBlindPlayers;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SILENCE_ENABLED;
    public static boolean silenceEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_ENVIRONMENT_ENABLED;
    public static boolean environmentEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SCROLL_LOOT_ENABLED;
    public static boolean scrollLootEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SLEEP_RESTORES_CHAKRA;
    public static boolean sleepRestoresChakra;

    private static final ForgeConfigSpec.BooleanValue CONFIG_CHAKRA_FLOW_ENABLED;
    public static boolean chakraFlowEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_CHAKRA_EXHAUSTION_ENABLED;
    public static boolean chakraExhaustionEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_JUTSU_LOUDNESS_ENABLED;
    public static boolean jutsuLoudnessEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_MEDITATION_AMBIENCE_ENABLED;
    public static boolean meditationAmbienceEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_LIGHTNING_CHARGES_BATTERIES;
    public static boolean lightningChargesBatteries;

    private static final ForgeConfigSpec.BooleanValue CONFIG_MARKER_RESPAWN_ENABLED;
    public static boolean markerRespawnEnabled;

    private static final ForgeConfigSpec.IntValue CONFIG_SENTRY_SEAL_RADIUS;
    public static int sentrySealRadius;

    private static final ForgeConfigSpec.BooleanValue CONFIG_SENTRY_SEAL_SUPPRESSES_SPAWNS;
    public static boolean sentrySealSuppressesSpawns;

    private static final ForgeConfigSpec.BooleanValue CONFIG_ELEMENTAL_STATES_ENABLED;
    public static boolean elementalStatesEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_NINJA_RANKS_ENABLED;
    public static boolean ninjaRanksEnabled;

    private static final ForgeConfigSpec.BooleanValue CONFIG_GENJUTSU_ON_PLAYERS;
    public static boolean genjutsuOnPlayers;

    //private static final ForgeConfigSpec.BooleanValue CONFIG_STARTS_AS_NINJA;
    //public static boolean startsAsNinja;

    static {
        ForgeConfigSpec.Builder configBuilder = new ForgeConfigSpec.Builder();

        // ===========================================================
        // Server Settings
        // ===========================================================
        configBuilder.comment("Server based settings").push(SERVER);

        configBuilder.comment("Player settings").push(CATEGORY_PLAYER);

//        CONFIG_STARTS_AS_NINJA = configBuilder.comment("Does the player start as a ninja by default?")
//                .define("startsAsNinja", true);

        configBuilder.comment("Stuff such as regen rates and maximum (will likely change with updates, e.g. stats system)").push(ENERGY_BARS);

        CONFIG_MAX_CHAKRA = configBuilder.comment("Max Chakra")
                .defineInRange("maxChakra",  25F ,  0d, Double.MAX_VALUE);

        CONFIG_CHAKRA_REGEN = configBuilder.comment("Chakra Regen")
                .defineInRange("chakraRegen",  0.05d ,  0d, Double.MAX_VALUE);

        CONFIG_MAX_STAMINA = configBuilder.comment("Max Stamina")
                .defineInRange("maxStamina",  100F ,  0d, Double.MAX_VALUE);

        CONFIG_STAMINA_REGEN = configBuilder.comment("Stamina Regen")
                .defineInRange("staminaRegen",  0.4d ,  0d, Double.MAX_VALUE);

        CONFIG_MAX_SUBSTITUTIONS = configBuilder.comment("Max Substitutions (base; skill tree nodes add more)")
                .defineInRange("maxSubstitutions",  1 ,  0, Integer.MAX_VALUE);

        CONFIG_SUBSTITUTION_REGEN_TIME = configBuilder.comment("Substitution Regen Time (Seconds)")
                        .defineInRange("substitutionRegenTime",  60 ,  0, Integer.MAX_VALUE);

        CONFIG_MAX_SENIN_MANA = configBuilder.comment("Max nature energy (senin mana) a ninja can gather, and the pool sage mode drains")
                .defineInRange("maxSeninMana",  100F ,  0d, Double.MAX_VALUE);

        CONFIG_SAGE_MODE_ENABLED = configBuilder.comment("Can players gather nature energy and enter sage mode?")
                .define("sageModeEnabled", true);

        CONFIG_SAGE_BUFFS_ENABLED = configBuilder.comment("Do the sage mode body perks apply: hunger stillness,"
                        + " free landings, halved stamina costs, periodic cleansing and senjutsu perception?")
                .define("sageBuffsEnabled", true);

        CONFIG_SAGE_KATA_ENABLED = configBuilder.comment("Does the frog kata strike around a sage's blows, and"
                        + " does sage mode lengthen melee reach?")
                .define("sageKataEnabled", true);

        CONFIG_HENGE_ENABLED = configBuilder.comment("Can players copy another player's skin and name with henge?")
                .define("hengeEnabled", true);

        CONFIG_HENGE_REACTIONS_ENABLED = configBuilder.comment("Do mobs react to a disguise? A face that"
                        + " belongs to a monster scares villagers into running and turns iron golems against"
                        + " the wearer. Off by default: it changes how the world's AI behaves.")
                .define("hengeReactionsEnabled", false);

        configBuilder.comment("Technique mastery: every cast makes a technique a little cheaper, and"
                + " mastery is never lost - dying keeps every point of it").push("mastery");

        CONFIG_MASTERY_ENABLED = configBuilder.comment("Should techniques get cheaper and faster the more they are used?")
                .define("masteryEnabled", true);

        CONFIG_MASTERY_HALFWAY_CASTS = configBuilder.comment("How many casts of a technique it takes to reach half of its mastery")
                .defineInRange("halfwayCasts", 40, 1, Integer.MAX_VALUE);

        CONFIG_MASTERY_EXPONENT = configBuilder.comment("How slow a technique's mastery is to get going: at 1 the first casts"
                        + " already pay off, higher values make the early casts worth almost nothing while keeping the"
                        + " halfway point at halfwayCasts (2 is the intended default)")
                .defineInRange("exponent", 2.0d, 0.1d, 10.0d);

        CONFIG_MASTERY_COST_REDUCTION = configBuilder.comment("How much cheaper a fully mastered technique gets (0.4 = 40% cheaper)")
                .defineInRange("costReduction", 0.4d, 0d, 0.95d);

        CONFIG_MASTERY_COOLDOWN_REDUCTION = configBuilder.comment("How much shorter a fully mastered technique's cooldown gets (0.4 = 40% shorter)")
                .defineInRange("cooldownReduction", 0.4d, 0d, 0.95d);

        CONFIG_MASTERY_SEAL_FREE = configBuilder.comment("Mastery at which the chakra seal is no longer needed at all, even for the multi shadow clone")
                .defineInRange("sealFreeAt", 0.9d, 0d, 1d);

        CONFIG_MEDITATION_RADIUS_MAX = configBuilder.comment("How far, in blocks, a fully mastered chakra meditation reaches at most")
                .defineInRange("meditationRadiusMax", 512d, 8d, 100000d);

        CONFIG_MEDITATION_PASSIVE_THRESHOLD = configBuilder.comment("Meditation mastery past which every meditating clone feeds its caster even out of range")
                .defineInRange("meditationPassiveAt", 0.95d, 0d, 1d);

        CONFIG_MEDITATION_PASSIVE_CAPACITY = configBuilder.comment("Chakra capacity each meditating clone adds while out of range past the threshold")
                .defineInRange("meditationPassiveCapacity", 25d, 0d, Double.MAX_VALUE);

        configBuilder.pop();

        configBuilder.comment("Chakra sensing: the held sense that shows what is around the caster, and the"
                + " tapped night vision trick").push("sensing");

        CONFIG_SENSING_RADIUS_MAX = configBuilder.comment("How far, in blocks, a fully mastered chakra sense reaches"
                        + " (an untrained sense always reaches 16). 96 is six chunks.")
                .defineInRange("radiusMax", 96d, 16d, 100000d);

        CONFIG_SENSING_NIGHT_VISION_COST = configBuilder.comment("Chakra burnt per second while the night vision trick is up")
                .defineInRange("nightVisionChakraPerSecond", 1.0d, 0d, 100d);

        configBuilder.pop();

        configBuilder.comment("Techniques that work the ground: the wide pick swing, and the ore sense"
                + " that reads the rock for a moment").push("mining");

        CONFIG_WIDE_DIG_ENABLED = configBuilder.comment("Can players hold the wide dig stance and break a"
                        + " 3x3 plane of blocks with every swing?")
                .define("wideDigEnabled", true);

        CONFIG_ORE_SENSE_ENABLED = configBuilder.comment("Can players cast the ore sense pulse? On a public"
                        + " server this is effectively a short-lived x-ray, so it can be turned off here.")
                .define("oreSenseEnabled", true);

        configBuilder.pop();

        configBuilder.comment("Stances and tricks that are not about the ground: sticking to a wall,"
                + " living in places the body should not, hiding in smoke, and running on chakra"
                + " instead of food").push("techniques");

        CONFIG_ADHESION_ENABLED = configBuilder.comment("Can players hold the chakra adhesion stance and"
                        + " climb along a wall they are pressed against?")
                .define("adhesionEnabled", true);

        CONFIG_ADAPTATION_ENABLED = configBuilder.comment("Can players hold the body adaptation stance,"
                        + " breathing water and shrugging off lava and powder snow?")
                .define("adaptationEnabled", true);

        CONFIG_SMOKE_ENABLED = configBuilder.comment("Can players cast the smoke screen?")
                .define("smokeEnabled", true);

        CONFIG_SMOKE_BLIND_PLAYERS = configBuilder.comment("Does the smoke screen also blind players caught"
                        + " in it? Turn this off to keep it a mob-only escape on a PvP server.")
                .define("smokeBlindPlayers", true);

        CONFIG_SILENCE_ENABLED = configBuilder.comment("Can players hold the silence stance, hiding their"
                        + " footsteps and blows from sculk sensors, shriekers, the warden and mob ears?")
                .define("silenceEnabled", true);

        configBuilder.pop();

        configBuilder.pop();

        configBuilder.comment("How the world around the caster changes techniques: weather, biome and"
                + " dimension make a breath reach further or hit softer").push("world");

        CONFIG_ENVIRONMENT_ENABLED = configBuilder.comment("Do weather, biome and dimension affect the"
                        + " elemental breaths (and how fast nature energy gathers)?")
                .define("environmentEnabled", true);

        CONFIG_SCROLL_LOOT_ENABLED = configBuilder.comment("Do the mod's scrolls and kit turn up in vanilla"
                        + " chests? Turn this off to keep chests exactly vanilla and hand scrolls out another way.")
                .define("scrollLootEnabled", true);

        CONFIG_SLEEP_RESTORES_CHAKRA = configBuilder.comment("Does sleeping through the night refill chakra and"
                        + " nature energy? Only a natural morning wake-up counts, never being woken by damage.")
                .define("sleepRestoresChakra", true);

        CONFIG_CHAKRA_FLOW_ENABLED = configBuilder.comment("Does filling the chakra bar by hand grant the short"
                        + " chakra flow effect (a faster passive trickle for a while)?")
                .define("chakraFlowEnabled", true);

        CONFIG_CHAKRA_EXHAUSTION_ENABLED = configBuilder.comment("Does an almost empty chakra pool slow the"
                        + " caster down? It is a soft penalty that removes itself as soon as the pool recovers.")
                .define("chakraExhaustionEnabled", true);

        CONFIG_JUTSU_LOUDNESS_ENABLED = configBuilder.comment("Do technique casts make noise the world can hear"
                        + " (sculk sensors, shriekers, the warden, mob ears)? Silent techniques stay silent.")
                .define("jutsuLoudnessEnabled", true);

        CONFIG_MEDITATION_AMBIENCE_ENABLED = configBuilder.comment("Do meditating clones hum and give off a soft"
                        + " glow while they are feeding their caster? Purely cosmetic.")
                .define("meditationAmbienceEnabled", true);

        CONFIG_LIGHTNING_CHARGES_BATTERIES = configBuilder.comment("Does lightning striking a lightning rod"
                        + " refill the chakra batteries lying or carried nearby? The only free way to charge one.")
                .define("lightningChargesBatteries", true);

        CONFIG_MARKER_RESPAWN_ENABLED = configBuilder.comment("Does a standing chakra marker log act as a respawn"
                        + " point when the ninja has no bed or respawn anchor? Beds and anchors always win.")
                .define("markerRespawnEnabled", true);

        CONFIG_SENTRY_SEAL_RADIUS = configBuilder.comment("How far a sentry seal watches for hostile mobs,"
                        + " in blocks. Also the radius kept clear of spawns when suppression is on.")
                .defineInRange("sentrySealRadius", 16, 4, 64);

        CONFIG_SENTRY_SEAL_SUPPRESSES_SPAWNS = configBuilder.comment("Does a sentry seal stop hostile mobs"
                        + " from spawning inside its radius? Off by default; spawner blocks are never affected.")
                .define("sentrySealSuppressesSpawns", false);

        CONFIG_ELEMENTAL_STATES_ENABLED = configBuilder.comment("Do the breaths leave elemental states behind?"
                        + " A soaked creature cannot catch fire and takes less fire damage; cold water chills"
                        + " a creature, slowing it until it warms up.")
                .define("elementalStatesEnabled", true);

        CONFIG_NINJA_RANKS_ENABLED = configBuilder.comment("Do ninja ranks (Genin, Chunin, Jonin, S-rank) exist?"
                        + " Ranks are milestones worked out from learned skill nodes and boss milestones, and"
                        + " are shown as advancements. Turn off to keep the ladder out of the game entirely.")
                .define("ninjaRanksEnabled", true);

        CONFIG_GENJUTSU_ON_PLAYERS = configBuilder.comment("May genjutsu be turned on players as well as mobs?"
                        + " Off by default: illusions on another player are a PvP tool and many servers do"
                        + " not want them, so the technique refuses a player target unless this is on.")
                .define("genjutsuOnPlayers", true);

        configBuilder.pop();

        configBuilder.comment("Variables for weapons").push(CATEGORY_WEAPONS);

        CONFIG_KUNAI_BLOCK_DAMAGE = configBuilder.comment("Explosive Kunai block damage")
                .define("kunaiExplosionBreakBlocks", true);

        CONFIG_KUNAI_EXPLOSION_RADIUS = configBuilder.comment("Explosive Kunai explosion radius")
                .defineInRange("kunaiExplosionRadius",  3d ,  1d, 100d);

        CONFIG_PAPERBOMB_EXPLOSION_RADIUS = configBuilder.comment("Paper Bomb explosion radius")
                .defineInRange("paperBombExplosionRadius",  4.0F ,  1d, 100d);

        CONFIG_PAPERBOMB_BLOCK_DAMAGE = configBuilder.comment("Paper Bomb block damage")
                .define("paperBombExplosionBreakBlocks", true);

        CONFIG_TAILED_BALL_BLOCK_DAMAGE = configBuilder.comment("Tailed Beast Bomb block damage. Off by default: the bomb wounds but breaks nothing. An operator can flip it mid-game with '/naruto debug bomb'.")
                .define("tailedBallExplosionBreakBlocks", false);

        CONFIG_JUTSU_KEYBIND_HOLD_THRESHOLD = configBuilder.comment("Key hold threshold in ticks (20 per second)")
                .defineInRange("jutsuKeyHoldThreshold", 15, 0, Integer.MAX_VALUE);

        CONFIG_JUTSU_CAST_DELAY = configBuilder.comment("Jutsu activation delay (20 per second)")
                .defineInRange("jutsuActivateDelay", 15, 0, Integer.MAX_VALUE);

        configBuilder.pop();

        configBuilder.comment("Kamui and the pocket dimension it opens").push(CATEGORY_KAMUI);

        CONFIG_KAMUI_ENABLED = configBuilder.comment("Can players use Kamui to step into the pocket dimension, and to drag targets in with them?")
                .define("kamuiEnabled", true);

        CONFIG_KAMUI_TRANSFER_SECONDS = configBuilder.comment("Seconds anything dragged into Kamui (anyone but the caster) spends in the void before it is spat back out where it was taken from")
                .defineInRange("kamuiTransferSeconds", 30, 1, 600);

        configBuilder.pop();

        configBuilder.comment("The eye bloodlines: a stance that is paid for by the tick and opens one eye's powers")
                .push(CATEGORY_DOJUTSU);

        CONFIG_RINNEGAN_ENABLED = configBuilder.comment("Can players open the Rinnegan? It costs chakra every tick and grants extra chakra and nature energy while it is open.")
                .define("rinneganEnabled", true);

        CONFIG_CHIBAKU_TENSEI_ENABLED = configBuilder.comment("Can players cast Chibaku Tensei? It pulls the ground and everything standing on it into a ball in the sky, then brings it down. Requires an open Rinnegan. The ball breaks no blocks - see chibakuTenseiRadius for how big it gets.")
                .define("chibakuTenseiEnabled", true);

        CONFIG_CHIBAKU_TENSEI_RADIUS = configBuilder.comment("How big the Chibaku Tensei ball gets at a full charge, in blocks of radius. A tapped ball is a fifth of this. Only the picture, the reach of the pull and the weight of the landing scale with it: the technique never breaks blocks.")
                .defineInRange("chibakuTenseiRadius", 16, 6, 32);

        CONFIG_SHARINGAN_ENABLED = configBuilder.comment("Can players open the Sharingan? It costs chakra every tick and reads whoever the caster looks at; Amaterasu is built on it.")
                .define("sharinganEnabled", true);

        CONFIG_BYAKUGAN_ENABLED = configBuilder.comment("Can players open the Byakugan? It costs chakra every tick and shows every living body in a short bubble through the walls; Rotation is built on it.")
                .define("byakuganEnabled", true);

        CONFIG_JOUGAN_ENABLED = configBuilder.comment("Can players open the Jougan? It costs chakra every tick, grants extra capacity and lights up hostile chakra; Dimensional Step and Chakra Sever are built on it.")
                .define("jouganEnabled", true);

        CONFIG_SENRIGAN_ENABLED = configBuilder.comment("Can players open the Senrigan? It is the cheapest eye: it gives no power of its own, but it lets the caster watch through any of their own shadow clones.")
                .define("senriganEnabled", true);

        configBuilder.pop();

        configBuilder.comment("Friendships between players: a two-way pact that keeps the mod's techniques and shadow clones off a friend")
                .push(CATEGORY_FRIENDS);

        CONFIG_FRIENDS_ENABLED = configBuilder.comment("Can players add each other as friends? A friendship is two-way: the mod's techniques and shadow clones spare a friend, and a request can be answered from the friends button in the skill menu.")
                .define("friendsEnabled", true);

        configBuilder.pop();

        configBuilder.pop();

        // ===========================================================
        // Client Settings
        // ===========================================================
        configBuilder.comment("Client based settings").push(CLIENT);

        configBuilder.comment("Variables for UI").push(CATEGORY_UI);
        CONFIG_CHAKRA_BAR_DESIGN = configBuilder.comment("Design for the chara bar")
                .defineInRange("chakraBarDesign", 0, 0,  BarDesigns.BarInfo.values().length - 1);

        configBuilder.pop();

        configBuilder.pop();

        MOD_CONFIG = configBuilder.build();
    }

    public static void loadVariables() {
        maxChakra = CONFIG_MAX_CHAKRA.get().floatValue();
        maxStamina = CONFIG_MAX_STAMINA.get().floatValue();
        staminaRegen = CONFIG_STAMINA_REGEN.get().floatValue();
        chakraRegen = CONFIG_CHAKRA_REGEN.get().floatValue();
        maxSubstitutions = CONFIG_MAX_SUBSTITUTIONS.get();
        substitutionRegenRate = 1f / (CONFIG_SUBSTITUTION_REGEN_TIME.get() * 20f);
        kunaiBlockDamage = CONFIG_KUNAI_BLOCK_DAMAGE.get();
        kunaiBlockDamage = CONFIG_KUNAI_BLOCK_DAMAGE.get();
        kunaiExplosionRadius = CONFIG_KUNAI_EXPLOSION_RADIUS.get().floatValue();
        paperbombExplosionRadius = CONFIG_PAPERBOMB_EXPLOSION_RADIUS.get().floatValue();
        paperbombBlockDamage = CONFIG_PAPERBOMB_BLOCK_DAMAGE.get();
        tailedBallBlockDamage = CONFIG_TAILED_BALL_BLOCK_DAMAGE.get();
        jutsuKeybindHoldThreshold = CONFIG_JUTSU_KEYBIND_HOLD_THRESHOLD.get();
        chakraBarDesign = CONFIG_CHAKRA_BAR_DESIGN.get();
        jutsuCastDelay = CONFIG_JUTSU_CAST_DELAY
                .get();
        kamuiEnabled = CONFIG_KAMUI_ENABLED.get();
        kamuiTransferSeconds = CONFIG_KAMUI_TRANSFER_SECONDS.get();
        rinneganEnabled = CONFIG_RINNEGAN_ENABLED.get();
        chibakuTenseiEnabled = CONFIG_CHIBAKU_TENSEI_ENABLED.get();
        chibakuTenseiRadius = CONFIG_CHIBAKU_TENSEI_RADIUS.get();
        sharinganEnabled = CONFIG_SHARINGAN_ENABLED.get();
        byakuganEnabled = CONFIG_BYAKUGAN_ENABLED.get();
        jouganEnabled = CONFIG_JOUGAN_ENABLED.get();
        senriganEnabled = CONFIG_SENRIGAN_ENABLED.get();
        friendsEnabled = CONFIG_FRIENDS_ENABLED.get();
        maxSeninMana = CONFIG_MAX_SENIN_MANA.get().floatValue();
        sageModeEnabled = CONFIG_SAGE_MODE_ENABLED.get();
        sageBuffsEnabled = CONFIG_SAGE_BUFFS_ENABLED.get();
        sageKataEnabled = CONFIG_SAGE_KATA_ENABLED.get();
        hengeEnabled = CONFIG_HENGE_ENABLED.get();
        hengeReactionsEnabled = CONFIG_HENGE_REACTIONS_ENABLED.get();
        masteryEnabled = CONFIG_MASTERY_ENABLED.get();
        masteryHalfwayCasts = CONFIG_MASTERY_HALFWAY_CASTS.get();
        masteryExponent = CONFIG_MASTERY_EXPONENT.get();
        masteryCostReduction = CONFIG_MASTERY_COST_REDUCTION.get();
        masteryCooldownReduction = CONFIG_MASTERY_COOLDOWN_REDUCTION.get();
        masterySealFree = CONFIG_MASTERY_SEAL_FREE.get();
        meditationRadiusMax = CONFIG_MEDITATION_RADIUS_MAX.get();
        meditationPassiveThreshold = CONFIG_MEDITATION_PASSIVE_THRESHOLD.get();
        meditationPassiveCapacity = CONFIG_MEDITATION_PASSIVE_CAPACITY.get();
        sensingRadiusMax = CONFIG_SENSING_RADIUS_MAX.get();
        sensingNightVisionChakraPerSecond = CONFIG_SENSING_NIGHT_VISION_COST.get();
        wideDigEnabled = CONFIG_WIDE_DIG_ENABLED.get();
        oreSenseEnabled = CONFIG_ORE_SENSE_ENABLED.get();
        adhesionEnabled = CONFIG_ADHESION_ENABLED.get();
        adaptationEnabled = CONFIG_ADAPTATION_ENABLED.get();
        smokeEnabled = CONFIG_SMOKE_ENABLED.get();
        smokeBlindPlayers = CONFIG_SMOKE_BLIND_PLAYERS.get();
        silenceEnabled = CONFIG_SILENCE_ENABLED.get();
        environmentEnabled = CONFIG_ENVIRONMENT_ENABLED.get();
        scrollLootEnabled = CONFIG_SCROLL_LOOT_ENABLED.get();
        sleepRestoresChakra = CONFIG_SLEEP_RESTORES_CHAKRA.get();
        chakraFlowEnabled = CONFIG_CHAKRA_FLOW_ENABLED.get();
        chakraExhaustionEnabled = CONFIG_CHAKRA_EXHAUSTION_ENABLED.get();
        jutsuLoudnessEnabled = CONFIG_JUTSU_LOUDNESS_ENABLED.get();
        meditationAmbienceEnabled = CONFIG_MEDITATION_AMBIENCE_ENABLED.get();
        lightningChargesBatteries = CONFIG_LIGHTNING_CHARGES_BATTERIES.get();
        markerRespawnEnabled = CONFIG_MARKER_RESPAWN_ENABLED.get();
        sentrySealRadius = CONFIG_SENTRY_SEAL_RADIUS.get();
        sentrySealSuppressesSpawns = CONFIG_SENTRY_SEAL_SUPPRESSES_SPAWNS.get();
        elementalStatesEnabled = CONFIG_ELEMENTAL_STATES_ENABLED.get();
        ninjaRanksEnabled = CONFIG_NINJA_RANKS_ENABLED.get();
        genjutsuOnPlayers = CONFIG_GENJUTSU_ON_PLAYERS.get();
        //startsAsNinja = CONFIG_STARTS_AS_NINJA.get();
    }

    @SubscribeEvent
    public static void onLoad(final ModConfigEvent.Loading configEvent) {
        loadVariables();
    }

    /**
     * Flips the bomb's block damage mid-game, for the '/naruto debug bomb' command. Writes the
     * spec value as well as the baked one, so a later config save keeps the choice.
     */
    public static void setTailedBallBlockDamage(boolean breaksBlocks) {
        CONFIG_TAILED_BALL_BLOCK_DAMAGE.set(breaksBlocks);
        tailedBallBlockDamage = breaksBlocks;
    }

    @SubscribeEvent
    public static void onReload(final ModConfigEvent.Reloading configEvent) {
        loadVariables();
    }
}
