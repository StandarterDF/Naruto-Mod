package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerUnlockSkillPacket;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillTree;
import com.sekwah.narutomod.util.ExperienceUtils;
import com.sekwah.narutomod.util.GuiUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The skill tree: a board of nodes a caster buys with experience points.
 *
 * <p>The board is drawn in its own coordinates and then panned and zoomed as a whole, so the layout
 * in {@link Skill} is all the screen needs to place anything. Nodes are coloured by what they are
 * waiting on - locked, ready, ready but unaffordable, learned - and a click sends the node's path to
 * the server, which is the only side allowed to spend the points. Everything the screen knows comes
 * from the synced {@code SkillData}, so it can never show a node open that the server has not
 * agreed to.</p>
 *
 * <p>The tree is meant to be big and to radiate from the first node, so the board starts zoomed out
 * enough to see most of it and has a button to snap back to the centre.</p>
 *
 * <h2>Keeping it light</h2>
 * <p>A board this size is easy to make stutter, so the drawing is deliberately cheap. Every node's
 * position, name and price is worked out once when the class loads rather than every frame; nodes and
 * links that are not on screen are skipped before they cost anything; the links - the only thing here
 * that needs a hand-drawn line - are added to a single buffer and drawn in one call instead of one
 * call per pixel, and the line is thickened when the view is zoomed out so fewer, larger blocks cover
 * the same distance. Node names are only drawn once the view is zoomed in past {@link #LABEL_ZOOM},
 * because at that scale they are the expensive part and the tooltip already carries them.</p>
 */
public class SkillTreeScreen extends Screen {

    /** The node box, in tree units. */
    private static final int NODE = 22;
    private static final int HALF = NODE / 2;

    /** One grid step between nodes, in tree units. */
    private static final int GRID_X = 132;
    private static final int GRID_Y = 94;

    private static final float MIN_ZOOM = 0.35f;
    private static final float MAX_ZOOM = 1.6f;

    /** Below this zoom the node names are left out; the tooltip still shows them. */
    private static final float LABEL_ZOOM = 0.65f;

    /** How far the mouse may move on a press before it counts as a drag rather than a click. */
    private static final double DRAG_THRESHOLD = 4.0;

    /** Colour of the route lit up to the node under the cursor. */
    private static final int HIGHLIGHT_COLOUR = 0xFFFFD37F;

    // ---------------------------------------------------------------------------------------------
    // The tree, flattened once
    // ---------------------------------------------------------------------------------------------

    /** Every node, in declaration order. Flat arrays so the render loop allocates nothing. */
    private static final Skill[] NODES = Skill.all().toArray(new Skill[0]);

    /** Node to its index in {@link #NODES}, used to resolve links by number. */
    private static final Map<Skill, Integer> INDEX = new IdentityHashMap<>();

    /** Pre-built name and price text, which would otherwise be rebuilt every frame. */
    private static final Component[] NODE_NAME = new Component[NODES.length];
    private static final Component[] NODE_COST = new Component[NODES.length];

    /** For each node, the indices of the nodes that must be learned first. */
    private static final int[][] PREREQUISITES = new int[NODES.length][];

    static {
        for (int i = 0; i < NODES.length; i++) {
            INDEX.put(NODES[i], i);
        }
        for (int i = 0; i < NODES.length; i++) {
            NODE_NAME[i] = Component.translatable(NODES[i].nameKey());
            NODE_COST[i] = Component.literal(formatCost(NODES[i].cost()));
            List<Skill> prerequisites = SkillTree.prerequisites(NODES[i]);
            int[] indices = new int[prerequisites.size()];
            for (int j = 0; j < indices.length; j++) {
                indices[j] = INDEX.getOrDefault(prerequisites.get(j), -1);
            }
            PREREQUISITES[i] = indices;
        }
    }

    private double panX;
    private double panY;
    private float zoom = 0.6f;

    private boolean dragging;
    private boolean pressMoved;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;

    private Set<ResourceLocation> unlocked = Set.of();
    private int points;

    private Skill hovered;
    private Button done;
    private Button center;

    /** Per node: whether it is on the route to the hovered one. Reused every frame. */
    private final boolean[] highlighted = new boolean[NODES.length];

    public SkillTreeScreen() {
        super(Component.translatable("naruto.gui.skill.title"));
    }

    @Override
    protected void init() {
        this.done = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.skill.done"),
                        button -> this.onClose())
                .pos(this.width / 2 - 49, this.height - 30).size(98, 20).build());
        this.center = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.skill.center"),
                        button -> this.center())
                .pos(this.width - 108, this.height - 30).size(98, 20).build());
    }

    @Override
    public void tick() {
        var player = this.minecraft == null ? null : this.minecraft.player;
        INinjaData ninjaData = player == null ? null
                : player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        this.unlocked = ninjaData == null ? Set.of() : new HashSet<>(ninjaData.getSkillData().getUnlocked());
        this.points = player == null ? 0 : ExperienceUtils.currentPoints(player);
    }

    /** Snaps the board back to the first node at a readable scale. */
    private void center() {
        this.panX = 0.0;
        this.panY = 0.0;
        this.zoom = 1.0f;
    }

    // ---------------------------------------------------------------------------------------------
    // Board coordinates
    // ---------------------------------------------------------------------------------------------

    private static int nodeX(Skill skill) {
        return skill.x() * GRID_X;
    }

    private static int nodeY(Skill skill) {
        return skill.y() * GRID_Y;
    }

    private Skill nodeAt(double mouseX, double mouseY) {
        double treeX = (mouseX - (this.width / 2.0 + this.panX)) / this.zoom;
        double treeY = (mouseY - (this.height / 2.0 + this.panY)) / this.zoom;
        for (Skill skill : NODES) {
            if (Math.abs(treeX - nodeX(skill)) <= HALF && Math.abs(treeY - nodeY(skill)) <= HALF) {
                return skill;
            }
        }
        return null;
    }

    /** Left edge of the visible board area, in tree units. */
    private double viewLeft() {
        return -(this.width / 2.0 + this.panX) / this.zoom;
    }

    private double viewRight() {
        return (this.width - (this.width / 2.0 + this.panX)) / this.zoom;
    }

    private double viewTop() {
        return -(this.height / 2.0 + this.panY) / this.zoom;
    }

    private double viewBottom() {
        return (this.height - (this.height / 2.0 + this.panY)) / this.zoom;
    }

    // ---------------------------------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        if (button == 0) {
            this.dragging = true;
            this.pressMoved = false;
            this.pressX = mouseX;
            this.pressY = mouseY;
            this.lastX = mouseX;
            this.lastY = mouseY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (button == 0 && this.dragging) {
            if (Math.abs(mouseX - this.pressX) > DRAG_THRESHOLD
                    || Math.abs(mouseY - this.pressY) > DRAG_THRESHOLD) {
                this.pressMoved = true;
            }
            if (this.pressMoved) {
                this.panX += mouseX - this.lastX;
                this.panY += mouseY - this.lastY;
            }
            this.lastX = mouseX;
            this.lastY = mouseY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && this.dragging) {
            this.dragging = false;
            if (!this.pressMoved) {
                Skill skill = nodeAt(mouseX, mouseY);
                if (skill != null) {
                    tryUnlock(skill);
                }
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        float previous = this.zoom;
        this.zoom = (float) Math.max(MIN_ZOOM, Math.min(MAX_ZOOM,
                this.zoom * (float) Math.pow(1.1, delta)));
        // Keep the point under the cursor where it is, so zooming does not slide the tree away.
        double centreX = this.width / 2.0 + this.panX;
        double centreY = this.height / 2.0 + this.panY;
        this.panX += (mouseX - centreX) * (1.0 - this.zoom / previous);
        this.panY += (mouseY - centreY) * (1.0 - this.zoom / previous);
        return true;
    }

    /**
     * Asks the server to buy the node. The screen only sends the request when the node looks
     * buyable; the server checks it all again, because the points and the set are its to hold.
     */
    private void tryUnlock(Skill skill) {
        if (this.unlocked.contains(skill.id())) {
            return;
        }
        if (!SkillTree.prerequisitesMet(skill, this.unlocked)) {
            return;
        }
        if (this.points < skill.cost()) {
            return;
        }
        PacketHandler.sendToServer(new ServerUnlockSkillPacket(skill.path()));
    }

    // ---------------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------------

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        this.hovered = nodeAt(mouseX, mouseY);

        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(this.width / 2.0 + this.panX, this.height / 2.0 + this.panY, 0);
        guiGraphics.pose().scale(this.zoom, this.zoom, 1.0f);

        // Links first, so the nodes sit on top of them. They go into one shared buffer and are drawn
        // in a single call, which is what keeps a tree this size from stuttering.
        this.drawLinks(guiGraphics);
        for (int i = 0; i < NODES.length; i++) {
            this.drawNode(guiGraphics, i);
        }

        guiGraphics.pose().popPose();

        GuiUtils.centeredText(guiGraphics, this.font, this.title, this.width / 2, 14, 0xFFFFFF);
        GuiUtils.centeredText(guiGraphics, this.font, Component.translatable("naruto.gui.skill.hint"),
                this.width / 2, 28, 0xAAAAAA);
        guiGraphics.drawString(this.font,
                Component.translatable("naruto.gui.skill.xp", formatXp(this.points)),
                10, this.height - 20, 0xFFD37F, true);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.hovered != null) {
            guiGraphics.renderComponentTooltip(this.font, tooltipFor(this.hovered), mouseX, mouseY);
        }
    }

    /**
     * Every link, added to one buffer. The link is stepped by a block whose size grows as the view
     * zooms out, so a long link costs fewer quads when the whole tree is on screen and keeps its
     * detail when the caster leans in.
     *
     * <p>While a node is hovered, the whole route that leads to it - the node itself and every
     * prerequisite up to the root - is lit in {@link #HIGHLIGHT_COLOUR} and drawn a step thicker on
     * a second pass, so it sits on top of the rest and the caster can read at a glance what a node
     * is still waiting on.</p>
     */
    private void drawLinks(GuiGraphics guiGraphics) {
        Matrix4f matrix = guiGraphics.pose().last().pose();
        VertexConsumer consumer = guiGraphics.bufferSource().getBuffer(RenderType.gui());
        int cell = Math.max(1, (int) Math.ceil(1.0f / this.zoom));
        double margin = HALF + cell;

        double left = viewLeft() - margin;
        double right = viewRight() + margin;
        double top = viewTop() - margin;
        double bottom = viewBottom() + margin;

        this.markHighlighted();

        // Two passes: pass 0 draws the dim links, pass 1 the lit route on top of them.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < NODES.length; i++) {
                int x2 = nodeX(NODES[i]);
                int y2 = nodeY(NODES[i]);
                for (int j : PREREQUISITES[i]) {
                    if (j < 0) {
                        continue;
                    }
                    boolean onRoute = this.highlighted[i];
                    if ((pass == 1) != onRoute) {
                        continue;
                    }
                    int x1 = nodeX(NODES[j]);
                    int y1 = nodeY(NODES[j]);
                    // Cheap reject: a link entirely off one edge is never partly on screen.
                    if ((x1 < left && x2 < left) || (x1 > right && x2 > right)
                            || (y1 < top && y2 < top) || (y1 > bottom && y2 > bottom)) {
                        continue;
                    }
                    int colour;
                    int width = cell;
                    if (onRoute) {
                        colour = HIGHLIGHT_COLOUR;
                        width = cell + 1;
                    } else {
                        boolean threaded = this.unlocked.contains(NODES[j].id())
                                && this.unlocked.contains(NODES[i].id());
                        colour = threaded ? NODES[i].branch().colour() : 0xFF3A3A3A;
                    }
                    addLine(consumer, matrix, x1, y1, x2, y2, width, colour);
                }
            }
        }
        // One flush for the whole set: this is the difference between one draw call and thousands.
        guiGraphics.flush();
    }

    /**
     * Marks the hovered node and, following its prerequisites up, everything it waits on. A link is
     * lit when the node it feeds is marked, so the whole route from the root to the cursor glows.
     */
    private void markHighlighted() {
        Arrays.fill(this.highlighted, false);
        if (this.hovered == null) {
            return;
        }
        int start = INDEX.getOrDefault(this.hovered, -1);
        if (start >= 0) {
            this.markHighlighted(start);
        }
    }

    private void markHighlighted(int index) {
        if (this.highlighted[index]) {
            return;
        }
        this.highlighted[index] = true;
        for (int prerequisite : PREREQUISITES[index]) {
            if (prerequisite >= 0) {
                this.markHighlighted(prerequisite);
            }
        }
    }

    private void drawNode(GuiGraphics guiGraphics, int index) {
        Skill skill = NODES[index];
        int cx = nodeX(skill);
        int cy = nodeY(skill);
        if (cx < viewLeft() - HALF || cx > viewRight() + HALF
                || cy < viewTop() - HALF || cy > viewBottom() + HALF) {
            return;
        }

        boolean learned = this.unlocked.contains(skill.id());
        boolean ready = SkillTree.prerequisitesMet(skill, this.unlocked);
        boolean affordable = this.points >= skill.cost();

        int border;
        int fill;
        if (learned) {
            border = skill.branch().colour();
            fill = 0xFF20391F;
        } else if (ready) {
            border = affordable ? 0xFFEDEDED : 0xFF707070;
            fill = affordable ? 0xFF2A2A2A : 0xFF1E1E1E;
        } else {
            border = 0xFF444444;
            fill = 0xFF141414;
        }

        guiGraphics.fill(cx - HALF - 1, cy - HALF - 1, cx + HALF + 1, cy + HALF + 1, border);
        guiGraphics.fill(cx - HALF, cy - HALF, cx + HALF, cy + HALF, fill);

        int costColour = learned ? 0xFF9FE89F : (ready && affordable ? 0xFFFFE082 : 0xFFB0B0B0);
        GuiUtils.centeredText(guiGraphics, this.font, NODE_COST[index], cx, cy - 4, costColour);
        // Names are the costly part of the frame, so they only appear once the caster has zoomed in
        // far enough to actually read them - the tooltip names the node when it is hovered either way.
        if (this.zoom >= LABEL_ZOOM || skill == this.hovered) {
            GuiUtils.centeredText(guiGraphics, this.font, NODE_NAME[index],
                    cx, cy + HALF + 4, learned ? 0xFFB8FFB8 : 0xFFD8D8D8);
        }
    }

    private List<Component> tooltipFor(Skill skill) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(skill.nameKey()).withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable(skill.branch().translationKey()).withStyle(ChatFormatting.DARK_GRAY));
        lines.add(Component.translatable(skill.descriptionKey()).withStyle(ChatFormatting.GRAY));
        if (this.unlocked.contains(skill.id())) {
            lines.add(Component.translatable("naruto.gui.skill.unlocked").withStyle(ChatFormatting.GREEN));
        } else {
            if (!SkillTree.prerequisitesMet(skill, this.unlocked)) {
                lines.add(Component.translatable("naruto.gui.skill.locked", prerequisiteNames(skill))
                        .withStyle(ChatFormatting.RED));
            } else if (this.points < skill.cost()) {
                lines.add(Component.translatable("naruto.gui.skill.poor", formatXp(skill.cost() - this.points))
                        .withStyle(ChatFormatting.RED));
            }
            lines.add(Component.translatable("naruto.gui.skill.cost", formatXp(skill.cost()))
                    .withStyle(ChatFormatting.YELLOW));
        }
        return lines;
    }

    private static Component prerequisiteNames(Skill skill) {
        MutableComponent names = Component.empty();
        List<Skill> prerequisites = SkillTree.prerequisites(skill);
        for (int i = 0; i < prerequisites.size(); i++) {
            if (i > 0) {
                names.append(Component.literal(", "));
            }
            names.append(Component.translatable(prerequisites.get(i).nameKey()));
        }
        return names;
    }

    /** A plain grouped number, e.g. {@code 12,345}. */
    private static String formatXp(int points) {
        return String.format(Locale.ROOT, "%,d", points);
    }

    /** A node has little room, so thousands are shortened, e.g. {@code 1.2k}. */
    private static String formatCost(int cost) {
        if (cost < 1000) {
            return Integer.toString(cost);
        }
        float thousands = cost / 1000f;
        if (thousands == Math.floor(thousands)) {
            return (int) thousands + "k";
        }
        return String.format(Locale.ROOT, "%.1fk", thousands);
    }

    /**
     * Steps one link and adds its quads to the shared buffer. The whole line is a run of {@code cell}
     * sized blocks, so the number of quads falls as the view zooms out while the line still reads as
     * continuous.
     */
    private static void addLine(VertexConsumer consumer, Matrix4f matrix,
                                int x1, int y1, int x2, int y2, int cell, int colour) {
        int steps = Math.max(Math.abs(x2 - x1), Math.abs(y2 - y1));
        int cells = Math.max(1, (int) Math.ceil((double) steps / cell));
        for (int i = 0; i <= cells; i++) {
            double t = (double) i / cells;
            int x = (int) Math.round(x1 + (x2 - x1) * t);
            int y = (int) Math.round(y1 + (y2 - y1) * t);
            addQuad(consumer, matrix, x - cell / 2, y - cell / 2, cell, colour);
        }
    }

    private static void addQuad(VertexConsumer consumer, Matrix4f matrix,
                                int x, int y, int size, int colour) {
        float a = ((colour >>> 24) & 0xFF) / 255.0f;
        float r = ((colour >> 16) & 0xFF) / 255.0f;
        float g = ((colour >> 8) & 0xFF) / 255.0f;
        float b = (colour & 0xFF) / 255.0f;
        consumer.vertex(matrix, x, y, 0.0f).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x, y + size, 0.0f).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x + size, y + size, 0.0f).color(r, g, b, a).endVertex();
        consumer.vertex(matrix, x + size, y, 0.0f).color(r, g, b, a).endVertex();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** The tree is a submenu of the jutsu menu, so closing it goes back there rather than to the game. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(new JutsuScreen());
    }
}
