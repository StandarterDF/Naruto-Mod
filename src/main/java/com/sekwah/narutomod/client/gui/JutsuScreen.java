package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.client.keybinds.NarutoKeyHandler;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerToggleNinjaPacket;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * The jutsu menu, opened with the jutsu key: the whole technique list on one page instead of telling
 * the player to go and run a command.
 *
 * <p>Techniques are listed by name with the keys that cast them, read from the same combo map the
 * casting itself uses, so the list can never drift away from what the keys actually do. They are
 * grouped by what they are for - clones together, elements together, and so on - and within a group
 * sorted by combo, which is far easier to search through than one long run ordered by key presses.
 * The groups are drawn as headers, and a group that spills onto the next page keeps its header.</p>
 *
 * <p>The list is only worth showing to an actual ninja, so until the player has taken the title up
 * the menu is just the two lines explaining that and the button to do it; the techniques appear the
 * moment they do.</p>
 */
public class JutsuScreen extends Screen {

    /** How many lines fit on one page before the arrows have to be used. */
    private static final int ROWS_PER_PAGE = 10;
    private static final int ROW_HEIGHT = 11;

    /** Smallest width the panel will take, and how far the list runs from the middle of it. */
    private static final int MIN_PANEL_HALF_WIDTH = 132;

    /**
     * The groups the list is broken into, in the order they are shown. A technique whose id is not in
     * {@link #CATEGORIES} falls into {@link #OTHER} rather than disappearing, so a new technique is
     * never missing from the menu just because nobody filed it yet.
     */
    private enum Category {
        BASICS("naruto.gui.jutsu.category.basics"),
        CLONES("naruto.gui.jutsu.category.clones"),
        DEFENCE("naruto.gui.jutsu.category.defence"),
        ELEMENTAL("naruto.gui.jutsu.category.elemental"),
        SPACE("naruto.gui.jutsu.category.space"),
        BODY("naruto.gui.jutsu.category.body"),
        ILLUSION("naruto.gui.jutsu.category.illusion"),
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

            Map.entry("shadow_clone", Category.CLONES),
            Map.entry("chibi_shadow_clone", Category.CLONES),
            Map.entry("multi_shadow_clone", Category.CLONES),
            Map.entry("chakra_meditation", Category.CLONES),

            Map.entry("chakra_shield", Category.DEFENCE),
            Map.entry("second_breath", Category.DEFENCE),
            Map.entry("chakra_regeneration", Category.DEFENCE),

            Map.entry("fireball", Category.ELEMENTAL),
            Map.entry("campfire", Category.ELEMENTAL),
            Map.entry("water_bullet", Category.ELEMENTAL),
            Map.entry("rasengan", Category.ELEMENTAL),
            Map.entry("rasenshuriken", Category.ELEMENTAL),
            Map.entry("ore_excavation", Category.ELEMENTAL),

            Map.entry("substitution", Category.SPACE),
            Map.entry("chakra_marker", Category.SPACE),
            Map.entry("kamui", Category.SPACE),
            Map.entry("kamui_phasing", Category.SPACE),

            Map.entry("senjutsu_charging", Category.BODY),
            Map.entry("sage_mode", Category.BODY),
            Map.entry("eight_gates", Category.BODY),
            Map.entry("gate_release", Category.BODY),
            Map.entry("storage_chest", Category.BODY),

            Map.entry("genjutsu", Category.ILLUSION),
            Map.entry("henge", Category.ILLUSION)
    );

    private Button becomeANinja;
    private Button changeBack;
    private Button previousPage;
    private Button nextPage;
    private Button done;

    private List<Entry> entries;
    private List<List<Row>> pages;
    private int page;

    /** Whether the player has taken up the title, which is what the list is for. */
    private boolean ninjaModeEnabled;

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

    public JutsuScreen() {
        super(Component.translatable("naruto.gui.jutsu.title"));
    }

