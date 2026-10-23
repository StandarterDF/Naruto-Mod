package com.sekwah.narutomod.skills;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * One node of the skill tree.
 *
 * <p>The table in the static block is the whole tree: a node's branch, where it sits on the board,
 * what it costs in skill points, what it grants and which nodes have to be learned first. Adding
 * a new node is one line there plus its two language keys - nothing else has to be touched, because
 * the screen lays the tree out from {@link #x()} and {@link #y()} and the rest of the game only ever
 * asks {@link SkillTree#bonus} or {@link SkillTree#hasUnlock}.</p>
 *
 * <p>There are two kinds of node. A stat node grants one {@link SkillEffect} and is created with
 * {@link #node}; an unlock node teaches a mechanic or a technique and is created with
 * {@link #unlock}, carrying a key like {@code ability:fireball} or {@code worker:mine}. The unlock
 * keys are what the rest of the game asks for, so a new technique only has to be listed here to be
 * pulled behind the tree.</p>
 *
 * <p>This is not an enum: a tree that grows in every direction reads far better as a table, and the
 * branches need to be free to spread into negative coordinates. Everything else only ever sees
 * {@link #all()}.</p>
 *
 * <h2>The sections and the branches</h2>
 * <p>The tree is two levels deep. A {@link SkillSection} is only a grouping on the top row of the tab
 * bar (the physical path, the elements, the special techniques, the eye bloodlines) and holds no
 * nodes. Each {@link SkillBranch} inside it is its own board with its own centre and is bought on its
 * own: the centre has no prerequisites, everything else in the branch hangs off it or off another
 * node of the same branch, and there are deliberately no links from one branch to another. That way
 * the centre is the whole entry price of a discipline - once it is paid a caster is free to go for
 * whatever technique they want, without having to walk a cross-country route through four other
 * trees first.</p>
 *
 * <p>A branch only ever carries what belongs to it: the elemental boards hold their element's
 * techniques and the passives that train them, the clone board holds the clones and the worker
 * trades, and raw stats stay in {@link SkillBranch#CORE}. That is what keeps a "fire" tab from
 * turning into a second stat tree.</p>
 *
 * <p>The dependencies that are kept are the ones that are really there: a clone variant waits on the
 * plain shadow clone, the worker trades wait on the clone they are worked by, the rasenshuriken waits
 * on the rasengan, sage mode waits on gathering nature energy. The elemental breaths are independent
 * of each other on purpose - there is no reason a caster who can breathe fire should have had to
 * learn to breathe water first.</p>
 *
 * <h2>How a board is laid out</h2>
 * <p>Inside a tab the placement follows a few rules; they keep a link from reading as a mistake:</p>
 * <ul>
 *     <li>a technique never hangs directly off another technique: between two of them there is
 *     always a passive "training" node, so the chain reads technique - practice - technique;</li>
 *     <li>a node and the node it requires normally stand at most two grid steps apart (four for the
 *     star spokes leaving a tab centre);</li>
 *     <li>a chain runs in a straight line and siblings sit side by side;</li>
 *     <li>two parents of one node are mirrored around it, so the two links meet in a symmetric V;</li>
 *     <li>the families of one tab keep out of each other's quadrants.</li>
 * </ul>
 * <p>{@code tools/skilltree_preview.py} renders this table offline and lints it against those rules,
 * so a layout can be planned (and looked at) without launching the game.</p>
 */
public final class Skill {

    private static final List<Skill> ALL = new ArrayList<>();

    /**
     * The techniques that are not only learned but found: their node also asks for the matching
     * scroll, so the tree's deepest secrets have to be dug out of the world before they can be
     * bought. Everything else is learned with points alone.
     */
    private static final Set<String> SCROLL_GATED = Set.of(
            "ability:rasengan",
            "ability:rasenshuriken",
            "ability:eight_gates",
            "ability:kamui",
            "ability:sage_mode",
            "ability:earth_wall",
            "ability:rinnegan",
            "ability:chibaku_tensei",
            "ability:sharingan",
            "ability:byakugan",
            "ability:jougan");

    static {

        // =====================================================================================
        // 1. PHYSICAL PATH - raw pools, mobility, hand-to-hand and medical ninjutsu.
        //
        // CORE - Core: health, armour, chakra reserve and regeneration, cost and cooldown.
        node("root", SkillBranch.CORE, 0, 0, 4,
                SkillEffect.ALL_ROUNDED, 6f);
        node("body_vigor", SkillBranch.CORE, -1, -1, 10,
                SkillEffect.MAX_HEALTH, 1f, "root");
        node("body_hearty", SkillBranch.CORE, -2, -2, 19,
                SkillEffect.MAX_HEALTH, 1f, "body_vigor");
        node("body_tough", SkillBranch.CORE, -3, -3, 27,
                SkillEffect.MAX_HEALTH, 4f, "body_hearty");
        node("body_unbreak", SkillBranch.CORE, -4, -4, 36,
                SkillEffect.MAX_HEALTH, 6f, "body_tough");
        node("body_skin", SkillBranch.CORE, -1, 0, 14,
                SkillEffect.ARMOR, 1f, "root");
        node("body_iron", SkillBranch.CORE, -2, 0, 21,
                SkillEffect.ARMOR, 1f, "body_skin");
        node("body_plate", SkillBranch.CORE, -3, 0, 26,
                SkillEffect.ARMOR, 1f, "body_iron");
        node("chakra_reserve", SkillBranch.CORE, 0, -1, 13,
                SkillEffect.MAX_CHAKRA, 15f, "root");
        node("chakra_pool", SkillBranch.CORE, 0, -2, 19,
                SkillEffect.MAX_CHAKRA, 25f, "chakra_reserve");
        node("chakra_deep", SkillBranch.CORE, 0, -3, 26,
                SkillEffect.MAX_CHAKRA, 35f, "chakra_pool");
        node("chakra_pinnacle", SkillBranch.CORE, 0, -4, 36,
                SkillEffect.MAX_CHAKRA, 50f, "chakra_deep");
        node("chakra_flow", SkillBranch.CORE, 1, -1, 11,
                SkillEffect.CHAKRA_REGEN, 0.4f, "root");
        node("chakra_stream", SkillBranch.CORE, 2, -2, 17,
                SkillEffect.CHAKRA_REGEN, 0.5f, "chakra_flow");
        node("chakra_current", SkillBranch.CORE, 3, -3, 24,
                SkillEffect.CHAKRA_REGEN, 0.6f, "chakra_stream");
        node("control_flow", SkillBranch.CORE, 4, -4, 16,
                SkillEffect.CHAKRA_REGEN, 0.4f, "chakra_current");
        node("control_meditation", SkillBranch.CORE, 5, -5, 23,
                SkillEffect.CHAKRA_REGEN, 0.5f, "control_flow");
        node("chakra_ocean", SkillBranch.CORE, 6, -6, 42,
                SkillEffect.CHAKRA_REGEN, 0.9f, "control_meditation");
        node("control_focus", SkillBranch.CORE, 1, 0, 10,
                SkillEffect.COST_REDUCTION, 0.02f, "root");
        node("control_efficient", SkillBranch.CORE, 2, 0, 14,
                SkillEffect.COST_REDUCTION, 0.02f, "control_focus");
        node("control_frugal", SkillBranch.CORE, 3, 0, 19,
                SkillEffect.COST_REDUCTION, 0.02f, "control_efficient");
        node("control_economy", SkillBranch.CORE, 4, 0, 25,
                SkillEffect.COST_REDUCTION, 0.03f, "control_frugal");
        node("control_swift", SkillBranch.CORE, 0, 1, 14,
                SkillEffect.COOLDOWN_REDUCTION, 0.02f, "root");
        node("control_precision", SkillBranch.CORE, 0, 2, 19,
                SkillEffect.COOLDOWN_REDUCTION, 0.02f, "control_swift");
        node("control_mastery", SkillBranch.CORE, 0, 3, 25,
                SkillEffect.COOLDOWN_REDUCTION, 0.03f, "control_precision");
        node("control_zen", SkillBranch.CORE, 0, 4, 34,
                SkillEffect.COOLDOWN_REDUCTION, 0.05f, "control_mastery");
        //
        // ATHLETICS - Athletics: speed, stamina, the substitution, the double jump and water walking.
        node("agility_swift", SkillBranch.ATHLETICS, 1, 0, 10,
                SkillEffect.MOVEMENT_SPEED, 0.004f, "endurance");
        node("agility_reflex", SkillBranch.ATHLETICS, 2, 0, 14,
                SkillEffect.MOVEMENT_SPEED, 0.004f, "agility_swift");
        node("agility_drift", SkillBranch.ATHLETICS, 3, 0, 33,
                SkillEffect.MOVEMENT_SPEED, 0.004f, "agility_reflex");
        node("agility_dash", SkillBranch.ATHLETICS, 4, 0, 25,
                SkillEffect.MOVEMENT_SPEED, 0.006f, "agility_drift");
        node("agility_blur", SkillBranch.ATHLETICS, 5, 0, 32,
                SkillEffect.MOVEMENT_SPEED, 0.008f, "agility_dash");
        node("agility_gale", SkillBranch.ATHLETICS, 6, 0, 40,
                SkillEffect.MOVEMENT_SPEED, 0.01f, "agility_blur");
        node("agility_legend", SkillBranch.ATHLETICS, 7, 0, 42,
                SkillEffect.MOVEMENT_SPEED, 0.012f, "agility_gale");
        node("endurance", SkillBranch.ATHLETICS, 0, 0, 12,
                SkillEffect.STAMINA_REGEN, 0.2f);
        unlock("agility_water_walk", SkillBranch.ATHLETICS, 1, -2, 21,
                "ability:water_walk", "endurance");
        unlock("agility_adhesion", SkillBranch.ATHLETICS, 2, -1, 21,
                "ability:chakra_adhesion", "endurance");
        node("sub_instinct", SkillBranch.ATHLETICS, -1, 1, 15,
                SkillEffect.MAX_SUBSTITUTIONS, 1f, "endurance");
        node("sub_reserve", SkillBranch.ATHLETICS, -2, 2, 18,
                SkillEffect.MAX_SUBSTITUTIONS, 1f, "sub_instinct");
        node("sub_mastery", SkillBranch.ATHLETICS, -3, 3, 21,
                SkillEffect.MAX_SUBSTITUTIONS, 1f, "sub_reserve");
        node("sub_legend", SkillBranch.ATHLETICS, -4, 4, 24,
                SkillEffect.MAX_SUBSTITUTIONS, 1f, "sub_mastery");
        node("endurance_regen", SkillBranch.ATHLETICS, -5, 5, 26,
                SkillEffect.SUBSTITUTION_REGEN, 0.3f, "sub_legend");
        node("endurance_veil", SkillBranch.ATHLETICS, -6, 6, 24,
                SkillEffect.SUBSTITUTION_INVISIBILITY, 10f, "endurance_regen");
        node("body_lungs", SkillBranch.ATHLETICS, 0, -2, 14,
                SkillEffect.MAX_STAMINA, 20f, "endurance");
        node("agility_spring", SkillBranch.ATHLETICS, 0, -4, 18,
                SkillEffect.MAX_STAMINA, 20f, "body_lungs");
        node("body_breathe", SkillBranch.ATHLETICS, 0, -6, 20,
                SkillEffect.MAX_STAMINA, 40f, "agility_spring");
        node("body_ironlungs", SkillBranch.ATHLETICS, 0, -8, 20,
                SkillEffect.MAX_STAMINA, 40f, "body_breathe");
        node("body_reservoir", SkillBranch.ATHLETICS, 0, -10, 44,
                SkillEffect.MAX_STAMINA, 60f, "body_ironlungs");
        node("body_endurance", SkillBranch.ATHLETICS, -1, 0, 16,
                SkillEffect.STAMINA_REGEN, 0.4f, "endurance");
        node("agility_breath", SkillBranch.ATHLETICS, -2, 0, 16,
                SkillEffect.STAMINA_REGEN, 0.4f, "body_endurance");
        node("agility_grip", SkillBranch.ATHLETICS, -3, 0, 25,
                SkillEffect.STAMINA_REGEN, 0.3f, "agility_breath");
        node("body_steady", SkillBranch.ATHLETICS, -4, 0, 27,
                SkillEffect.STAMINA_REGEN, 0.4f, "agility_grip");
        node("body_heart", SkillBranch.ATHLETICS, -5, 0, 45,
                SkillEffect.STAMINA_REGEN, 0.5f, "body_steady");
        unlock("agility_jump", SkillBranch.ATHLETICS, 1, 1, 20,
                "ability:double_jump", "endurance");
        node("endurance_jump", SkillBranch.ATHLETICS, 2, 2, 21,
                SkillEffect.DOUBLE_JUMP_POWER, 0.25f, "agility_jump");
        node("endurance_jump2", SkillBranch.ATHLETICS, 3, 3, 28,
                SkillEffect.DOUBLE_JUMP_POWER, 0.35f, "endurance_jump");
        node("endurance_charge", SkillBranch.ATHLETICS, -1, -1, 18,
                SkillEffect.CHAKRA_CHARGE_RATE, 0.3f, "endurance");
        node("endurance_landing", SkillBranch.ATHLETICS, -2, -2, 21,
                SkillEffect.SOFT_LANDING, 0.3f, "endurance_charge");
        //
        // TAIJUTSU - Taijutsu: unarmed damage, the chakra fist and the Eight Gates.
        node("agility_mighty", SkillBranch.TAIJUTSU, 1, 0, 15,
                SkillEffect.ATTACK_DAMAGE, 1f, "taijutsu");
        node("agility_power", SkillBranch.TAIJUTSU, 2, 0, 20,
                SkillEffect.ATTACK_DAMAGE, 1f, "agility_mighty");
        node("agility_fury", SkillBranch.TAIJUTSU, 3, 0, 30,
                SkillEffect.ATTACK_DAMAGE, 1f, "agility_power");
        node("agility_perfect", SkillBranch.TAIJUTSU, 4, 0, 42,
                SkillEffect.ATTACK_DAMAGE, 3f, "agility_fury");
        node("tech_body", SkillBranch.TAIJUTSU, 0, -1, 12,
                SkillEffect.COST_REDUCTION, 0.02f, "taijutsu");
        unlock("body_fist", SkillBranch.TAIJUTSU, 0, -2, 15,
                "ability:chakra_fist", "tech_body");
        node("gates_core", SkillBranch.TAIJUTSU, -1, 0, 25,
                SkillEffect.STAMINA_REGEN, 0.3f, "taijutsu");
        node("gates_stamina", SkillBranch.TAIJUTSU, -1, -1, 22,
                SkillEffect.MAX_STAMINA, 30f, "gates_core");
        node("gates_stamina2", SkillBranch.TAIJUTSU, -1, -2, 30,
                SkillEffect.MAX_STAMINA, 40f, "gates_stamina");
        node("body_balance", SkillBranch.TAIJUTSU, -2, 0, 40,
                SkillEffect.STAMINA_REGEN, 0.3f, "gates_core");
        unlock("body_eight_gates", SkillBranch.TAIJUTSU, -3, 0, 104,
                "ability:eight_gates", "body_balance");
        node("taijutsu", SkillBranch.TAIJUTSU, 0, 0, 10,
                SkillEffect.ATTACK_DAMAGE, 1f);
        //
        // MEDICINE - Medicine: feeding, regeneration, the chakra shield and the second breath.
        node("technique", SkillBranch.MEDICINE, 0, 1, 15,
                SkillEffect.CHAKRA_REGEN, 0.5f);
        unlock("body_feeding", SkillBranch.MEDICINE, 1, 1, 21,
                "ability:chakra_feeding", "technique");
        unlock("body_regen", SkillBranch.MEDICINE, 0, 2, 40,
                "ability:chakra_regeneration", "technique");
        unlock("body_shield", SkillBranch.MEDICINE, -1, 2, 40,
                "ability:chakra_shield", "technique");
        node("body_flow", SkillBranch.MEDICINE, 0, 3, 37,
                SkillEffect.CHAKRA_REGEN, 0.4f, "body_regen", "body_shield");
        unlock("body_second_breath", SkillBranch.MEDICINE, 0, 4, 76,
                "ability:second_breath", "body_flow");

        // =====================================================================================
        // 2. NINJUTSU - one board per nature, each with its own centre.
        //
        // FIRE - Fire: the fireball, the campfire and the fire breath.
        node("elem_ember", SkillBranch.FIRE, 1, 1, 10,
                SkillEffect.COST_REDUCTION, 0.02f);
        unlock("elem_fire", SkillBranch.FIRE, 2, 1, 15,
                "ability:fireball", "elem_ember");
        unlock("elem_campfire", SkillBranch.FIRE, 1, 2, 10,
                "ability:campfire", "elem_ember");
        unlock("elem_fire_breath", SkillBranch.FIRE, 3, 2, 20,
                "ability:fire_breath", "elem_kindling");
        node("elem_kindling", SkillBranch.FIRE, 2, 2, 11,
                SkillEffect.COST_REDUCTION, 0.02f, "elem_fire");
        //
        // WATER - Water: the water bullet and the water breath.
        unlock("elem_water", SkillBranch.WATER, 1, 2, 15,
                "ability:water_bullet", "elem_spring");
        node("elem_tide", SkillBranch.WATER, 2, 2, 19,
                SkillEffect.COOLDOWN_REDUCTION, 0.02f, "elem_water");
        unlock("elem_water_breath", SkillBranch.WATER, 3, 2, 34,
                "ability:water_breath", "elem_tide");
        node("elem_spring", SkillBranch.WATER, 0, 2, 10,
                SkillEffect.COST_REDUCTION, 0.02f);
        //
        // EARTH - Earth: ore excavation, the wide dig and ore sense.
        unlock("elem_ore_excavation", SkillBranch.EARTH, 1, 0, 10,
                "ability:ore_excavation", "elem_delving");
        node("elem_delving", SkillBranch.EARTH, 0, 0, 20,
                SkillEffect.STAMINA_REGEN, 0.3f);
        unlock("elem_wide_dig", SkillBranch.EARTH, 0, 1, 18,
                "ability:wide_dig", "elem_delving");
        unlock("elem_ore_sense", SkillBranch.EARTH, -1, 0, 18,
                "ability:ore_sense", "elem_delving");
        node("elem_strata", SkillBranch.EARTH, 0, -1, 16,
                SkillEffect.ARMOR, 1f, "elem_delving");
        unlock("elem_earth_wall", SkillBranch.EARTH, 1, -1, 24,
                "ability:earth_wall", "elem_strata");
        //
        // WIND - Wind: the wind breath, the rasengan and the rasenshuriken.
        node("elem_gust", SkillBranch.WIND, 1, 0, 19,
                SkillEffect.COOLDOWN_REDUCTION, 0.02f);
        unlock("elem_wind_breath", SkillBranch.WIND, 2, 0, 34,
                "ability:wind_breath", "elem_gust");
        node("elem_spiral", SkillBranch.WIND, 3, 0, 28,
                SkillEffect.COST_REDUCTION, 0.02f, "elem_wind_breath");
        unlock("elem_rasengan", SkillBranch.WIND, 3, 1, 66,
                "ability:rasengan", "elem_spiral");
        node("elem_fusion", SkillBranch.WIND, 2, 1, 40,
                SkillEffect.COST_REDUCTION, 0.03f, "elem_rasengan");
        unlock("elem_rasenshuriken", SkillBranch.WIND, 1, 1, 104,
                "ability:rasenshuriken", "elem_fusion");
        //
        // WOOD - Wood: the growth breath.
        node("elem_bloom", SkillBranch.WOOD, 2, 0, 32,
                SkillEffect.CHAKRA_REGEN, 0.4f, "elem_sap");
        unlock("elem_wood_breath", SkillBranch.WOOD, 4, 0, 47,
                "ability:wood_breath", "elem_bloom");
        node("elem_sap", SkillBranch.WOOD, 0, 0, 14,
                SkillEffect.CHAKRA_REGEN, 0.2f);

        // =====================================================================================
        // 3. SPECIAL TECHNIQUES - clones, space, the mind and senjutsu.
        //
        // CLONES - Clones: every clone variant, the supply chest and the worker trades.
        node("clone_focus", SkillBranch.CLONES, 2, 1, 8,
                SkillEffect.MAX_CHAKRA, 10f);
        unlock("clone_shadow", SkillBranch.CLONES, 3, 1, 20,
                "ability:shadow_clone", "clone_focus");
        node("clone_drill", SkillBranch.CLONES, 3, 2, 16,
                SkillEffect.MAX_CHAKRA, 12f, "clone_shadow");
        unlock("clone_meditation", SkillBranch.CLONES, 4, 2, 18,
                "ability:chakra_meditation", "clone_drill");
        unlock("clone_chibi", SkillBranch.CLONES, 2, 2, 34,
                "ability:chibi_shadow_clone", "clone_drill");
        unlock("clone_multi", SkillBranch.CLONES, 3, 3, 65,
                "ability:multi_shadow_clone", "clone_drill");
        node("clone_vitality", SkillBranch.CLONES, 2, 3, 26,
                SkillEffect.MAX_CHAKRA, 18f, "clone_chibi", "clone_multi");
        node("clone_orders", SkillBranch.CLONES, 4, 3, 28,
                SkillEffect.COOLDOWN_REDUCTION, 0.02f, "clone_meditation");
        unlock("clone_recall", SkillBranch.CLONES, 4, 4, 24,
                "ability:clone_recall", "clone_orders");
        node("clone_mastery", SkillBranch.CLONES, 3, 4, 40,
                SkillEffect.COOLDOWN_REDUCTION, 0.03f, "clone_recall");
        node("clone_logistics", SkillBranch.CLONES, 5, 4, 37,
                SkillEffect.COST_REDUCTION, 0.02f, "clone_recall");
        unlock("clone_storage", SkillBranch.CLONES, 5, 3, 28,
                "ability:storage_chest", "clone_logistics");
        node("clone_legend", SkillBranch.CLONES, 2, 4, 42,
                SkillEffect.MAX_CHAKRA, 35f, "clone_mastery", "clone_vitality");
        node("clone_lifetime", SkillBranch.CLONES, 3, 5, 36,
                SkillEffect.CLONE_LIFETIME, 0.3f, "clone_mastery");
        node("labour_hands", SkillBranch.CLONES, 5, 2, 36,
                SkillEffect.MAX_STAMINA, 20f, "clone_storage");
        unlock("labour_worker", SkillBranch.CLONES, 5, 1, 54,
                "worker:farm", "labour_hands");
        node("labour_grit", SkillBranch.CLONES, 6, 1, 40,
                SkillEffect.STAMINA_REGEN, 0.3f, "labour_worker");
        unlock("labour_chop", SkillBranch.CLONES, 6, 0, 54,
                "worker:chop", "labour_grit");
        unlock("labour_mine", SkillBranch.CLONES, 7, 1, 54,
                "worker:mine", "labour_grit");
        unlock("labour_hunt", SkillBranch.CLONES, 6, 2, 54,
                "worker:hunt", "labour_grit");
        node("labour_efficiency", SkillBranch.CLONES, 6, 3, 40,
                SkillEffect.COOLDOWN_REDUCTION, 0.03f, "labour_hunt");
        node("labour_mastery", SkillBranch.CLONES, 8, 1, 36,
                SkillEffect.MAX_CHAKRA, 20f, "labour_mine");
        node("labour_lifetime", SkillBranch.CLONES, 6, 4, 40,
                SkillEffect.WORKER_LIFETIME, 0.3f, "labour_efficiency");
        //
        // SPACE - Space: the marker, the thread, body adaptation and Kamui.
        node("space_anchor", SkillBranch.SPACE, 0, 1, 10,
                SkillEffect.COST_REDUCTION, 0.02f);
        unlock("space_marker", SkillBranch.SPACE, 1, 1, 10,
                "ability:chakra_marker", "space_anchor");
        unlock("space_thread", SkillBranch.SPACE, 0, 2, 10,
                "ability:chakra_thread", "space_anchor");
        unlock("space_adaptation", SkillBranch.SPACE, -1, 1, 10,
                "ability:body_adaptation", "space_anchor");
        node("space_pull", SkillBranch.SPACE, 1, 2, 19,
                SkillEffect.COST_REDUCTION, 0.02f, "space_thread");
        node("space_veil", SkillBranch.SPACE, -1, 2, 19,
                SkillEffect.COOLDOWN_REDUCTION, 0.02f, "space_adaptation");
        unlock("space_kamui", SkillBranch.SPACE, 2, 3, 65,
                "ability:kamui", "space_pull");
        unlock("space_phasing", SkillBranch.SPACE, 0, 3, 48,
                "ability:kamui_phasing", "space_veil");
        node("space_mastery", SkillBranch.SPACE, 1, 3, 30,
                SkillEffect.COST_REDUCTION, 0.03f, "space_kamui", "space_phasing");
        //
        // MIND - Mind: sensing, night vision, genjutsu, henge, smoke and silence.
        node("mind_focus", SkillBranch.MIND, 0, 0, 9,
                SkillEffect.COOLDOWN_REDUCTION, 0.01f);
        unlock("mind_sensing", SkillBranch.MIND, 1, 0, 28,
                "ability:chakra_sensing", "mind_focus");
        unlock("mind_nightvision", SkillBranch.MIND, 1, -2, 20,
                "mechanic:night_vision", "mind_eyes");
        unlock("mind_genjutsu", SkillBranch.MIND, -1, 0, 20,
                "ability:genjutsu", "mind_focus");
        unlock("mind_henge", SkillBranch.MIND, 0, -1, 20,
                "ability:henge", "mind_focus");
        node("mind_deceit", SkillBranch.MIND, -1, -1, 19,
                SkillEffect.COST_REDUCTION, 0.02f, "mind_genjutsu");
        unlock("mind_smoke", SkillBranch.MIND, -1, -2, 34,
                "ability:smoke_screen", "mind_deceit");
        node("mind_quiet", SkillBranch.MIND, 0, -2, 17,
                SkillEffect.COOLDOWN_REDUCTION, 0.01f, "mind_henge");
        unlock("mind_silence", SkillBranch.MIND, 0, -3, 18,
                "ability:silence", "mind_quiet");
        node("mind_illusion", SkillBranch.MIND, -1, -3, 30,
                SkillEffect.COOLDOWN_REDUCTION, 0.03f, "mind_smoke");
        node("mind_eyes", SkillBranch.MIND, 1, -1, 11,
                SkillEffect.CHAKRA_REGEN, 0.2f, "mind_sensing");
        //
        // SAGE - Senjutsu: nature energy, its gathering and Sage Mode.
        node("sage_core", SkillBranch.SAGE, 1, 0, 20,
                SkillEffect.SENIN_MANA, 25f);
        unlock("body_senjutsu", SkillBranch.SAGE, 2, 0, 54,
                "ability:senjutsu_charging", "sage_core");
        node("sage_mana", SkillBranch.SAGE, 3, 0, 30,
                SkillEffect.SENIN_MANA, 50f, "body_senjutsu");
        node("sage_gather", SkillBranch.SAGE, 2, 1, 26,
                SkillEffect.SENIN_MANA_REGEN, 0.4f, "body_senjutsu");
        node("sage_entry", SkillBranch.SAGE, 2, 2, 32,
                SkillEffect.SAGE_ENTRY, 0.3f, "sage_gather");
        node("body_enlighten", SkillBranch.SAGE, 3, 2, 36,
                SkillEffect.MAX_CHAKRA, 20f, "sage_entry");
        unlock("body_sage_mode", SkillBranch.SAGE, 3, 1, 104,
                "ability:sage_mode", "body_enlighten");

        // =====================================================================================
        // 4. DOJUTSU - the eye bloodlines.
        //
        // SHARINGAN - the copy wheel: what it reads, its own capacity, and the black flame that
        // hangs off a training node rather than off the eye itself.
        node("sharingan_base", SkillBranch.SHARINGAN, 0, 0, 24,
                SkillEffect.CHAKRA_REGEN, 0.2f);
        node("sharingan_tomoe", SkillBranch.SHARINGAN, -1, 0, 28,
                SkillEffect.MAX_CHAKRA, 15f, "sharingan_base");
        node("sharingan_insight", SkillBranch.SHARINGAN, 1, 0, 32,
                SkillEffect.COST_REDUCTION, 0.04f, "sharingan_base");
        node("sharingan_read", SkillBranch.SHARINGAN, 1, -1, 34,
                SkillEffect.MAX_CHAKRA, 20f, "sharingan_insight");
        unlock("sharingan_eye", SkillBranch.SHARINGAN, 2, 0, 48,
                "ability:sharingan", "sharingan_insight");
        node("sharingan_flame", SkillBranch.SHARINGAN, 2, -1, 30,
                SkillEffect.MAX_CHAKRA, 15f, "sharingan_read");
        unlock("sharingan_amaterasu", SkillBranch.SHARINGAN, 3, -1, 64,
                "ability:amaterasu", "sharingan_flame");
        node("sharingan_genjutsu", SkillBranch.SHARINGAN, 0, 1, 30,
                SkillEffect.MAX_CHAKRA, 20f, "sharingan_base");
        node("sharingan_mangekyo", SkillBranch.SHARINGAN, 0, 2, 34,
                SkillEffect.CHAKRA_REGEN, 0.15f, "sharingan_genjutsu");
        node("sharingan_shape", SkillBranch.SHARINGAN, 3, 0, 36,
                SkillEffect.COST_REDUCTION, 0.03f, "sharingan_flame");
        unlock("sharingan_kagutsuchi", SkillBranch.SHARINGAN, 4, 0, 72,
                "ability:kagutsuchi", "sharingan_shape");

        // BYAKUGAN - the all-seeing eye: sight first, then the palms that answer what the sight
        // finds. The spin hangs off a training node, as every technique does.
        node("byakugan_base", SkillBranch.BYAKUGAN, 0, 0, 24,
                SkillEffect.CHAKRA_REGEN, 0.2f);
        node("byakugan_veins", SkillBranch.BYAKUGAN, -1, 0, 28,
                SkillEffect.MAX_CHAKRA, 15f, "byakugan_base");
        node("byakugan_sight", SkillBranch.BYAKUGAN, 1, 0, 30,
                SkillEffect.COST_REDUCTION, 0.04f, "byakugan_base");
        unlock("byakugan_eye", SkillBranch.BYAKUGAN, 2, 0, 48,
                "ability:byakugan", "byakugan_sight");
        node("byakugan_palm", SkillBranch.BYAKUGAN, 0, -1, 30,
                SkillEffect.MAX_CHAKRA, 20f, "byakugan_base");
        node("byakugan_rotation_prep", SkillBranch.BYAKUGAN, 0, -2, 32,
                SkillEffect.MAX_STAMINA, 15f, "byakugan_palm");
        unlock("byakugan_rotation", SkillBranch.BYAKUGAN, 1, -2, 56,
                "ability:rotation", "byakugan_rotation_prep");
        node("byakugan_tenketsu", SkillBranch.BYAKUGAN, 0, 1, 30,
                SkillEffect.MAX_CHAKRA, 20f, "byakugan_base");
        node("byakugan_tenketsu_mastery", SkillBranch.BYAKUGAN, 0, 2, 34,
                SkillEffect.CHAKRA_REGEN, 0.15f, "byakugan_tenketsu");

        // JOUGAN - the pure eye: it reads hostile chakra and carries the caster's reserves, and its
        // two techniques are one step into space and one cut through a body's chakra.
        node("jougan_base", SkillBranch.JOUGAN, 0, 0, 24,
                SkillEffect.CHAKRA_REGEN, 0.2f);
        node("jougan_pure", SkillBranch.JOUGAN, -1, 0, 28,
                SkillEffect.MAX_CHAKRA, 15f, "jougan_base");
        node("jougan_pool", SkillBranch.JOUGAN, -2, 0, 32,
                SkillEffect.MAX_CHAKRA, 25f, "jougan_pure");
        unlock("jougan_eye", SkillBranch.JOUGAN, 1, 0, 48,
                "ability:jougan", "jougan_base");
        node("jougan_senin", SkillBranch.JOUGAN, 1, -1, 30,
                SkillEffect.SENIN_MANA, 25f, "jougan_eye");
        unlock("jougan_step", SkillBranch.JOUGAN, 2, -1, 56,
                "ability:dimension_step", "jougan_senin");
        node("jougan_cut", SkillBranch.JOUGAN, 0, 1, 30,
                SkillEffect.COST_REDUCTION, 0.04f, "jougan_base");
        unlock("jougan_sever", SkillBranch.JOUGAN, 1, 1, 56,
                "ability:chakra_sever", "jougan_cut");
        node("jougan_vision", SkillBranch.JOUGAN, 0, 2, 34,
                SkillEffect.MAX_CHAKRA, 20f, "jougan_cut");
        // The eye's own training: what it reads in a body it can also strike, and the chakra flow it
        // follows around itself keeps the stance fed a little longer.
        node("jougan_strike", SkillBranch.JOUGAN, 2, 1, 34,
                SkillEffect.ATTACK_DAMAGE, 1.0f, "jougan_cut");
        node("jougan_focus", SkillBranch.JOUGAN, 1, 2, 32,
                SkillEffect.CHAKRA_REGEN, 0.2f, "jougan_vision");

        // RINNEGAN - Rinnegan: the eye of the sage, its capacity, attraction and Chibaku Tensei.
        // Every technique hangs off a passive rather than off another technique, so the branch
        // reads as training between one secret and the next.
        node("eye_rinn", SkillBranch.RINNEGAN, 0, 0, 24,
                SkillEffect.MAX_CHAKRA, 20f);
        node("eye_pool", SkillBranch.RINNEGAN, -1, 0, 26,
                SkillEffect.MAX_CHAKRA, 25f, "eye_rinn");
        node("eye_senin", SkillBranch.RINNEGAN, -1, -1, 30,
                SkillEffect.SENIN_MANA, 25f, "eye_pool");
        node("eye_focus", SkillBranch.RINNEGAN, 1, 0, 18,
                SkillEffect.CHAKRA_REGEN, 0.2f, "eye_rinn");
        unlock("eye_rinnegan", SkillBranch.RINNEGAN, 2, 0, 48,
                "ability:rinnegan", "eye_focus");
        node("eye_force", SkillBranch.RINNEGAN, 2, -1, 26,
                SkillEffect.MAX_CHAKRA, 15f, "eye_rinnegan");
        unlock("eye_push", SkillBranch.RINNEGAN, 2, -2, 40,
                "ability:universal_force", "eye_force");
        node("eye_gravity", SkillBranch.RINNEGAN, 1, -1, 32,
                SkillEffect.SENIN_MANA, 25f, "eye_rinnegan");
        unlock("eye_tensei", SkillBranch.RINNEGAN, 0, -1, 72,
                "ability:chibaku_tensei", "eye_gravity");
        // The palm eye hangs off the same eye as the rest of its powers, but on its own side of the
        // board: absorbing is a different answer to a fight than pulling or dropping a mountain.
        node("eye_palm", SkillBranch.RINNEGAN, 3, 0, 30,
                SkillEffect.COST_REDUCTION, 0.03f, "eye_rinnegan");
        unlock("eye_absorb", SkillBranch.RINNEGAN, 4, 0, 64,
                "ability:momoshiki_absorption", "eye_palm");

        // SENRIGAN - the far eye. It hands out no power, only a viewpoint, so the branch is short:
        // open the eye, train the sight, then learn to step the viewpoint through the caster's own
        // clones and to hold the body in the air on chakra.
        node("senrigan_base", SkillBranch.SENRIGAN, 0, 0, 24,
                SkillEffect.CHAKRA_REGEN, 0.2f);
        node("senrigan_focus", SkillBranch.SENRIGAN, -1, 0, 26,
                SkillEffect.MAX_CHAKRA, 20f, "senrigan_base");
        unlock("senrigan_eye", SkillBranch.SENRIGAN, -2, 0, 48,
                "ability:senrigan", "senrigan_focus");
        node("senrigan_far", SkillBranch.SENRIGAN, 1, 0, 30,
                SkillEffect.MAX_CHAKRA, 20f, "senrigan_base");
        node("senrigan_thread", SkillBranch.SENRIGAN, 1, -1, 34,
                SkillEffect.COST_REDUCTION, 0.03f, "senrigan_far");
        unlock("senrigan_sight", SkillBranch.SENRIGAN, 2, -1, 56,
                "ability:senrigan_sight", "senrigan_thread");
        node("senrigan_levity", SkillBranch.SENRIGAN, 2, 0, 34,
                SkillEffect.COST_REDUCTION, 0.03f, "senrigan_far");
        unlock("senrigan_flight", SkillBranch.SENRIGAN, 3, 0, 56,
                "ability:senrigan_flight", "senrigan_levity");

    }

    private final String path;
    private final SkillBranch branch;
    private final int x;
    private final int y;
    private final int cost;
    private final SkillEffect effect;
    private final float amount;
    private final String unlock;
    private final String[] requires;
    private final ResourceLocation id;

    private Skill(String path, SkillBranch branch, int x, int y, int cost,
                  SkillEffect effect, float amount, String unlock, String... requires) {
        this.path = path;
        this.branch = branch;
        this.x = x;
        this.y = y;
        this.cost = cost;
        this.effect = effect;
        this.amount = amount;
        this.unlock = unlock;
        this.requires = requires;
        this.id = new ResourceLocation(NarutoMod.MOD_ID, path);
    }

    /** A node that hands out a passive bonus. */
    private static void node(String path, SkillBranch branch, int x, int y, int cost,
                             SkillEffect effect, float amount, String... requires) {
        ALL.add(new Skill(path, branch, x, y, cost, effect, amount, null, requires));
    }

    /** A node that teaches a mechanic or a technique, named by its unlock key. */
    private static void unlock(String path, SkillBranch branch, int x, int y, int cost,
                               String unlockKey, String... requires) {
        ALL.add(new Skill(path, branch, x, y, cost, SkillEffect.UNLOCK, 0f, unlockKey, requires));
    }

    /** Every node of the tree, in declaration order. */
    public static List<Skill> all() {
        return ALL;
    }

    /** Registry-style id, also what the unlocked set stores. */
    public ResourceLocation id() {
        return this.id;
    }

    public String path() {
        return this.path;
    }

    public SkillBranch branch() {
        return this.branch;
    }

    /** Horizontal position on the board, in grid steps. */
    public int x() {
        return this.x;
    }

    /** Vertical position on the board, in grid steps. */
    public int y() {
        return this.y;
    }

    /** Price in skill points, bought with experience at {@link SkillPoints#XP_PER_POINT} per point. */
    public int cost() {
        return this.cost;
    }

    public SkillEffect effect() {
        return this.effect;
    }

    public float amount() {
        return this.amount;
    }

    /** The unlock key of an unlock node, or null for a plain bonus. */
    public String unlock() {
        return this.unlock;
    }

    /** Whether this node teaches something rather than granting a number. */
    public boolean isUnlock() {
        return this.unlock != null;
    }

    /** Paths of the nodes that must be learned before this one. */
    public String[] requires() {
        return this.requires;
    }

    /** Whether this node opens a tab: the one node of its branch that has no prerequisites. */
    public boolean isBranchCentre() {
        return this.requires.length == 0;
    }

    /** Whether this node also needs its scroll to have been found and read before it can be bought. */
    public boolean requiresScroll() {
        return this.unlock != null && SCROLL_GATED.contains(this.unlock);
    }

    /**
     * The technique a scroll for this node teaches, as a registry id, or null when no scroll is
     * needed. The scroll is keyed by the technique itself, so {@code ability:rasengan} asks for a
     * scroll that knows {@code narutomod:rasengan}.
     */
    public ResourceLocation scrollKnowledge() {
        if (!this.requiresScroll()) {
            return null;
        }
        return new ResourceLocation(NarutoMod.MOD_ID, this.unlock.substring("ability:".length()));
    }

    public String nameKey() {
        return "naruto.skill." + this.path + ".name";
    }

    public String descriptionKey() {
        return "naruto.skill." + this.path + ".desc";
    }
}
