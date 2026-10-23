package com.sekwah.narutomod.client.gui;

import com.sekwah.narutomod.client.ClientFriendsData;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerFriendActionPacket;
import com.sekwah.narutomod.network.s2c.ClientboundFriendsPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The friends screen, opened from the skill menu.
 *
 * <p>It is one scrollable page with three lists: requests waiting for an answer at the top,
 * requests the caster is still waiting on, and the accepted friends. Each row carries the one
 * action that makes sense for it, drawn as a clickable word instead of a widget, because the rows
 * themselves scroll and a screen full of floating buttons would scroll badly.</p>
 *
 * <p>Nothing here decides anything: a click is only a request to the server, which validates it and
 * sends the whole list back. The screen is redrawn from {@link ClientFriendsData}, so it always
 * shows what the server has actually agreed to.</p>
 */
public class FriendsScreen extends Screen {

    private static final int PANEL_WIDTH = 380;
    private static final int HEADER_HEIGHT = 18;
    private static final int ROW_HEIGHT = 20;
    private static final int ROW_GAP = 2;

    private final Screen parent;

    private EditBox nameField;
    private Button addButton;

    private double scroll;

    /** Clickable words built while drawing, so a scrolled row knows where it actually is. */
    private final List<Hit> hits = new ArrayList<>();

    public FriendsScreen(Screen parent) {
        super(Component.translatable("naruto.friends.title"));
        this.parent = parent;
    }

    private int left() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int top() {
        return 24;
    }

    private int panelBottom() {
        return this.height - 32;
    }

    private int contentTop() {
        return this.top() + 62;
    }

    @Override
    protected void init() {
        int left = this.left();
        int top = this.top();
        this.nameField = new EditBox(this.font, left + 12, top + 36, 190, 20,
                Component.translatable("naruto.friends.add.hint"));
        this.nameField.setMaxLength(16);
        this.nameField.setHint(Component.translatable("naruto.friends.add.hint"));
        this.addRenderableWidget(this.nameField);

        this.addButton = this.addRenderableWidget(Button.builder(
                        Component.translatable("naruto.friends.add"),
                        button -> this.sendRequest())
                .pos(left + 208, top + 36).size(70, 20).build());
    }