    @Override
    protected void init() {
        this.entries = buildEntries();
        this.pages = buildPages(this.entries);
        this.addButtons();
        this.applyState();
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    /** How far below {@link #panelTop()} the content runs, which is what the panel is sized to. */
    private int contentBottom() {
        return this.ninjaModeEnabled ? 232 : 110;
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
                int row = this.font.width(Component.translatable(entry.nameKey()))
                        + this.font.width(comboKeys(entry.combo())) + 8;
                widest = Math.max(widest, row);
            }
        } else {
            widest = Math.max(widest, this.font.width(Component.translatable("naruto.gui.jutsu.locked")));
        }
        return Math.max(MIN_PANEL_HALF_WIDTH, widest / 2 + 16);
    }

    /**
     * The lines under the title. The first one talks about the list below, so it is only true for a
     * ninja; anybody else just gets told how to become one.
     */
    private List<Component> visibleLines() {
        if (this.ninjaModeEnabled) {
            return List.of(this.noticeLine, this.toggleLine);
        }
        return List.of(this.toggleLine, Component.translatable("naruto.gui.jutsu.locked"));
    }

    private void addButtons() {
        int centreX = this.width / 2;
        int top = panelTop();

        previousPage = this.addRenderableWidget(Button.builder(Component.literal("<"), (button) -> {
            if (this.page > 0) {
                this.page--;
            }
        }).pos(centreX - 74, top + 158).size(20, 20).build());

        nextPage = this.addRenderableWidget(Button.builder(Component.literal(">"), (button) -> {
            if (this.page < this.pageCount() - 1) {
                this.page++;
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

        done = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.done"), (button) -> {
            this.minecraft.popGuiLayer();
        }).pos(centreX - 49, top + 210).size(98, 20).build());
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
            becomeANinja.setPosition(centreX - 102, top + 184);
            changeBack.setPosition(centreX + 4, top + 184);
            done.setPosition(centreX - 49, top + 210);
        } else {
            becomeANinja.setPosition(centreX - 102, top + 58);
            changeBack.setPosition(centreX + 4, top + 58);
            done.setPosition(centreX - 49, top + 84);
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
                result.add(new Entry(id.toString(), combo, CATEGORIES.getOrDefault(id.getPath(), Category.OTHER))));
        result.sort(Comparator.comparingInt((Entry entry) -> entry.category().ordinal())
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

    @Override
    public void tick() {
        var player = this.minecraft.player;
        boolean isNinja = player != null && player.getCapability(NinjaCapabilityHandler.NINJA_DATA)
                .map(ninjaData -> ninjaData.isNinjaModeEnabled()).orElse(false);
        this.ninjaModeEnabled = isNinja;
        this.applyState();
    }

    /** Pushes the state into the widgets: which are lit, where they sit and whether they show. */
    private void applyState() {
        becomeANinja.active = !this.ninjaModeEnabled;
        changeBack.active = this.ninjaModeEnabled;

        // The page count can shrink under us if the list is ever rebuilt.
        if (this.page >= pageCount()) {
            this.page = pageCount() - 1;
        }
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
            this.renderTechniqueList(guiGraphics, centreX, top + 42);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTechniqueList(GuiGraphics guiGraphics, int centreX, int listTop) {
        int halfWidth = panelHalfWidth();
        int listLeft = centreX - halfWidth + 10;
        int listRight = centreX + halfWidth - 10;

        List<Row> rows = this.pages.get(this.page);
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int y = listTop + 4 + i * ROW_HEIGHT;
            if (row.isHeader()) {
                guiGraphics.drawString(this.font,
                        Component.translatable(row.category().translationKey), listLeft, y, 0xFFD37F, false);
                continue;
            }
            guiGraphics.drawString(this.font, Component.translatable(row.entry().nameKey()),
                    listLeft + 6, y, 0xFFFFFF, false);
            Component keys = comboKeys(row.entry().combo());
            guiGraphics.drawString(this.font, keys, listRight - this.font.width(keys), y, 0x55FFFF, false);
        }

        GuiUtils.centeredText(guiGraphics, this.font,
                Component.translatable("naruto.gui.jutsu.page", this.page + 1, this.pageCount()),
                centreX, listTop + 122);
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
