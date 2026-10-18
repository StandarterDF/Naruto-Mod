package com.sekwah.narutomod.skills;

import com.sekwah.narutomod.NarutoMod;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * One node of the skill tree.
 *
 * <p>The table in the static block is the whole tree: a node's branch, where it sits on the board,
 * what it costs in experience points, what it grants and which nodes have to be learned first. Adding
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
 * <h2>How the board is laid out</h2>
 * <p>The root sits in the middle and the branches radiate from it, one direction each. Inside a
 * branch the placement follows a few rules, and it is worth keeping them when adding a node - a
 * link that jumps across the board reads as a mistake even when the unlock is correct:</p>
 * <ul>
 *     <li>a technique never hangs directly off another technique: between two of them there is
 *     always a passive "training" node, so the chain reads technique - practice - technique and
 *     buying a technique is never just "the next box over";</li>
 *     <li>a node and every node it requires stand at most two grid steps apart (three for the
 *     branch trunks leaving the root);</li>
 *     <li>a branch grows outward, never back across itself: chains run in a straight line and
 *     siblings sit side by side;</li>
 *     <li>two parents of one node are mirrored around it, so the two links meet in a symmetric V;</li>
 *     <li>branches keep out of each other's boxes, so a family reads as one block on the board.</li>
 * </ul>
 * <p>{@code tools/skilltree_preview.py} renders this table offline and lints it against those rules,
 * so a layout can be planned (and looked at) without launching the game.</p>
 */
public final class Skill {

    private static final List<Skill> ALL = new ArrayList<>();