    /** Sends the typed name to the server. The field is cleared so the same request is not sent twice. */
    private void sendRequest() {
        if (this.nameField == null) {
            return;
        }
        String name = this.nameField.getValue().trim();
        if (name.isEmpty()) {
            return;
        }
        PacketHandler.sendToServer(ServerFriendActionPacket.request(name));
        this.nameField.setValue("");
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button != 0) {
            return false;
        }
        for (Hit hit : this.hits) {
            if (hit.contains(mouseX, mouseY)) {
                PacketHandler.sendToServer(new ServerFriendActionPacket(hit.action(), hit.target(), ""));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int view = this.panelBottom() - this.contentTop() - 6;
        int content = this.contentHeight();
        double max = Math.max(0, content - view);
        if (max <= 0) {
            return false;
        }
        this.scroll = Mth.clamp(this.scroll - delta * 18.0d, 0.0d, max);
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.nameField != null && this.nameField.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.sendRequest();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private int contentHeight() {
        int height = 0;
        height += this.sectionHeight(ClientFriendsData.incoming());
        height += this.sectionHeight(ClientFriendsData.outgoing());
        height += this.sectionHeight(ClientFriendsData.friends());
        return height;
    }

    private int sectionHeight(List<ClientboundFriendsPacket.Entry> entries) {
        return HEADER_HEIGHT + Math.max(1, entries.size()) * (ROW_HEIGHT + ROW_GAP);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.hits.clear();

        int left = this.left();
        int top = this.top();
        int right = left + PANEL_WIDTH;
        int bottom = this.panelBottom();

        guiGraphics.fill(left, top, right, bottom, 0xC0101010);
        guiGraphics.fill(left, top, right, top + 1, 0xFF585858);
        guiGraphics.fill(left, bottom - 1, right, bottom, 0xFF585858);
        guiGraphics.drawCenteredString(this.font, this.title, this.width / 2, top + 10, 0xFFFFFF);
        guiGraphics.drawString(this.font, Component.translatable("naruto.friends.add.label"),
                left + 12, top + 24, 0xFFD37F, false);

        int y = this.contentTop() - (int) this.scroll;
        int viewTop = this.contentTop();
        int viewBottom = bottom - 6;
        y = this.drawSection(guiGraphics, left + 12, right - 12, y, viewTop, viewBottom,
                "naruto.friends.section.incoming", ClientFriendsData.incoming(), Action.ACCEPT);
        y = this.drawSection(guiGraphics, left + 12, right - 12, y, viewTop, viewBottom,
                "naruto.friends.section.outgoing", ClientFriendsData.outgoing(), Action.CANCEL);
        this.drawSection(guiGraphics, left + 12, right - 12, y, viewTop, viewBottom,
                "naruto.friends.section.friends", ClientFriendsData.friends(), Action.REMOVE);

        if (this.contentHeight() > viewBottom - viewTop) {
            guiGraphics.drawString(this.font, Component.translatable("naruto.friends.scroll"),
                    left + 12, bottom - 14, 0x808080, false);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    /**
     * Draws one list and returns the y the next one starts at. Rows outside the view are skipped
     * before they cost anything, and a skipped row leaves no clickable area behind.
     */
    private int drawSection(GuiGraphics guiGraphics, int left, int right, int y, int viewTop, int viewBottom,
                            String headerKey, List<ClientboundFriendsPacket.Entry> entries, Action action) {
        if (y + HEADER_HEIGHT > viewTop && y < viewBottom) {
            guiGraphics.drawString(this.font, Component.translatable(headerKey), left, y + 4,
                    ChatFormatting.GOLD.getColor(), false);
        }
        y += HEADER_HEIGHT;
        if (entries.isEmpty()) {
            if (y + ROW_HEIGHT > viewTop && y < viewBottom) {
                guiGraphics.drawString(this.font, Component.translatable("naruto.friends.empty"),
                        left + 6, y + 6, 0x707070, false);
            }
            return y + ROW_HEIGHT + ROW_GAP;
        }
        for (ClientboundFriendsPacket.Entry entry : entries) {
            int rowBottom = y + ROW_HEIGHT;
            if (rowBottom > viewTop && y < viewBottom) {
                guiGraphics.fill(left, y, right, rowBottom, 0x30FFFFFF);
                guiGraphics.drawString(this.font, entry.name(), left + 6, y + 6, 0xFFFFFF, false);
                int actionX = this.drawAction(guiGraphics, right, y, action, entry.id());
                if (action == Action.ACCEPT) {
                    this.drawActionAt(guiGraphics, actionX - 8, y, "naruto.friends.decline",
                            0xFF7070, ServerFriendActionPacket.DECLINE, entry.id());
                }
            }
            y += ROW_HEIGHT + ROW_GAP;
        }
        return y;
    }

    /** Draws the primary action on a row and registers its click area. */
    private int drawAction(GuiGraphics guiGraphics, int right, int y, Action action, UUID target) {
        String key = switch (action) {
            case ACCEPT -> "naruto.friends.accept";
            case CANCEL -> "naruto.friends.cancel";
            case REMOVE -> "naruto.friends.remove";
        };
        int colour = action == Action.ACCEPT ? 0x7FF07F : 0xFF9070;
        int width = this.font.width(Component.translatable(key));
        int x = right - width;
        guiGraphics.drawString(this.font, Component.translatable(key), x, y + 6, colour, false);
        byte packetAction = switch (action) {
            case ACCEPT -> ServerFriendActionPacket.ACCEPT;
            case CANCEL -> ServerFriendActionPacket.CANCEL;
            case REMOVE -> ServerFriendActionPacket.REMOVE;
        };
        this.hits.add(new Hit(packetAction, target, x - 2, y, x + width + 2, y + ROW_HEIGHT));
        return x;
    }

    /** Adds the decline word to the left of the accept word on an incoming request. */
    private void drawActionAt(GuiGraphics guiGraphics, int right, int y, String key, int colour,
                              byte action, UUID target) {
        int width = this.font.width(Component.translatable(key));
        int x = Math.max(this.left() + 6, right - width);
        guiGraphics.drawString(this.font, Component.translatable(key), x, y + 6, colour, false);
        this.hits.add(new Hit(action, target, x - 2, y, x + width + 2, y + ROW_HEIGHT));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    private enum Action {
        ACCEPT,
        CANCEL,
        REMOVE
    }

    private record Hit(byte action, UUID target, int x1, int y1, int x2, int y2) {
        boolean contains(double mouseX, double mouseY) {
            return mouseX >= this.x1 && mouseX <= this.x2 && mouseY >= this.y1 && mouseY <= this.y2;
        }
    }
}
