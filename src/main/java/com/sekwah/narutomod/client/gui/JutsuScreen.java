package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.sekwah.narutomod.abilities.Ability;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.abilities.AbilityUnlocks;
import com.sekwah.narutomod.abilities.jutsus.ChakraSensingAbility;
import com.sekwah.narutomod.abilities.jutsus.ChakraShieldAbility;
import com.sekwah.narutomod.abilities.jutsus.HengeJutsuAbility;
import com.sekwah.narutomod.abilities.jutsus.SageModeAbility;
import com.sekwah.narutomod.abilities.jutsus.SecondBreathAbility;
import com.sekwah.narutomod.abilities.mastery.Mastery;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.client.keybinds.NarutoKeyHandler;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerAbilityActivatePacket;
import com.sekwah.narutomod.network.c2s.ServerAbilityChannelPacket;
import com.sekwah.narutomod.network.c2s.ServerToggleAbilityDisabledPacket;
import com.sekwah.narutomod.network.c2s.ServerToggleNinjaPacket;
import com.sekwah.narutomod.registries.NarutoRegistries;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The jutsu menu, opened with the jutsu key: the whole technique list on one page instead of telling
 * the player to go and run a command.
 *
 * <p>Techniques are listed by name with the keys that cast them, read from the same combo map the
 * casting itself uses, so the list can never drift away from what the keys actually do. They are
 * grouped by what they are for - clones together, elements together, and each eye's techniques under
 * that eye rather than all dojutsu in one lump - and within a group sorted by combo, which is far
 * easier to search through than one long run ordered by key presses. The groups are drawn as headers,
 * and a group that spills onto the next page keeps its header.</p>
 *
 * <p>The menu is a reference, so it remembers where the reader was: the page and the unlocked-only
 * filter are kept for the rest of the session, and closing the menu or stepping into the skill tree
 * and back opens it exactly where it was left. The filter hides techniques that are still locked,
 * which is the difference between "what can I cast" and "what is there to learn".</p>
 *
 * <p>The list is only worth showing to an actual ninja, so until the player has taken the title up
 * the menu is just the two lines explaining that and the button to do it; the techniques appear the
 * moment they do.</p>
 */
public class JutsuScreen extends Screen {

    /** How many lines fit on one page before the arrows have to be used. */
    private static final int ROWS_PER_PAGE = 10;
    private static final int ROW_HEIGHT = 11;

    /**
     * Width a hover description is wrapped to. Narrow enough to stay a tooltip rather than a wall
     * of text, wide enough that a normal sentence still fits on one line.
     */
    private static final int TOOLTIP_WIDTH = 190;

    /** Smallest width the panel will take, and how far the list runs from the middle of it. */
    private static final int MIN_PANEL_HALF_WIDTH = 132;

    /** The bottom row of buttons for a ninja: skill tree, done, and the unlocked-only filter. */
    private static final int BUTTON_WIDTH = 96;
    private static final int BUTTON_GAP = 4;
    private static final int NINJA_BUTTON_ROW_WIDTH = BUTTON_WIDTH * 3 + BUTTON_GAP * 2;

    /** The little mastery bar drawn on every technique row, next to its percentage. */
    private static final int BAR_WIDTH = 26;
    private static final int BAR_HEIGHT = 5;

    /** Fixed width of the percentage slot on the left of a row, so the numbers line up. */
    private static final String PERCENT_SLOT = "100%";

    /** Marker set before a technique that is running in the background right now, and its colour. */
    private static final String ACTIVE_MARKER = "[+]";
    private static final int ACTIVE_COLOUR = 0xFF55FF55;

    /**
     * A technique that is up without draining chakra every tick - a shield that is holding, a
     * second breath that is waiting, a disguise that is worn. Same marker, amber instead of green,
     * so "paying for this right now" and "this is still in effect" do not look alike.
     */
    private static final String ARMED_MARKER = "[*]";
    private static final int ARMED_COLOUR = 0xFFFFC94A;

    /** Suffix printed after a technique that is still locked behind the skill tree. */
    private static final Component LOCK_SUFFIX = Component.translatable("naruto.gui.jutsu.locked_suffix");

    /** Suffix printed after a technique the caster has switched off in the menu. */
    private static final Component DISABLED_SUFFIX = Component.translatable("naruto.gui.jutsu.disabled_suffix");

    /** Dimmed colour a locked technique's name and keys are drawn in. */
    private static final int LOCKED_COLOUR = 0xFF808080;

    /** Even dimmer: a technique that is learned but switched off, so it reads as "put aside". */
    private static final int DISABLED_COLOUR = 0xFF6A6A6A;

    /** The list starts this far below the panel top, matching {@link #renderTechniqueList}. */
    private static final int LIST_TOP_OFFSET = 42;

    /** How far in from the panel edge the list runs, on both sides. */
    private static final int LIST_PADDING = 10;

    /**
     * The groups the list is broken into, in the order they are shown. A technique that has its own
     * entry in {@link #CATEGORIES} goes there; everything else follows the branch of its skill-tree
     * node through {@link #BRANCHES}, so filing a technique in the tree also files it in this menu.
     * Only a technique with neither - a stray id nobody placed anywhere - falls into {@link #OTHER},
     * which keeps it visible instead of dropping it from the menu.
     */
    private enum Category {
        BASICS("naruto.gui.jutsu.category.basics"),
        BODY("naruto.gui.jutsu.category.body"),
        DEFENCE("naruto.gui.jutsu.category.defence"),
        ELEMENTAL("naruto.gui.jutsu.category.elemental"),
        TAIJUTSU("naruto.gui.jutsu.category.taijutsu"),
        CLONES("naruto.gui.jutsu.category.clones"),
        SPACE("naruto.gui.jutsu.category.space"),
        GATES("naruto.gui.jutsu.category.gates"),
        ILLUSION("naruto.gui.jutsu.category.illusion"),
        SHARINGAN("naruto.gui.jutsu.category.sharingan"),
        BYAKUGAN("naruto.gui.jutsu.category.byakugan"),
        JOUGAN("naruto.gui.jutsu.category.jougan"),
        SENRIGAN("naruto.gui.jutsu.category.senrigan"),
        RINNEGAN("naruto.gui.jutsu.category.rinnegan"),
        SIX_PATHS("naruto.gui.jutsu.category.six_paths"),
        SAGE("naruto.gui.jutsu.category.sage"),
        CURSE_SEAL("naruto.gui.jutsu.category.curse_seal"),
        TAILED("naruto.gui.jutsu.category.tailed"),
        OTHER("naruto.gui.jutsu.category.other");

