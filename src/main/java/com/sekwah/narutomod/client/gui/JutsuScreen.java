package com.sekwah.narutomod.client.gui;

import com.sekwah.narutomod.abilities.NarutoAbilities;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerToggleNinjaPacket;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

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
 * actually do. The keys are drawn as keybinds rather than letters, which means they follow whatever
 * the player has rebound them to.</p>
 */
public class JutsuScreen extends Screen {

    /** How many techniques fit on one page before the arrows have to be used. */
    private static final int ROWS_PER_PAGE = 10;
    private static final int ROW_HEIGHT = 11;

    private Button becomeANinja;
    private Button changeBack;
    private Button previousPage;
    private Button nextPage;

    private List<Entry> entries;
    private int page;

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

    protected void init() {
        this.entries = buildEntries();
        this.addButtons();
    }

    public void addButtons() {
        int bottom = this.height / 2 + 50;

        previousPage = this.addRenderableWidget(Button.builder(Component.literal("<"), (button) -> {
            if (this.page > 0) {
                this.page--;
            }
        }).pos(this.width / 2 - 102, bottom).size(20, 20).build());
        renderables.add(previousPage);

        nextPage = this.addRenderableWidget(Button.builder(Component.literal(">"), (button) -> {
            if (this.page < this.pageCount() - 1) {
                this.page++;
            }
        }).pos(this.width / 2 + 82, bottom).size(20, 20).build());
        renderables.add(nextPage);

        becomeANinja = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.enable"), (button) -> {
            PacketHandler.sendToServer(new ServerToggleNinjaPacket(true));
        }).pos(this.width / 2 - 102, bottom + 26).size(98, 20).build());
        renderables.add(becomeANinja);
        becomeANinja.active = false;

        changeBack = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.disable"), (button) -> {
            PacketHandler.sendToServer(new ServerToggleNinjaPacket(false));
        }).pos(this.width / 2 + 4, bottom + 26).size(98, 20).build());

        renderables.add(changeBack);
        changeBack.active = false;

        Button back = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.jutsu.done"), (button) -> {
            this.minecraft.popGuiLayer();
        }).pos(this.width / 2 - 98 / 2, bottom + 50).size(98, 20).build());

        renderables.add(back);
    }

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

    public void tick() {
        super.tick();
        AtomicBoolean isNinja = new AtomicBoolean(false);
        var player = this.minecraft.player;
        if(player != null) {
            player.getCapability(NinjaCapabilityHandler.NINJA_DATA).ifPresent(ninjaData -> {
                isNinja.set(ninjaData.isNinjaModeEnabled());
            });
        }
        becomeANinja.active = !isNinja.get();
        changeBack.active = isNinja.get();

        // The page can shrink under us if the map is rebuilt for any reason.
        if (this.page >= pageCount()) {
            this.page = pageCount() - 1;
        }
        previousPage.active = this.page > 0;
        nextPage.active = this.page < pageCount() - 1;
    }

    public boolean isPauseScreen() {
        return false;
    }

    public void render(GuiGraphics guiGraphics, int p_96311_, int p_96312_, float p_96313_) {
        this.renderBackground(guiGraphics);
        int centreX = this.width / 2;
        int top = this.height / 2 - 110;
        GuiUtils.centeredText(guiGraphics, this.font, this.title, centreX, top);
        for (int i = 0; i < renderLines.length; i++) {
            GuiUtils.centeredText(guiGraphics, this.font, renderLines[i], centreX, top + 12 + 10 * i);
        }

        this.renderTechniqueList(guiGraphics, centreX, top + 44);
        super.render(guiGraphics, p_96311_, p_96312_, p_96313_);
    }

    private void renderTechniqueList(GuiGraphics guiGraphics, int centreX, int listTop) {
        if (entries == null) {
            return;
        }
        GuiUtils.centeredText(guiGraphics, this.font,
                Component.translatable("naruto.gui.jutsu.techniques"), centreX, listTop, 0xFFD37F);

        int firstRow = this.page * ROWS_PER_PAGE;
        int listLeft = centreX - 118;
        int listRight = centreX + 118;
        for (int i = 0; i < ROWS_PER_PAGE; i++) {
            int index = firstRow + i;
            if (index >= entries.size()) {
                break;
            }
            Entry entry = entries.get(index);
            int y = listTop + 14 + i * ROW_HEIGHT;
            guiGraphics.drawString(this.font, Component.translatable(entry.nameKey()), listLeft, y, 0xFFFFFF, false);
            MutableComponent keys = comboKeys(entry.combo());
            guiGraphics.drawString(this.font, keys, listRight - this.font.width(keys), y, 0x55FFFF, false);
        }

        GuiUtils.centeredText(guiGraphics, this.font,
                Component.translatable("naruto.gui.jutsu.page", this.page + 1, this.pageCount()),
                centreX, this.height / 2 + 51);
    }

    /**
     * The combo drawn as the keys it is made of. Digits map to the three casting keys, the same way
     * the caster's own key presses do.
     */
    private static MutableComponent comboKeys(long combo) {
        MutableComponent keys = Component.empty();
        String digits = Long.toString(combo);
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0) {
                keys.append(Component.literal(" "));
            }
            keys.append(Component.keybind("naruto.keys.key" + digits.charAt(i)));
        }
        return keys;
    }
}