    static {
        // ---- The trunk every branch grows from (centre) ----------------------------------------
        node("root", SkillBranch.ROOT, 0, 0, 40,
                SkillEffect.MAX_CHAKRA, 50f);

        // ==== CHAKRA: a wider pool and a faster flow (north) ====================================
        // A symmetric diamond: every tier splits in two and merges back on the next one.
        node("chakra_flow", SkillBranch.CHAKRA, 0, -1, 60,
                SkillEffect.CHAKRA_REGEN, 0.2f, "root");
        node("chakra_reserve", SkillBranch.CHAKRA, -1, -2, 120,
                SkillEffect.MAX_CHAKRA, 120f, "chakra_flow");
        node("chakra_stream", SkillBranch.CHAKRA, 1, -2, 140,
                SkillEffect.CHAKRA_REGEN, 0.25f, "chakra_flow");
        node("chakra_pool", SkillBranch.CHAKRA, -1, -3, 240,
                SkillEffect.MAX_CHAKRA, 180f, "chakra_reserve");
        node("chakra_current", SkillBranch.CHAKRA, 1, -3, 240,
                SkillEffect.CHAKRA_REGEN, 0.3f, "chakra_stream");
        node("chakra_deep", SkillBranch.CHAKRA, 0, -4, 320,
                SkillEffect.MAX_CHAKRA, 200f, "chakra_pool", "chakra_current");
        node("chakra_ocean", SkillBranch.CHAKRA, -1, -5, 550,
                SkillEffect.CHAKRA_REGEN, 0.45f, "chakra_deep");
        node("chakra_pinnacle", SkillBranch.CHAKRA, 1, -5, 800,
                SkillEffect.MAX_CHAKRA, 400f, "chakra_deep");

        // ==== MIND: the senses and the illusions (north west) ===================================
        // Two mirrored chains: every technique is entered through a passive, never through another
        // technique, and the pair below the middle merges back into the capstone.
        unlock("mind_sensing", SkillBranch.MIND, -3, -2, 250,
                "ability:chakra_sensing", "root");
        node("mind_focus", SkillBranch.MIND, -4, -3, 200,
                SkillEffect.COOLDOWN_REDUCTION, 0.03f, "mind_sensing");
        unlock("mind_genjutsu", SkillBranch.MIND, -5, -4, 350,
                "ability:genjutsu", "mind_focus");
        unlock("mind_henge", SkillBranch.MIND, -3, -4, 300,
                "ability:henge", "mind_focus");
        node("mind_deceit", SkillBranch.MIND, -6, -5, 250,
                SkillEffect.COST_REDUCTION, 0.03f, "mind_genjutsu");
        node("mind_quiet", SkillBranch.MIND, -2, -5, 250,
                SkillEffect.COOLDOWN_REDUCTION, 0.03f, "mind_henge");
        unlock("mind_smoke", SkillBranch.MIND, -6, -6, 300,
                "ability:smoke_screen", "mind_deceit");
        unlock("mind_silence", SkillBranch.MIND, -2, -6, 300,
                "ability:silence", "mind_quiet");
        node("mind_illusion", SkillBranch.MIND, -4, -5, 1000,
                SkillEffect.COOLDOWN_REDUCTION, 0.06f, "mind_smoke", "mind_silence");

        // ==== AGILITY: a faster body, a heavier hand and free movement (west) ===================
        // Three parallel rows: damage on top, speed through the middle, stamina underneath. The
        // mobility unlocks at the far end sit behind their own training passives.
        node("agility_swift", SkillBranch.AGILITY, -2, 0, 60,
                SkillEffect.MOVEMENT_SPEED, 0.004f, "root");
        node("agility_mighty", SkillBranch.AGILITY, -3, -1, 120,
                SkillEffect.ATTACK_DAMAGE, 1f, "agility_swift");
        node("agility_reflex", SkillBranch.AGILITY, -3, 0, 140,
                SkillEffect.MOVEMENT_SPEED, 0.004f, "agility_swift");
        node("agility_breath", SkillBranch.AGILITY, -3, 1, 120,
                SkillEffect.STAMINA_REGEN, 0.4f, "agility_swift");
        node("agility_power", SkillBranch.AGILITY, -4, -1, 240,
                SkillEffect.ATTACK_DAMAGE, 1f, "agility_mighty");
        unlock("agility_jump", SkillBranch.AGILITY, -4, 0, 200,
                "ability:double_jump", "agility_reflex");
        node("agility_spring", SkillBranch.AGILITY, -4, 1, 240,
                SkillEffect.MAX_STAMINA, 20f, "agility_breath");
        node("agility_fury", SkillBranch.AGILITY, -5, -1, 450,
                SkillEffect.ATTACK_DAMAGE, 2f, "agility_power");
        node("agility_dash", SkillBranch.AGILITY, -5, 0, 300,
                SkillEffect.MOVEMENT_SPEED, 0.006f, "agility_jump");
        node("agility_grip", SkillBranch.AGILITY, -5, 1, 260,
                SkillEffect.STAMINA_REGEN, 0.3f, "agility_jump");
        node("agility_perfect", SkillBranch.AGILITY, -6, -1, 1000,
                SkillEffect.ATTACK_DAMAGE, 3f, "agility_fury");
        node("agility_blur", SkillBranch.AGILITY, -6, 0, 550,
                SkillEffect.MOVEMENT_SPEED, 0.008f, "agility_dash");
        unlock("agility_adhesion", SkillBranch.AGILITY, -6, 1, 260,
                "ability:chakra_adhesion", "agility_grip");
        node("agility_gale", SkillBranch.AGILITY, -7, -1, 750,
                SkillEffect.MOVEMENT_SPEED, 0.01f, "agility_blur");
        node("agility_legend", SkillBranch.AGILITY, -7, 0, 1000,
                SkillEffect.MOVEMENT_SPEED, 0.012f, "agility_blur");
        node("agility_drift", SkillBranch.AGILITY, -7, 1, 300,
                SkillEffect.MOVEMENT_SPEED, 0.004f, "agility_adhesion");
        unlock("agility_water_walk", SkillBranch.AGILITY, -8, 1, 260,
                "ability:water_walk", "agility_drift");

        // ==== SPACE: the eye of the void and the tricks around it (south west) =================
        // Mirrors MIND: one passive opens the pair of techniques below it, each of those is
        // followed by its own passive, and the two passives meet again at the capstone.
        unlock("space_marker", SkillBranch.SPACE, -3, 2, 250,
                "ability:chakra_marker", "root");
        node("space_anchor", SkillBranch.SPACE, -4, 3, 250,
                SkillEffect.COST_REDUCTION, 0.03f, "space_marker");
        unlock("space_thread", SkillBranch.SPACE, -5, 4, 300,
                "ability:chakra_thread", "space_anchor");
        unlock("space_adaptation", SkillBranch.SPACE, -3, 4, 300,
                "ability:body_adaptation", "space_anchor");
        node("space_pull", SkillBranch.SPACE, -6, 5, 350,
                SkillEffect.COST_REDUCTION, 0.04f, "space_thread");
        node("space_veil", SkillBranch.SPACE, -4, 5, 350,
                SkillEffect.COOLDOWN_REDUCTION, 0.04f, "space_adaptation");
        unlock("space_kamui", SkillBranch.SPACE, -6, 6, 750,
                "ability:kamui", "space_pull");
        unlock("space_phasing", SkillBranch.SPACE, -4, 6, 500,
                "ability:kamui_phasing", "space_veil");
        node("space_mastery", SkillBranch.SPACE, -5, 7, 1000,
                SkillEffect.COST_REDUCTION, 0.06f, "space_kamui", "space_phasing");

        // ==== BODY: life, stamina, hide and the defensive stances (south) ========================
        node("body_vigor", SkillBranch.BODY, 0, 1, 60,
                SkillEffect.MAX_HEALTH, 2f, "root");
        node("body_skin", SkillBranch.BODY, -1, 2, 120,
                SkillEffect.ARMOR, 1f, "body_vigor");
        node("body_endurance", SkillBranch.BODY, 0, 2, 140,
                SkillEffect.STAMINA_REGEN, 0.4f, "body_vigor");
        node("body_lungs", SkillBranch.BODY, 1, 2, 120,
                SkillEffect.MAX_STAMINA, 20f, "body_vigor");
        node("body_hearty", SkillBranch.BODY, -2, 3, 240,
                SkillEffect.MAX_HEALTH, 2f, "body_skin");
        node("body_breathe", SkillBranch.BODY, 0, 3, 320,
                SkillEffect.MAX_STAMINA, 40f, "body_endurance");
        node("body_iron", SkillBranch.BODY, 1, 3, 240,
                SkillEffect.ARMOR, 2f, "body_lungs");
        // The same lungs feed a second lane beside the armour one, running down the flank:
        // a thicker hide and a longer wind are two sides of one arm.
        node("body_ironlungs", SkillBranch.BODY, 2, 3, 400,
                SkillEffect.MAX_STAMINA, 40f, "body_lungs");
        node("body_steady", SkillBranch.BODY, 2, 4, 550,
                SkillEffect.STAMINA_REGEN, 0.4f, "body_ironlungs");
        node("body_tough", SkillBranch.BODY, -1, 4, 450,
                SkillEffect.MAX_HEALTH, 4f, "body_hearty");
        node("body_unbreak", SkillBranch.BODY, 0, 4, 800,
                SkillEffect.MAX_HEALTH, 6f, "body_breathe", "body_tough");
        node("body_plate", SkillBranch.BODY, 1, 4, 450,
                SkillEffect.ARMOR, 2f, "body_iron");
        // The four stances sit on one row, mirrored around the pair below them.
        unlock("body_fist", SkillBranch.BODY, -2, 5, 300,
                "ability:chakra_fist", "body_tough");
        unlock("body_regen", SkillBranch.BODY, -1, 5, 300,
                "ability:chakra_regeneration", "body_tough");
        unlock("body_shield", SkillBranch.BODY, 1, 5, 350,
                "ability:chakra_shield", "body_plate");
        unlock("body_feeding", SkillBranch.BODY, 2, 5, 300,
                "ability:chakra_feeding", "body_plate");
        // The two healing stances meet in a passive before the next technique.
        node("body_flow", SkillBranch.BODY, -1, 6, 450,
                SkillEffect.CHAKRA_REGEN, 0.2f, "body_regen", "body_shield");
        unlock("body_second_breath", SkillBranch.BODY, 0, 6, 550,
                "ability:second_breath", "body_flow");
        node("body_balance", SkillBranch.BODY, 0, 7, 450,
                SkillEffect.STAMINA_REGEN, 0.3f, "body_second_breath");
        unlock("body_senjutsu", SkillBranch.BODY, -1, 8, 500,
                "ability:senjutsu_charging", "body_balance");
        unlock("body_eight_gates", SkillBranch.BODY, 1, 8, 800,
                "ability:eight_gates", "body_balance");
        // The gates are paid for in stamina, so the arm that opens them carries on into the
        // endurance that feeds them.
        node("body_reservoir", SkillBranch.BODY, 2, 8, 800,
                SkillEffect.MAX_STAMINA, 60f, "body_eight_gates");
        node("body_heart", SkillBranch.BODY, 2, 9, 1000,
                SkillEffect.STAMINA_REGEN, 0.5f, "body_reservoir");
        node("body_enlighten", SkillBranch.BODY, 0, 9, 700,
                SkillEffect.MAX_CHAKRA, 100f, "body_senjutsu");
        unlock("body_sage_mode", SkillBranch.BODY, 0, 10, 950,
                "ability:sage_mode", "body_enlighten");

        // ==== CONTROL: cheaper and quicker techniques (east) ====================================
        // Three columns: cost, cooldown and regen, meeting again at the bottom.
        node("control_focus", SkillBranch.CONTROL, 2, 0, 60,
                SkillEffect.COST_REDUCTION, 0.03f, "root");
        node("control_efficient", SkillBranch.CONTROL, 3, -1, 120,
                SkillEffect.COST_REDUCTION, 0.03f, "control_focus");
        node("control_swift", SkillBranch.CONTROL, 3, 0, 120,
                SkillEffect.COOLDOWN_REDUCTION, 0.05f, "control_focus");
        node("control_flow", SkillBranch.CONTROL, 3, 1, 140,
                SkillEffect.CHAKRA_REGEN, 0.2f, "control_focus");
        node("control_frugal", SkillBranch.CONTROL, 4, -1, 240,
                SkillEffect.COST_REDUCTION, 0.04f, "control_efficient");
        node("control_precision", SkillBranch.CONTROL, 4, 0, 240,
                SkillEffect.COOLDOWN_REDUCTION, 0.05f, "control_swift");
        node("control_meditation", SkillBranch.CONTROL, 4, 1, 300,
                SkillEffect.CHAKRA_REGEN, 0.25f, "control_flow");
        node("control_economy", SkillBranch.CONTROL, 5, -1, 450,
                SkillEffect.COST_REDUCTION, 0.05f, "control_frugal");
        node("control_mastery", SkillBranch.CONTROL, 5, 0, 450,
                SkillEffect.COOLDOWN_REDUCTION, 0.06f, "control_precision");
        node("control_zen", SkillBranch.CONTROL, 5, 1, 800,
                SkillEffect.COOLDOWN_REDUCTION, 0.1f, "control_meditation", "control_mastery");

        // ==== CLONES: the clone family (north east) =============================================
        // Shadow Clone is the hub, but the variants are entered through a shared training passive;
        // recall and the supply chest each sit behind their own passive as well.
        unlock("clone_shadow", SkillBranch.CLONES, 3, -2, 150,
                "ability:shadow_clone", "root");
        node("clone_drill", SkillBranch.CLONES, 3, -3, 200,
                SkillEffect.MAX_CHAKRA, 60f, "clone_shadow");
        unlock("clone_chibi", SkillBranch.CLONES, 2, -4, 200,
                "ability:chibi_shadow_clone", "clone_drill");
        unlock("clone_multi", SkillBranch.CLONES, 3, -4, 260,
                "ability:multi_shadow_clone", "clone_drill");
        unlock("clone_meditation", SkillBranch.CLONES, 4, -4, 260,
                "ability:chakra_meditation", "clone_drill");
        node("clone_vitality", SkillBranch.CLONES, 2, -5, 450,
                SkillEffect.MAX_CHAKRA, 100f, "clone_chibi", "clone_multi");
        node("clone_orders", SkillBranch.CLONES, 4, -5, 300,
                SkillEffect.COOLDOWN_REDUCTION, 0.04f, "clone_meditation");
        unlock("clone_recall", SkillBranch.CLONES, 4, -6, 260,
                "ability:clone_recall", "clone_orders");
        node("clone_mastery", SkillBranch.CLONES, 3, -6, 650,
                SkillEffect.COOLDOWN_REDUCTION, 0.06f, "clone_recall");
        node("clone_logistics", SkillBranch.CLONES, 5, -6, 300,
                SkillEffect.COST_REDUCTION, 0.03f, "clone_recall");
        unlock("clone_storage", SkillBranch.CLONES, 5, -7, 300,
                "ability:storage_chest", "clone_logistics");
        node("clone_legend", SkillBranch.CLONES, 2, -6, 1100,
                SkillEffect.MAX_CHAKRA, 250f, "clone_mastery", "clone_vitality");

        // ==== LABOUR: the worker trades, an extension of the clone knot (east of CLONES) ========
        // Workers are clones put to chores, so the branch leaves CLONES through one small node
        // ({@code labour_hands}) instead of hanging off the far side of the board, and the three
        // trades are opened by a single training passive after the farmhand.
        node("labour_hands", SkillBranch.LABOUR, 6, -6, 200,
                SkillEffect.MAX_STAMINA, 20f, "clone_storage");
        unlock("labour_worker", SkillBranch.LABOUR, 7, -6, 300,
                "worker:farm", "labour_hands");
        node("labour_grit", SkillBranch.LABOUR, 7, -5, 400,
                SkillEffect.STAMINA_REGEN, 0.3f, "labour_worker");
        unlock("labour_chop", SkillBranch.LABOUR, 6, -4, 450,
                "worker:chop", "labour_grit");
        unlock("labour_mine", SkillBranch.LABOUR, 7, -4, 450,
                "worker:mine", "labour_grit");
        unlock("labour_hunt", SkillBranch.LABOUR, 8, -4, 450,
                "worker:hunt", "labour_grit");
        node("labour_efficiency", SkillBranch.LABOUR, 6, -3, 750,
                SkillEffect.COOLDOWN_REDUCTION, 0.06f, "labour_chop", "labour_mine");
        node("labour_mastery", SkillBranch.LABOUR, 8, -3, 950,
                SkillEffect.MAX_CHAKRA, 150f, "labour_mine", "labour_hunt");

        // ==== ELEMENTAL: the five natures and the earth under them (south east) =================
        // Fire opens the branch; a fire-control passive then opens the three chains, and inside
        // each chain the techniques alternate with their own training passives.
        unlock("elem_fire", SkillBranch.ELEMENTAL, 3, 2, 150,
                "ability:fireball", "root");
        node("elem_ember", SkillBranch.ELEMENTAL, 4, 2, 200,
                SkillEffect.COST_REDUCTION, 0.03f, "elem_fire");
        unlock("elem_ore_excavation", SkillBranch.ELEMENTAL, 3, 3, 260,
                "ability:ore_excavation", "elem_ember");
        unlock("elem_campfire", SkillBranch.ELEMENTAL, 4, 3, 200,
                "ability:campfire", "elem_ember");
        unlock("elem_water", SkillBranch.ELEMENTAL, 5, 2, 200,
                "ability:water_bullet", "elem_ember");
        unlock("elem_fire_breath", SkillBranch.ELEMENTAL, 5, 3, 260,
                "ability:fire_breath", "elem_ember");
        node("elem_delving", SkillBranch.ELEMENTAL, 3, 4, 300,
                SkillEffect.STAMINA_REGEN, 0.3f, "elem_ore_excavation");
        unlock("elem_wide_dig", SkillBranch.ELEMENTAL, 3, 5, 300,
                "ability:wide_dig", "elem_delving");
        unlock("elem_ore_sense", SkillBranch.ELEMENTAL, 4, 5, 350,
                "ability:ore_sense", "elem_delving");
        node("elem_gust", SkillBranch.ELEMENTAL, 5, 4, 300,
                SkillEffect.COOLDOWN_REDUCTION, 0.04f, "elem_fire_breath");
        unlock("elem_wind_breath", SkillBranch.ELEMENTAL, 5, 5, 320,
                "ability:wind_breath", "elem_gust");
        node("elem_spiral", SkillBranch.ELEMENTAL, 5, 6, 400,
                SkillEffect.COST_REDUCTION, 0.04f, "elem_wind_breath");
        unlock("elem_rasengan", SkillBranch.ELEMENTAL, 5, 7, 550,
                "ability:rasengan", "elem_spiral");
        node("elem_fusion", SkillBranch.ELEMENTAL, 5, 8, 500,
                SkillEffect.COST_REDUCTION, 0.05f, "elem_rasengan");
        unlock("elem_rasenshuriken", SkillBranch.ELEMENTAL, 5, 9, 950,
                "ability:rasenshuriken", "elem_fusion");
        node("elem_tide", SkillBranch.ELEMENTAL, 6, 2, 300,
                SkillEffect.COOLDOWN_REDUCTION, 0.04f, "elem_water");
        unlock("elem_water_breath", SkillBranch.ELEMENTAL, 6, 3, 260,
                "ability:water_breath", "elem_tide");
        node("elem_bloom", SkillBranch.ELEMENTAL, 6, 4, 400,
                SkillEffect.CHAKRA_REGEN, 0.2f, "elem_water_breath");
        unlock("elem_wood_breath", SkillBranch.ELEMENTAL, 6, 5, 400,
                "ability:wood_breath", "elem_bloom");
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

    /** Price in experience points. */
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

    public String nameKey() {
        return "naruto.skill." + this.path + ".name";
    }

    public String descriptionKey() {
        return "naruto.skill." + this.path + ".desc";
    }
}