        private final String translationKey;

        Category(String translationKey) {
            this.translationKey = translationKey;
        }
    }

    /** Which group each technique belongs to, keyed by the ability id's path. */
    private static final Map<String, Category> CATEGORIES = Map.ofEntries(
            Map.entry("chakra_charge", Category.BASICS),
            Map.entry("water_walk", Category.BASICS),
            Map.entry("chakra_fist", Category.BASICS),
            Map.entry("leap", Category.BASICS),
            Map.entry("blade_rain", Category.TAIJUTSU),

            Map.entry("shadow_clone", Category.CLONES),
            Map.entry("chibi_shadow_clone", Category.CLONES),
            Map.entry("multi_shadow_clone", Category.CLONES),
            Map.entry("chakra_meditation", Category.CLONES),
            Map.entry("clone_recall", Category.CLONES),
            Map.entry("shuriken_shadow", Category.CLONES),

            Map.entry("chakra_shield", Category.DEFENCE),

            Map.entry("chakra_regeneration", Category.DEFENCE),

            Map.entry("fireball", Category.ELEMENTAL),
            Map.entry("fire_breath", Category.ELEMENTAL),
            Map.entry("campfire", Category.ELEMENTAL),
            Map.entry("water_bullet", Category.ELEMENTAL),
            Map.entry("water_breath", Category.ELEMENTAL),
            Map.entry("wind_breath", Category.ELEMENTAL),
            Map.entry("wood_breath", Category.ELEMENTAL),
            Map.entry("rasengan", Category.ELEMENTAL),
            Map.entry("rasenshuriken", Category.ELEMENTAL),
            Map.entry("kirin", Category.ELEMENTAL),
            Map.entry("ore_excavation", Category.ELEMENTAL),
            Map.entry("wide_dig", Category.ELEMENTAL),
            Map.entry("earth_wall", Category.ELEMENTAL),

            Map.entry("substitution", Category.SPACE),
            Map.entry("chakra_marker", Category.SPACE),
            Map.entry("kamui", Category.SPACE),
            Map.entry("kamui_phasing", Category.SPACE),
            Map.entry("chakra_thread", Category.SPACE),

            Map.entry("senjutsu_charging", Category.SAGE),
            Map.entry("sage_mode", Category.SAGE),
            Map.entry("chakra_sensing", Category.BODY),
            Map.entry("ore_sense", Category.BODY),
            Map.entry("eight_gates", Category.GATES),
            Map.entry("storage_chest", Category.BODY),
            Map.entry("chakra_adhesion", Category.BODY),
            Map.entry("body_adaptation", Category.BODY),
            Map.entry("bone_tools", Category.BODY),
            Map.entry("curse_seal_one", Category.CURSE_SEAL),
            Map.entry("curse_seal_two", Category.CURSE_SEAL),
            Map.entry("chakra_feeding", Category.BODY),

            Map.entry("chakra_cloak", Category.TAILED),
            Map.entry("tailed_cloak", Category.TAILED),
            Map.entry("seal_vessel", Category.TAILED),
            Map.entry("seal_break", Category.TAILED),
            Map.entry("divided_cloak", Category.TAILED),
            Map.entry("perfect_cloak", Category.TAILED),
            Map.entry("tailed_ball", Category.TAILED),

            Map.entry("genjutsu", Category.ILLUSION),
            Map.entry("henge", Category.ILLUSION),
            Map.entry("smoke_screen", Category.ILLUSION),
            Map.entry("silence", Category.ILLUSION),

            Map.entry("rinnegan", Category.RINNEGAN),
            Map.entry("universal_force", Category.RINNEGAN),
            Map.entry("chibaku_tensei", Category.RINNEGAN),
            Map.entry("momoshiki_absorption", Category.RINNEGAN),

            Map.entry("sharingan", Category.SHARINGAN),
            Map.entry("amaterasu", Category.SHARINGAN),
            Map.entry("kagutsuchi", Category.SHARINGAN),

            Map.entry("byakugan", Category.BYAKUGAN),
            Map.entry("rotation", Category.BYAKUGAN),

            Map.entry("jougan", Category.JOUGAN),
            Map.entry("dimension_step", Category.JOUGAN),
            Map.entry("chakra_sever", Category.JOUGAN),

            Map.entry("senrigan", Category.SENRIGAN),
            Map.entry("senrigan_sight", Category.SENRIGAN),
            Map.entry("senrigan_flight", Category.SENRIGAN)
    );

