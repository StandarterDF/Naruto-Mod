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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The jutsu menu, opened with the jutsu key: the whole technique list on one page instead of telling
 * the player to go and run a command.
 *
 * <p>Every technique that has a combo is listed by name with the keys that cast it, read from the
 * same combo map the casting itself uses, so the list can never drift away from what the keys
 * actually do. It is drawn on its own panel so it stays readable over whatever the player is
 * standing in.</p>
 *
 * <p>The list is only worth showing to an actual ninja, so until the player has taken the title up
 * the menu is just the two lines explaining that and the button to do it; the techniques appear the
 * moment they do.</p>
 */
public class JutsuScreen extends Screen {

    /** How many techniques fit on one page before the arrows have to be used. */
    private static final int ROWS_PER_PAGE = 9;
    private static final int ROW_HEIGHT = 11;

    /** Width of the panel, and how far the list runs from the middle of it. */
    private static final int PANEL_HALF_WIDTH = 132;

    private Button becomeANinja;
    private Button changeBack;
    private Button previousPage;
    private Button nextPage;
    private Button done;

    private List<Entry> entries;
    private int page;

    /** Whether the player has taken up the title, which is what the list is for. */
    private boolean ninjaModeEnabled;

    /** One line of the list: the technique's translation key and the combo that casts it. */
    private record Entry(String nameKey, long combo) {
    }

    private final Component[] renderLines = {
            Component.translatable("naruto.gui.jutsu.notice"),
            Component.translatable("naruto.gui.jutsu.notice2"),
    };

    public JutsuScreen() {
        super(Component.translatable("naruto.gui.jutsu.title"));
    }

    @Override
    protected void init() {
        this.entries = buildEntries();
        this.addButtons();
        this.applyState();
    }

    // ---------------------------------------------------------------------------------------------
    // Layout
    // ---------------------------------------------------------------------------------------------

    /** How far below {@link #panelTop()} the content runs, which is what the panel is sized to. */
    private int contentBottom() {
        return this.ninjaModeEnabled ? 230 : 110;
    }

    /**
     * Top of the content: centred on the screen, but never so low that the buttons would fall off a
     * small window.
     */
    private int panelTop() {
        int panelHeight = contentBottom() + 10;
        return Math.max(4, Math.min(this.height / 2 - panelHeight / 2, this.height - panelHeight - 4));
    }

    private void addButtons() {
        int centreX = this.width / 2;
        int top = panelTop();

        previousPage = this.addRenderableWidget(Button.builder(Component.literal("<"), (button) -> {
            if (this.page > 0) {
                this.page--;
            }
        }).pos(centreX - 74, top + 154).size(20, 20).build());

        nextPage = this.addRenderableWidget(Button.builder(Component.literal(">"), (button) -> {
            if (this.page < this.pageCount() - 1) {
                this.page++;
            }
        }).pos(centreX + 54, top + 154).size(20, 20).build());

        becomeANinja = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.enable"), (button) -> {
            PacketHandler.sendToServer(new ServerToggleNinjaPacket(true));
        }).pos(centreX - 102, top + 178).size(98, 20).build());
        becomeANinja.active = false;

        changeBack = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.disable"), (button) -> {
            PacketHandler.sendToServer(new ServerToggleNinjaPacket(false));
        }).pos(centreX + 4, top + 178).size(98, 20).build());
        changeBack.active = false;

        done = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.done"), (button) -> {
            this.minecraft.popGuiLayer();
        }).pos(centreX - 49, top + 204).size(98, 20).build());
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
            previousPage.setPosition(centreX - 74, top + 154);
            nextPage.setPosition(centreX + 54, top + 154);
            becomeANinja.setPosition(centreX - 102, top + 178);
            changeBack.setPosition(centreX + 4, top + 178);
            done.setPosition(centreX - 49, top + 204);
        } else {
            becomeANinja.setPosition(centreX - 102, top + 58);
            changeBack.setPosition(centreX + 4, top + 58);
            done.setPosition(centreX - 49, top + 84);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // State
    // ---------------------------------------------------------------------------------------------

    /**
     * The technique list, taken from the combo map so it shows exactly what the keys can cast, and
     * sorted by combo so related techniques sit next to each other.
     */
    private static List<Entry> buildEntries() {
        List<Entry> result = new ArrayList<>();
        NarutoAbilities.COMBO_MAP.forEach((combo, id) -> result.add(new Entry(id.toString(), combo)));
        result.sort(Comparator.comparingLong(Entry::combo).thenComparing(Entry::nameKey));
        return result;
    }

    private int pageCount() {
        return Math.max(1, (this.entries.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
    }

    @Override
    public void tick() {
        AtomicBoolean isNinja = new AtomicBoolean(false);
        var player = this.minecraft.player;
        if (player != null) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                isNinja.set(ninjaData.isNinjaModeEnabled());
            });
        }
        this.ninjaModeEnabled = isNinja.get();
        this.applyState();
    }

    /** Pushes the state into the widgets: which are lit, where they sit and whether they show. */
    private void applyState() {
        becomeANinja.active = !this.ninjaModeEnabled;
        changeBack.active = this.ninjaModeEnabled;

        // The page can shrink under us if the map is rebuilt for any reason.
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
        guiGraphics.fill(centreX - PANEL_HALF_WIDTH, top - 10, centreX + PANEL_HALF_WIDTH,
                top + contentBottom(), 0xA0000000);

        GuiUtils.centeredText(guiGraphics, this.font, this.title, centreX, top);
        for (int i = 0; i < renderLines.length; i++) {
            GuiUtils.centeredText(guiGraphics, this.font, renderLines[i], centreX, top + 14 + 10 * i);
        }

        if (this.ninjaModeEnabled) {
            this.renderTechniqueList(guiGraphics, centreX, top + 42);
        } else {
            GuiUtils.centeredText(guiGraphics, this.font,
                    Component.translatable("naruto.gui.jutsu.locked"), centreX, top + 42, 0xAAAAAA);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderTechniqueList(GuiGraphics guiGraphics, int centreX, int listTop) {
        if (entries == null) {
            return;
        }
        GuiUtils.centeredText(guiGraphics, this.font,
                Component.translatable("naruto.gui.jutsu.techniques"), centreX, listTop, 0xFFD37F);

        int firstRow = this.page * ROWS_PER_PAGE;
        int listLeft = centreX - PANEL_HALF_WIDTH + 10;
        int listRight = centreX + PANEL_HALF_WIDTH - 10;
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int index = firstRow + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int y = listTop + 16 + i * ROW_HEIGHT;
            guiGraphics.drawString(this.font, Component.translatable(entry.nameKey()), listLeft, y, 0xFFFFFF, false);
            Component keys = comboKeys(entry.combo());
            guiGraphics.drawString(this.font, keys, listRight - this.font.width(keys), y, 0x55FFFF, false);
        }

        GuiUtils.centeredText(guiGraphics, this.font,
                Component.translatable("naruto.gui.jutsu.page", this.page + 1, this.pageCount()),
                centreX, listTop + 118);
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