    /**
     * Where a technique goes when it has no entry of its own in {@link #CATEGORIES}: the branch of
     * the node that teaches it. That is the same board the caster learns it on, so the menu and the
     * tree never disagree - a technique filed under Taijutsu in the tree is listed under Taijutsu
     * here. Branches that already share a group (the five natures, the three physical pools) map to
     * one category, and a technique that needs a different home despite its branch keeps an entry in
     * {@link #CATEGORIES} to override this.
     */
    private static final Map<SkillBranch, Category> BRANCHES = Map.ofEntries(
            Map.entry(SkillBranch.CORE, Category.BASICS),
            Map.entry(SkillBranch.ATHLETICS, Category.BASICS),
            Map.entry(SkillBranch.TAIJUTSU, Category.TAIJUTSU),
            Map.entry(SkillBranch.GATES, Category.GATES),
            Map.entry(SkillBranch.MEDICINE, Category.DEFENCE),

            Map.entry(SkillBranch.FIRE, Category.ELEMENTAL),
            Map.entry(SkillBranch.WATER, Category.ELEMENTAL),
            Map.entry(SkillBranch.EARTH, Category.ELEMENTAL),
            Map.entry(SkillBranch.WIND, Category.ELEMENTAL),
            Map.entry(SkillBranch.WOOD, Category.ELEMENTAL),

            Map.entry(SkillBranch.CLONES, Category.CLONES),
            Map.entry(SkillBranch.SPACE, Category.SPACE),
            Map.entry(SkillBranch.MIND, Category.ILLUSION),
            Map.entry(SkillBranch.SAGE, Category.BODY),

            Map.entry(SkillBranch.SHARINGAN, Category.SHARINGAN),
            Map.entry(SkillBranch.BYAKUGAN, Category.BYAKUGAN),
            Map.entry(SkillBranch.JOUGAN, Category.JOUGAN),
            Map.entry(SkillBranch.SENRIGAN, Category.SENRIGAN),
            Map.entry(SkillBranch.RINNEGAN, Category.RINNEGAN),
            Map.entry(SkillBranch.SIX_PATHS, Category.SIX_PATHS)
    );

    /** Files a technique by its explicit entry first, then by the branch that teaches it. */
    private static Category categoryOf(ResourceLocation id) {
        Category explicit = CATEGORIES.get(id.getPath());
        if (explicit != null) {
            return explicit;
        }
        Skill node = SkillTree.byUnlock("ability:" + id.getPath());
        Category fromBranch = node == null ? null : BRANCHES.get(node.branch());
        return fromBranch == null ? Category.OTHER : fromBranch;
    }

    /**
     * Chains that read base-first no matter what their combos say: the cloak before the tail, the
     * gates opened before their arts, the mode before its orbs, the charging before the sage form,
     * the eye before its gaze. Numbers only order within one group - a 0 in one group and a 0 in
     * another never meet, because the group itself sorts first. Anything without an entry keeps the
     * combo order it always had.
     */
    private static final Map<String, Integer> ORDER_OVERRIDES = Map.ofEntries(
            Map.entry("chakra_cloak", 0),
            Map.entry("tailed_cloak", 1),
            Map.entry("seal_vessel", 2),
            Map.entry("seal_break", 3),
            Map.entry("divided_cloak", 4),
            Map.entry("perfect_cloak", 5),
            Map.entry("tailed_ball", 6),

            Map.entry("eight_gates", 0),
            Map.entry("gate_peacock", 1),
            Map.entry("gate_tiger", 2),
            Map.entry("gate_elephant", 3),

            Map.entry("six_paths_mode", 0),
            Map.entry("gudodama_launch", 1),
            Map.entry("gudodama_guard", 2),
            Map.entry("gudodama_form", 3),
            Map.entry("yin_yang_heal", 4),
            Map.entry("limbo", 5),

            Map.entry("senjutsu_charging", 0),
            Map.entry("sage_mode", 1),

            Map.entry("jougan", 0),

            Map.entry("curse_seal_one", 0),
            Map.entry("curse_seal_two", 1)
    );

    /** A technique's place in its group: the chain order above, or the combo order after it. */
    private static int orderOf(Entry entry) {
        String key = entry.nameKey();
        int colon = key.indexOf(':');
        String path = colon < 0 ? key : key.substring(colon + 1);
        return ORDER_OVERRIDES.getOrDefault(path, Integer.MAX_VALUE);
    }

    private Button becomeANinja;
    private Button changeBack;
    private Button skillTree;
    private Button previousPage;
    private Button nextPage;
    private Button done;
    private Button onlyUnlockedButton;

    private List<Entry> entries;
    private List<List<Row>> pages;
    private int page;

    /**
     * The page the list was last on. Static so closing the menu and opening it again - or coming back
     * from the skill tree - lands where the reader left off instead of at the top every time.
     */
    private static int rememberedPage = 0;

    /**
     * Whether the list is showing only the techniques already learned. Static like the page, so the
     * menu opens in the same mode it was left in.
     */
    private static boolean onlyUnlocked = false;

    /** Whether the player has taken up the title, which is what the list is for. */
    private boolean ninjaModeEnabled;

    /**
     * The width of the widest combo on the list, used when sizing the panel so a row always has room
     * for its keys. The keys themselves are right aligned against the mastery bar.
     */
    private int keyColumnWidth;

    /**
     * The player's cast count per technique, straight off the synced ninja data, which is what the
     * mastery bars on each row are drawn from. Empty until the first tick.
     */
    private Map<ResourceLocation, Integer> masteryCasts = Map.of();

    /**
     * Techniques that are up in the background right now - the stances, sage mode and a held sense -
     * read from the same synced state the techniques themselves run on, so the menu can never claim
     * something is active that is not.
     */
    private Set<ResourceLocation> activeAbilities = Set.of();

    /** Techniques whose effect is still holding without a per-tick drain, shown with the amber marker. */
    private Set<ResourceLocation> armedAbilities = Set.of();

    /**
     * Techniques the caster has switched off. Read from the same synced data the server gates casts
     * on, so a grey row in the menu is exactly a technique the server would refuse.
     */
    private Set<ResourceLocation> disabledAbilities = Set.of();

    /**
     * Whether the caster asked to be able to click any technique in the list, not only the ones that
     * stay on, see {@code MenuClickCommand}. Read from the synced data, since the client is what
     * decides whether a row responds to a click at all.
     */
    private boolean clickAnyTechnique;

    /** Space kept free for {@link #ACTIVE_MARKER} on every row, so names all start in one column. */
    private int markerColumnWidth;

    /**
     * The synced ninja data, kept so a row can ask the same question the server asks before it runs a
     * technique: is this one unlocked yet. Null until the screen is ticked once.
     */
    private INinjaData ninjaData;

    /** One technique: its translation key, the combo that casts it and where it is filed. */
    private record Entry(String nameKey, long combo, Category category) {
    }

    /** One line of a page: either a group header, or a technique under the header above it. */
    private record Row(Category category, Entry entry) {
        boolean isHeader() {
            return this.entry == null;
        }
    }

    private final Component noticeLine = Component.translatable("naruto.gui.jutsu.notice");
    private final Component toggleLine = Component.translatable("naruto.gui.jutsu.notice2");
    private final Component skillLine = Component.translatable("naruto.gui.jutsu.notice3");

    public JutsuScreen() {
        super(Component.translatable("naruto.gui.jutsu.title"));
    }

    @Override
    protected void init() {
        this.entries = buildEntries();
        this.markerColumnWidth = Math.max(this.font.width(ACTIVE_MARKER),
                this.font.width(ARMED_MARKER)) + 4;
        this.keyColumnWidth = this.entries.stream()
                .mapToInt(entry -> this.font.width(comboKeys(entry.combo())))
                .max().orElse(0);
        this.rebuildPages();
        this.addButtons();
        this.page = rememberedPage;
        this.applyState();
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    /** How far below {@link #panelTop()} the content runs, which is what the panel is sized to. */
    private int contentBottom() {
        return this.ninjaModeEnabled ? 214 : 110;
    }

    /**
     * Top of the content: centred on the screen, but never so low that the buttons would fall off a
     * small window.
     */
    private int panelTop() {
        int panelHeight = contentBottom() + 10;
        return Math.max(4, Math.min(this.height / 2 - panelHeight / 2, this.height - panelHeight - 4));
    }

    /**
     * Half the panel's width, wide enough for whatever is actually drawn on it. The text is wider
     * than the list, so a fixed width would leave the notices hanging over the panel's edges.
     */
    private int panelHalfWidth() {
        int widest = this.font.width(this.title);
        for (Component line : this.visibleLines()) {
            widest = Math.max(widest, this.font.width(line));
        }
        for (Category category : Category.values()) {
            widest = Math.max(widest, this.font.width(Component.translatable(category.translationKey)));
        }
        if (this.ninjaModeEnabled && this.entries != null) {
            for (Entry entry : this.entries) {
                int row = this.font.width(PERCENT_SLOT) + 10 + this.markerColumnWidth
                        + this.font.width(Component.translatable(entry.nameKey())) + 14
                        + BAR_WIDTH + 14 + this.keyColumnWidth + 8;
                if (!this.isUnlocked(entry)) {
                    row += this.font.width(LOCK_SUFFIX);
                } else if (this.isDisabled(entry)) {
                    row += this.font.width(DISABLED_SUFFIX);
                }
                widest = Math.max(widest, row);
            }
        } else {
            widest = Math.max(widest, this.font.width(Component.translatable("naruto.gui.jutsu.locked")));
        }
        // The row of buttons under the list is fixed width, so the panel has to be at least wide
        // enough to hold it with a margin on either side.
        widest = Math.max(widest, NINJA_BUTTON_ROW_WIDTH + 24);
        return Math.max(MIN_PANEL_HALF_WIDTH, widest / 2 + 16);
    }

    /**
     * The lines under the title. The first one talks about the list below, so it is only true for a
     * ninja; anybody else just gets told how to become one.
     */
    private List<Component> visibleLines() {
        if (this.ninjaModeEnabled) {
            return List.of(this.noticeLine, this.skillLine);
        }
        return List.of(this.toggleLine, Component.translatable("naruto.gui.jutsu.locked"));
    }

    private void addButtons() {
        int centreX = this.width / 2;
        int top = panelTop();

        previousPage = this.addRenderableWidget(Button.builder(Component.literal("<"), (button) -> {
            if (this.page > 0) {
                this.page--;
                this.applyState();
            }
        }).pos(centreX - 74, top + 158).size(20, 20).build());

        nextPage = this.addRenderableWidget(Button.builder(Component.literal(">"), (button) -> {
            if (this.page < this.pageCount() - 1) {
                this.page++;
                this.applyState();
            }
        }).pos(centreX + 54, top + 158).size(20, 20).build());

        becomeANinja = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.enable"), (button) -> {
            PacketHandler.sendToServer(new ServerToggleNinjaPacket(true));
        }).pos(centreX - 102, top + 184).size(98, 20).build());
        becomeANinja.active = false;

        changeBack = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.disable"), (button) -> {
            PacketHandler.sendToServer(new ServerToggleNinjaPacket(false));
        }).pos(centreX + 4, top + 184).size(98, 20).build());
        changeBack.active = false;

        // Once the title is taken the two buttons above are gone for good and this takes their place:
        // the menu becomes a way into the skill tree rather than a way to stop being a ninja.
        skillTree = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.skilltree"), (button) -> {
            this.minecraft.setScreen(new SkillTreeScreen());
        }).pos(centreX - 49, top + 184).size(BUTTON_WIDTH, 20).build());
        skillTree.visible = false;

        done = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.done"), (button) -> {
            this.minecraft.popGuiLayer();
        }).pos(centreX - 49, top + 210).size(BUTTON_WIDTH, 20).build());

        // The list can be narrowed to what this caster has actually learned. Off by default: the
        // full list is what tells a new ninja what there is to buy in the tree.
        onlyUnlockedButton = this.addRenderableWidget(Button.builder(onlyUnlockedLabel(), (button) -> {
            onlyUnlocked = !onlyUnlocked;
            this.rebuildPages();
            this.page = 0;
            this.applyState();
        }).pos(centreX + 54, top + 184).size(BUTTON_WIDTH, 20).build());
        onlyUnlockedButton.setTooltip(Tooltip.create(
                Component.translatable("naruto.gui.jutsu.only_unlocked.tip")));
        onlyUnlockedButton.visible = false;
    }

    /** The filter button's label turns green while the filter is on, so its state is visible at a glance. */
    private static Component onlyUnlockedLabel() {
        return Component.translatable("naruto.gui.jutsu.only_unlocked")
                .withStyle(onlyUnlocked ? ChatFormatting.GREEN : ChatFormatting.GRAY);
    }

    /**
     * Puts the buttons where the current state wants them. A ninja gets the paging arrows and the
     * buttons under the list; anybody else gets a short menu with no list to page through.
     */
    private void layoutButtons() {
        int centreX = this.width / 2;
        int top = panelTop();

        previousPage.visible = this.ninjaModeEnabled;
        nextPage.visible = this.ninjaModeEnabled;

        if (this.ninjaModeEnabled) {
            previousPage.setPosition(centreX - 74, top + 158);
            nextPage.setPosition(centreX + 54, top + 158);

            // One row under the list, left to right: Skill Tree, Done, Unlocked Only.
            int rowLeft = centreX - NINJA_BUTTON_ROW_WIDTH / 2;
            skillTree.setPosition(rowLeft, top + 184);
            done.setPosition(rowLeft + BUTTON_WIDTH + BUTTON_GAP, top + 184);
            onlyUnlockedButton.setPosition(rowLeft + 2 * (BUTTON_WIDTH + BUTTON_GAP), top + 184);
        } else {
            becomeANinja.setPosition(centreX - 102, top + 58);
            changeBack.setPosition(centreX + 4, top + 58);
            done.setPosition(centreX - BUTTON_WIDTH / 2, top + 84);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The list itself
    // ---------------------------------------------------------------------------------------------

    /**
     * The technique list, taken from the combo map so it shows exactly what the keys can cast.
     */
    private static List<Entry> buildEntries() {
        List<Entry> result = new ArrayList<>();
        NarutoAbilities.COMBO_MAP.forEach((combo, id) ->
                result.add(new Entry(id.toString(), combo, categoryOf(id))));
        result.sort(Comparator.comparingInt((Entry entry) -> entry.category().ordinal())
                .thenComparingInt(JutsuScreen::orderOf)
                .thenComparingLong(Entry::combo));
        return result;
    }

    /**
     * Splits the techniques into pages, group header and all.
     *
     * <p>A page is filled with whole groups where it can be: a header and at least one technique stay
     * together, so a page never ends on a bare header, and a group that carries on over the break
     * gets its header printed again at the top of the next page - otherwise the reader would be
     * looking at a run of technique names with no idea what they belong to.</p>
     */
    private static List<List<Row>> buildPages(List<Entry> entries) {
        List<List<Row>> pages = new ArrayList<>();
        List<Row> current = new ArrayList<>();
        Category openCategory = null;
        for (Entry entry : entries) {
            boolean headerNeeded = entry.category() != openCategory;
            // A page must not end on a header, so the entry is made to fit alongside it.
            int needed = headerNeeded ? 2 : 1;
            if (current.size() + needed > ROWS_PER_PAGE) {
                pages.add(current);
                current = new ArrayList<>();
                headerNeeded = true;
            }
            if (headerNeeded) {
                current.add(new Row(entry.category(), null));
                openCategory = entry.category();
            }
            current.add(new Row(entry.category(), entry));
        }
        if (!current.isEmpty()) {
            pages.add(current);
        }
        if (pages.isEmpty()) {
            pages.add(new ArrayList<>());
        }
        return pages;
    }

    private int pageCount() {
        return Math.max(1, this.pages.size());
    }

    /**
     * Rebuilds the paged list from the entries, applying the unlocked-only filter when it is on.
     * Called when the synced ninja data first arrives and whenever the filter is switched, not every
     * tick: the list only changes when the player learns something, which happens in the skill tree.
     */
    private void rebuildPages() {
        List<Entry> visible = this.entries;
        if (onlyUnlocked) {
            visible = new ArrayList<>();
            for (Entry entry : this.entries) {
                if (this.isUnlocked(entry)) {
                    visible.add(entry);
                }
            }
        }
        this.pages = buildPages(visible);
    }

    @Override
    public void tick() {
        var player = this.minecraft.player;
        var ninjaData = player == null ? null
                : player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        boolean dataChanged = this.ninjaData != ninjaData;
        this.ninjaModeEnabled = ninjaData != null && ninjaData.isNinjaModeEnabled();
        this.ninjaData = ninjaData;
        this.masteryCasts = ninjaData == null ? Map.of() : ninjaData.getMasteryData().getCasts();
        this.activeAbilities = activeAbilitiesOf(ninjaData);
        this.armedAbilities = armedAbilitiesOf(ninjaData);
        this.disabledAbilities = ninjaData == null ? Set.of()
                : Set.copyOf(ninjaData.getDisabledAbilityData().getAbilities());
        this.clickAnyTechnique = ninjaData != null && ninjaData.isMenuClickAll();
        // The first tick is when the synced data first arrives, and the unlocked-only filter needs it
        // to know what to show. After that the list only changes when the filter is switched.
        if (dataChanged) {
            this.rebuildPages();
        }
        this.applyState();
    }

    /**
     * The techniques that are up in the background right now, read from the same synced state they
     * run on: the stances from the toggle set, sage mode from its flag, and a held chakra sense from
     * its radius. Nothing here is guessed, so the marker can never disagree with what is running.
     */
    private static Set<ResourceLocation> activeAbilitiesOf(INinjaData ninjaData) {
        if (ninjaData == null) {
            return Set.of();
        }
        Set<ResourceLocation> active = new HashSet<>(ninjaData.getToggleAbilityData().getAbilitiesHashSet());
        if (ninjaData.isSageMode()) {
            active.add(SageModeAbility.ID);
        }
        if (ninjaData.getSensingRadius() > 0f || ninjaData.getNightVisionTicks() > 0) {
            active.add(ChakraSensingAbility.ID);
        }
        return active;
    }

    /**
     * Techniques that are in effect but are not being paid for every tick: the chakra shield while
     * it holds points, the second breath while it waits for a killing blow, and a henge while the
     * disguise is worn. These get the amber marker rather than the green one - the green means "the
     * chakra is running right now", which is a different thing from "this is still doing something".
     */
    private static Set<ResourceLocation> armedAbilitiesOf(INinjaData ninjaData) {
        if (ninjaData == null) {
            return Set.of();
        }
        Set<ResourceLocation> armed = new HashSet<>();
        if (ninjaData.getShieldPoints() > 0f) {
            armed.add(ChakraShieldAbility.ID);
        }
        if (ninjaData.isSecondBreathArmed()) {
            armed.add(SecondBreathAbility.ID);
        }
        if (!ninjaData.getHengeTarget().isEmpty() || !ninjaData.getHengeEntityType().isEmpty()) {
            armed.add(HengeJutsuAbility.ID);
        }
        return armed;
    }

    /** Whether the technique on a row is one of the ones currently running in the background. */
    private boolean isActive(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.nameKey());
        return id != null && this.activeAbilities.contains(id);
    }

    /** Whether the technique on a row is in effect without a per-tick drain. */
    private boolean isArmed(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.nameKey());
        return id != null && this.armedAbilities.contains(id);
    }

    /** Whether the caster has switched this technique off in the menu. */
    private boolean isDisabled(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.nameKey());
        return id != null && this.disabledAbilities.contains(id);
    }

    /**
     * Whether the menu offers click actions for this technique. Only techniques that stay on are
     * controllable - stances, the armed chakra shield and sage mode - so a stray click cannot fire a
     * one-shot cast like a fireball.
     */
    private boolean isControllable(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.nameKey());
        Ability ability = id == null ? null : NarutoRegistries.ABILITIES.getValue(id);
        return ability != null && ability.isMenuControllable();
    }

    /**
     * Whether a row reacts to a click at all: either the technique stays on and the menu controls it,
     * or the caster turned on click-any-technique with {@code /naruto menuclick}. Locked rows are
     * never clickable - there is nothing to cast yet.
     */
    private boolean isClickable(Entry entry) {
        return (this.clickAnyTechnique || this.isControllable(entry)) && this.isUnlocked(entry);
    }

    /**
     * Whether the caster may use the technique on a row yet. The menu lists every technique so the
     * tree has something to point at - a locked row is dimmed and says so rather than hiding, which is
     * how the menu tells a new ninja what there is to learn.
     */
    private boolean isUnlocked(Entry entry) {
        return AbilityUnlocks.isUnlocked(this.ninjaData, ResourceLocation.tryParse(entry.nameKey()));
    }

    /** Pushes the state into the widgets: which are lit, where they sit and whether they show. */
    private void applyState() {
        // The title, once taken, hides both "become" and "change back": from then on the menu offers
        // the skill tree instead.
        becomeANinja.visible = !this.ninjaModeEnabled;
        changeBack.visible = !this.ninjaModeEnabled;
        skillTree.visible = this.ninjaModeEnabled;
        becomeANinja.active = !this.ninjaModeEnabled;
        changeBack.active = this.ninjaModeEnabled;
        onlyUnlockedButton.visible = this.ninjaModeEnabled;
        onlyUnlockedButton.active = this.ninjaModeEnabled;
        onlyUnlockedButton.setMessage(onlyUnlockedLabel());

        // The page count can shrink under us if the list is ever rebuilt or filtered.
        if (this.page >= pageCount()) {
            this.page = pageCount() - 1;
        }
        if (this.page < 0) {
            this.page = 0;
        }
        // Remembered for the next time the menu is opened from the same position.
        rememberedPage = this.page;

        previousPage.active = this.ninjaModeEnabled && this.page > 0;
        nextPage.active = this.ninjaModeEnabled && this.page < pageCount() - 1;

        this.layoutButtons();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);

        int centreX = this.width / 2;
        int top = panelTop();

        // A dark panel behind the text: the menu is drawn over the world, and pale text on a bright
        // field is unreadable without it.
        guiGraphics.fill(centreX - panelHalfWidth(), top - 10, centreX + panelHalfWidth(),
                top + contentBottom(), 0xA0000000);

        GuiUtils.centeredText(guiGraphics, this.font, this.title, centreX, top);
        List<Component> lines = this.visibleLines();
        for (int i = 0; i < lines.size(); i++) {
            GuiUtils.centeredText(guiGraphics, this.font, lines.get(i), centreX, top + 14 + 10 * i);
        }

        if (this.ninjaModeEnabled) {
            this.renderTechniqueList(guiGraphics, centreX, top + LIST_TOP_OFFSET, mouseX, mouseY);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
        this.renderHoverTooltip(guiGraphics, mouseX, mouseY);
    }

    private void renderTechniqueList(GuiGraphics guiGraphics, int centreX, int listTop,
                                     int mouseX, int mouseY) {
        int halfWidth = panelHalfWidth();
        int listLeft = centreX - halfWidth + LIST_PADDING;
        int listRight = centreX + halfWidth - LIST_PADDING;
        int percentSlot = this.font.width(PERCENT_SLOT);
        // The marker sits just before the name in a column reserved on every row, so active and
        // inactive names still line up.
        int markerX = listLeft + 6 + percentSlot + 10;
        int nameX = markerX + this.markerColumnWidth;

        List<Row> rows = this.pages.get(this.page);
        int hovered = this.rowIndexAt(mouseX, mouseY);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = listTop + 4 + i * ROW_HEIGHT;
            if (row.isHeader()) {
                guiGraphics.drawString(this.font,
                        Component.translatable(row.category().translationKey), listLeft, y, 0xFFD37F, false);
                continue;
            }

            // A light band under the row the pointer is on, so it is obvious which technique a click
            // would use before it is clicked. A row that would not react gets no band: promising a
            // click that does nothing is worse than not offering it.
            if (i == hovered && this.isClickable(row.entry())) {
                guiGraphics.fill(listLeft - 2, y - 2, listRight + 2, y + ROW_HEIGHT - 2, 0x30FFFFFF);
            }

            // Fixed columns, left to right: the percentage is parked in its own slot on the far left,
            // then the name, then the keys in a right aligned column, and the mastery bar last, on
            // the far right. Reading a column of bars down the list is the point, so nothing here
            // may drift with the width of a name or a combo.
            boolean locked = !this.isUnlocked(row.entry());
            boolean off = !locked && this.isControllable(row.entry()) && this.isDisabled(row.entry());
            int colour = locked ? LOCKED_COLOUR : off ? DISABLED_COLOUR : 0xFFFFFF;
            Component percent = locked
                    ? Component.literal("-")
                    : Component.literal(Math.round(this.masteryOf(row.entry()) * 100f) + "%");
            guiGraphics.drawString(this.font, percent,
                    listLeft + 6 + percentSlot - this.font.width(percent), y,
                    locked ? LOCKED_COLOUR : 0xD8D8D8, false);

            if (!locked && !off && this.isActive(row.entry())) {
                guiGraphics.drawString(this.font, ACTIVE_MARKER, markerX, y, ACTIVE_COLOUR, false);
            } else if (!locked && !off && this.isArmed(row.entry())) {
                guiGraphics.drawString(this.font, ARMED_MARKER, markerX, y, ARMED_COLOUR, false);
            }

            Component name = Component.translatable(row.entry().nameKey());
            if (locked) {
                name = name.copy().append(LOCK_SUFFIX);
            } else if (off) {
                name = name.copy().append(DISABLED_SUFFIX);
            }
            guiGraphics.drawString(this.font, name, nameX, y, colour, false);

            Component keys = comboKeys(row.entry().combo());
            guiGraphics.drawString(this.font, keys,
                    listRight - BAR_WIDTH - 14 - this.font.width(keys), y,
                    locked || off ? DISABLED_COLOUR : 0x55FFFF, false);

            this.renderMasteryBar(guiGraphics, row.entry(), listRight, y, locked, off);
        }

        GuiUtils.centeredText(guiGraphics, this.font,
                Component.translatable("naruto.gui.jutsu.page", this.page + 1, this.pageCount()),
                centreX, listTop + 122);
    }

    /**
     * The reference card for the row under the pointer: what the technique is, how it is cast, and
     * what the row itself cannot say - the activation type and the click hints. Drawn as a tooltip
     * rather than permanent lines so the list stays readable and the explanation appears exactly
     * where it applies.
     *
     * <p>Locked techniques are described too. A new ninja needs to know what a node is worth before
     * spending points on it, and hiding the text until it is bought made the tree a row of names.</p>
     */
    private void renderHoverTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!this.ninjaModeEnabled || this.pages == null || this.entries == null) {
            return;
        }
        int index = this.rowIndexAt(mouseX, mouseY);
        if (index < 0) {
            return;
        }
        Row row = this.pages.get(this.page).get(index);
        if (row.isHeader()) {
            return;
        }
        ResourceLocation id = ResourceLocation.tryParse(row.entry().nameKey());
        Ability ability = id == null ? null : NarutoRegistries.ABILITIES.getValue(id);
        boolean locked = !this.isUnlocked(row.entry());
        boolean controllable = this.isControllable(row.entry());
        boolean off = controllable && this.isDisabled(row.entry());

        List<FormattedText> lines = new ArrayList<>();
        lines.add(Component.translatable(row.entry().nameKey())
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD));
        if (ability != null) {
            lines.add(Component.translatable(switch (ability.activationType()) {
                        case INSTANT -> "naruto.gui.jutsu.hover.instant";
                        case TOGGLE -> "naruto.gui.jutsu.hover.toggle";
                        case CHANNELED -> "naruto.gui.jutsu.hover.channeled";
                    }).withStyle(ChatFormatting.AQUA));
        }
        Component description = this.descriptionOf(id);
        if (description != null) {
            lines.add(Component.empty());
            lines.addAll(this.font.getSplitter().splitLines(description.getString(), TOOLTIP_WIDTH,
                    Style.EMPTY.withColor(ChatFormatting.GRAY)));
        }
        if (locked) {
            lines.add(Component.empty());
            lines.add(Component.translatable("naruto.gui.jutsu.hover.locked")
                    .withStyle(ChatFormatting.RED));
        } else if (this.isClickable(row.entry())) {
            lines.add(Component.empty());
            lines.add(Component.translatable(off ? "naruto.gui.jutsu.hint.enable"
                    : "naruto.gui.jutsu.hint.cast").withStyle(ChatFormatting.GRAY));
            // Only techniques that stay on can be put aside; with click-any-technique on, a
            // fireball is clickable to cast but has nothing to switch off, so it gets no second
            // hint.
            if (controllable) {
                lines.add(Component.translatable(off ? "naruto.gui.jutsu.hint.enabled"
                        : "naruto.gui.jutsu.hint.disable").withStyle(ChatFormatting.DARK_GRAY));
            }
        }
        guiGraphics.renderComponentTooltip(this.font, lines, mouseX, mouseY, ItemStack.EMPTY);
    }

    /**
     * The explanation for a technique, preferring the combat description over the skill-tree line.
     * The tree line is only a lesson title ("Learn the Fireball technique"), so it is a fallback
     * for a technique that has no description of its own yet, not the normal reading.
     *
     * @return the description, or null when neither the technique nor its node has one.
     */
    private Component descriptionOf(ResourceLocation id) {
        if (id == null) {
            return null;
        }
        String abilityKey = "narutomod:" + id.getPath() + ".desc";
        Component description = Component.translatable(abilityKey);
        if (!description.getString().equals(abilityKey)) {
            return description;
        }
        Skill node = SkillTree.byUnlock("ability:" + id.getPath());
        if (node == null) {
            return null;
        }
        String nodeKey = node.descriptionKey();
        Component fallback = Component.translatable(nodeKey);
        return fallback.getString().equals(nodeKey) ? null : fallback;
    }

    /**
     * Which row of the current page is under the pointer, or -1. Headers count as rows: they take a
     * line like everything else, so the index is the same one the drawing loop uses.
     */
    private int rowIndexAt(int mouseX, int mouseY) {
        if (this.pages == null || this.page < 0 || this.page >= this.pages.size()) {
            return -1;
        }
        int halfWidth = this.panelHalfWidth();
        int left = this.width / 2 - halfWidth + LIST_PADDING - 4;
        int right = this.width / 2 + halfWidth - LIST_PADDING + 4;
        if (mouseX < left || mouseX > right) {
            return -1;
        }
        int listTop = panelTop() + LIST_TOP_OFFSET;
        int index = (mouseY - (listTop + 4)) / ROW_HEIGHT;
        if (index < 0 || index >= this.pages.get(this.page).size()) {
            return -1;
        }
        return index;
    }

    /**
     * Left click uses a technique from the list: a stance is toggled on or off, an armed instant
     * like the chakra shield is cast. Only rows {@link #isClickable} accepts reach this point, so a
     * one-shot cast cannot be fired by a stray click unless the caster asked for that.
     */
    private void activate(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.nameKey());
        if (id == null) {
            return;
        }
        Ability ability = NarutoRegistries.ABILITIES.getValue(id);
        if (ability == null) {
            return;
        }
        if (ability.activationType() == Ability.ActivationType.CHANNELED) {
            PacketHandler.sendToServer(new ServerAbilityChannelPacket(id,
                    ServerAbilityChannelPacket.ChannelStatus.MIN_ACTIVATE));
        } else {
            PacketHandler.sendToServer(new ServerAbilityActivatePacket(id));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (!this.ninjaModeEnabled || (button != 0 && button != 1)) {
            return false;
        }
        int index = this.rowIndexAt((int) mouseX, (int) mouseY);
        if (index < 0) {
            return false;
        }
        Row row = this.pages.get(this.page).get(index);
        if (row.isHeader() || !this.isClickable(row.entry())) {
            return false;
        }
        boolean controllable = this.isControllable(row.entry());
        if (button == 1) {
            // Right click puts a technique aside, or brings it back. Only techniques that stay on
            // have anything to put aside, so the other rows ignore the right button.
            if (!controllable) {
                return false;
            }
            // The client only asks; the server owns the set and syncs it straight back.
            ResourceLocation id = ResourceLocation.tryParse(row.entry().nameKey());
            if (id != null) {
                PacketHandler.sendToServer(new ServerToggleAbilityDisabledPacket(id,
                        !this.isDisabled(row.entry())));
            }
            return true;
        }
        if (!controllable || !this.isDisabled(row.entry())) {
            this.activate(row.entry());
            return true;
        }
        return false;
    }


    /**
     * The mastery bar, drawn at the far right of the row, after the keys, and at a fixed offset in
     * from the right edge so every bar on the page lines up in one column regardless of how wide
     * that row's name or keys are. A locked technique's bar stays empty: there is no progress to show
     * on something that cannot be cast yet.
     */
    private void renderMasteryBar(GuiGraphics guiGraphics, Entry entry, int listRight, int y, boolean locked,
                                  boolean off) {
        float mastery = locked || off ? 0f : this.masteryOf(entry);

        int barRight = listRight;
        int barLeft = barRight - BAR_WIDTH;
        int barY = y + 3;
        guiGraphics.fill(barLeft, barY, barRight, barY + BAR_HEIGHT, 0xFF2A2A2A);
        int filled = Math.round(BAR_WIDTH * mastery);
        if (filled > 0) {
            guiGraphics.fill(barLeft, barY, barLeft + filled, barY + BAR_HEIGHT, masteryColour(mastery));
        }
    }

    /** How far the technique on a row has been mastered, from the synced cast counts. */
    private float masteryOf(Entry entry) {
        ResourceLocation id = ResourceLocation.tryParse(entry.nameKey());
        if (id == null) {
            return 0f;
        }
        Integer casts = this.masteryCasts.get(id);
        return casts == null ? 0f : Mastery.level(casts);
    }

    /** A bar colour that slides from a plain green to a ripe gold as the technique is finished. */
    private static int masteryColour(float mastery) {
        int red = Math.round(0x55 + (0xFF - 0x55) * mastery);
        int green = Math.round(0xCC + (0xD3 - 0xCC) * mastery);
        int blue = Math.round(0x55 + (0x7F - 0x55) * mastery);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }

    /**
     * The combo drawn as the keys it is made of. Digits map to the three casting keys, the same way
     * the caster's own key presses do.
     */
    private static Component comboKeys(long combo) {
        MutableComponent keys = Component.empty();
        String digits = Long.toString(combo);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) {
                keys.append(Component.literal(" "));
            }
            keys.append(Component.literal(keyLetter(digits.charAt(i) - '0')));
        }
        return keys;
    }

    /**
     * The name of the key a combo digit is bound to, as it is printed on an English keyboard.
     *
     * <p>Vanilla's own keybind components ask the operating system for the key's name, which follows
     * the keyboard layout: with a Cyrillic layout the three keys come back as Cyrillic letters and
     * the menu reads like nonsense. The key code is the same whatever the layout, so the letter is
     * worked out from that instead, and only keys that are not letters or digits fall back to the
     * localised name.</p>
     */
    private static String keyLetter(int digit) {
        KeyMapping mapping = NarutoKeyHandler.getJutsuKey(digit);
        if (mapping == null) {
            return String.valueOf(digit);
        }
        InputConstants.Key key = mapping.getKey();
        if (key.getType() == InputConstants.Type.KEYSYM) {
            int code = key.getValue();
            if (code >= GLFW.GLFW_KEY_A && code <= GLFW.GLFW_KEY_Z) {
                return String.valueOf((char) ('A' + (code - GLFW.GLFW_KEY_A)));
            }
            if (code >= GLFW.GLFW_KEY_0 && code <= GLFW.GLFW_KEY_9) {
                return String.valueOf((char) ('0' + (code - GLFW.GLFW_KEY_0)));
            }
        }
        return key.getDisplayName().getString();
    }
}
