package com.sekwah.narutomod.client.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.sekwah.narutomod.capabilities.INinjaData;
import com.sekwah.narutomod.capabilities.NinjaCapabilityHandler;
import com.sekwah.narutomod.network.PacketHandler;
import com.sekwah.narutomod.network.c2s.ServerBuySkillPointsPacket;
import com.sekwah.narutomod.network.c2s.ServerUnlockSkillPacket;
import com.sekwah.narutomod.skills.Skill;
import com.sekwah.narutomod.skills.SkillBranch;
import com.sekwah.narutomod.skills.SkillPoints;
import com.sekwah.narutomod.skills.SkillSection;
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
 * The skill tree: a board of nodes a caster buys with skill points.
 *
 * <p>The board is drawn in its own coordinates and then panned and zoomed as a whole, so the layout
 * in {@link Skill} is all the screen needs to place anything. Nodes are coloured by what they are
 * waiting on - locked, ready, ready but unaffordable, learned - and a click sends the node's path to
 * the server, which is the only side allowed to spend the points. Experience is first exchanged for
 * points with the buttons at the bottom left, so a caster can bank what they have earned and spend
 * it at their own pace. Everything the screen knows comes from the synced {@code SkillData}, so it
 * can never show a node open that the server has not agreed to.</p>
 *
 * <h2>Two levels of tabs</h2>
 * <p>The tree is far too big for one strip of tabs, so the navigation is two rows deep. The top row
 * is the four {@link SkillSection sections} (the physical path, the natures, the special techniques
 * and the eye bloodlines); the row below it is the {@link SkillBranch branches} of the active
 * section. Each branch is its own constellation around its own centre, panned and zoomed on its own,
 * and switching a branch snaps the view back to that centre. A section is only a grouping - it holds
 * no nodes - so a caster picks a discipline first and a speciality second.</p>
 *
 * <p>A branch with no nodes yet (the eye bloodlines) is still a selectable tab: the board is simply
 * empty and says so, which is what leaves room for the next set of techniques without reshaping the
 * screen again.</p>
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

    /** The sections of the top row, in display order. */
    private static final SkillSection[] SECTIONS = SkillSection.values();

    /** For each section, the branches of its sub-tab row, in declaration order. */
    private static final SkillBranch[][] SECTION_BRANCHES = new SkillBranch[SECTIONS.length][];

    /** Every branch, in display order (section by section). */
    private static final SkillBranch[] BRANCHES = SkillBranch.values();

    /** For each branch, the indices into {@link #NODES} of the nodes that belong to it. */
    private static final int[][] BRANCH_NODES = new int[BRANCHES.length][];

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
        for (int s = 0; s < SECTIONS.length; s++) {
            SECTION_BRANCHES[s] = SkillBranch.of(SECTIONS[s]);
        }
        for (int b = 0; b < BRANCHES.length; b++) {
            SkillBranch branch = BRANCHES[b];
            int count = 0;
            for (Skill node : NODES) {
                if (node.branch() == branch) {
                    count++;
                }
            }
            int[] members = new int[count];
            int at = 0;
            for (int i = 0; i < NODES.length; i++) {
                if (NODES[i].branch() == branch) {
                    members[at++] = i;
                }
            }
            BRANCH_NODES[b] = members;
        }
    }

    /** Section row geometry: a strip of equal cells across the top of the board. */
    private static final int SECTION_TOP = 28;
    private static final int TAB_HEIGHT = 16;

    /** Branch row geometry: one row lower, sized to fit the widest section. */
    private static final int BRANCH_TOP = SECTION_TOP + TAB_HEIGHT + 2;
    private static final int BRANCH_TAB_WIDTH = 74;

    private double panX;
    private double panY;
    private float zoom = 0.6f;

    /** Which section is open; its branch row is the one on screen. */
    private int activeSection;

    /** Which branch's board is on screen; the others are hidden entirely. */
    private int activeBranch;

    private boolean dragging;
    private boolean pressMoved;
    private double pressX;
    private double pressY;
    private double lastX;
    private double lastY;

    private Set<ResourceLocation> unlocked = Set.of();
    private Set<ResourceLocation> scrolls = Set.of();

    /** Skill points already banked, and the experience that can still be turned into more. */
    private int skillPoints;
    private int xp;

    private Skill hovered;
    private Button done;
    private Button center;
    private Button friends;
    private Button buyOne;
    private Button buyTen;
    private Button buyMax;

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
                .pos(this.width - 218, this.height - 30).size(98, 20).build());
        // Friends sit at the bottom right, next to the view reset: the list belongs to the same
        // social side of the menu as the tree, not to the techniques screen.
        this.friends = this.addRenderableWidget(Button.builder(Component.translatable("naruto.friends.button"),
                        button -> this.minecraft.setScreen(new FriendsScreen(this)))
                .pos(this.width - 108, this.height - 30).size(98, 20).build());
        // The exchange lives in the free space at the bottom left: one, ten, or everything the
        // experience on hand affords. Buying is a request to the server either way.
        int exchangeY = this.height - 30;
        this.buyOne = this.addRenderableWidget(Button.builder(Component.literal("+1"),
                        button -> this.buyPoints(1))
                .pos(10, exchangeY).size(30, 20).build());
        this.buyTen = this.addRenderableWidget(Button.builder(Component.literal("+10"),
                        button -> this.buyPoints(10))
                .pos(44, exchangeY).size(38, 20).build());
        this.buyMax = this.addRenderableWidget(Button.builder(Component.translatable("naruto.gui.skill.buy.max"),
                        button -> this.buyPoints(0))
                .pos(86, exchangeY).size(46, 20).build());
    }

    @Override
    public void tick() {
        var player = this.minecraft == null ? null : this.minecraft.player;
        INinjaData ninjaData = player == null ? null
                : player.getCapability(NinjaCapabilityHandler.NINJA_DATA).orElse(null);
        this.unlocked = ninjaData == null ? Set.of() : new HashSet<>(ninjaData.getSkillData().getUnlocked());
        this.scrolls = ninjaData == null ? Set.of() : new HashSet<>(ninjaData.getSkillData().getScrolls());
        this.skillPoints = ninjaData == null ? 0 : ninjaData.getSkillData().getPoints();
        this.xp = player == null ? 0 : ExperienceUtils.currentPoints(player);
    }

    /**
     * Asks the server to exchange experience for points. A non-positive amount means "everything
     * affordable"; the server trims the request to the experience actually on hand either way.
     */
    private void buyPoints(int amount) {
        if (this.minecraft == null || this.minecraft.player == null) {
            return;
        }
        int affordable = SkillPoints.affordable(this.minecraft.player);
        int wanted = amount <= 0 ? affordable : Math.min(amount, affordable);
        if (wanted <= 0) {
            return;
        }
        PacketHandler.sendToServer(new ServerBuySkillPointsPacket(wanted));
    }

    /** Snaps the board back to its centre node at a readable scale. */
    private void center() {
        this.panX = 0.0;
        this.panY = 0.0;
        this.zoom = 1.0f;
    }

    /** Shows another section and opens its first branch, so the board is never left blank. */
    private void switchSection(int section) {
        if (section < 0 || section >= SECTIONS.length || section == this.activeSection) {
            return;
        }
        this.activeSection = section;
        SkillBranch[] branches = SECTION_BRANCHES[section];
        this.activeBranch = branches.length == 0 ? 0 : indexOf(branches[0]);
        this.center();
        this.hovered = null;
    }

    /** Shows another branch of the open section: the board is per-branch, so the view snaps back. */
    private void switchBranch(int branch) {
        if (branch < 0 || branch >= BRANCHES.length || branch == this.activeBranch) {
            return;
        }
        if (BRANCHES[branch].section() != SECTIONS[this.activeSection]) {
            return;
        }
        this.activeBranch = branch;
        this.center();
        this.hovered = null;
    }

    private static int indexOf(SkillBranch branch) {
        for (int i = 0; i < BRANCHES.length; i++) {
            if (BRANCHES[i] == branch) {
                return i;
            }
        }
        return 0;
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
        for (int index : BRANCH_NODES[this.activeBranch]) {
            Skill skill = NODES[index];
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
            int section = sectionAt(mouseX, mouseY);
            if (section >= 0) {
                switchSection(section);
                return true;
            }
            int branch = branchAt(mouseX, mouseY);
            if (branch >= 0) {
                switchBranch(branch);
                return true;
            }
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

    /** The section tab under the cursor, or -1. */
    private int sectionAt(double mouseX, double mouseY) {
        if (mouseY < SECTION_TOP || mouseY > SECTION_TOP + TAB_HEIGHT) {
            return -1;
        }
        int width = this.sectionTabWidth();
        int left = this.width / 2 - (SECTIONS.length * width) / 2;
        int index = (int) ((mouseX - left) / width);
        if (mouseX < left || index < 0 || index >= SECTIONS.length) {
            return -1;
        }
        return index;
    }

    /** The branch tab under the cursor, or -1 when the cursor is not on the open section's row. */
    private int branchAt(double mouseX, double mouseY) {
        if (mouseY < BRANCH_TOP || mouseY > BRANCH_TOP + TAB_HEIGHT) {
            return -1;
        }
        SkillBranch[] branches = SECTION_BRANCHES[this.activeSection];
        int left = this.width / 2 - (branches.length * BRANCH_TAB_WIDTH) / 2;
        int index = (int) ((mouseX - left) / BRANCH_TAB_WIDTH);
        if (mouseX < left || index < 0 || index >= branches.length) {
            return -1;
        }
        return indexOf(branches[index]);
    }

    /** The section tabs share the width of the screen, so a longer name still fits. */
    private int sectionTabWidth() {
        return Math.max(96, Math.min(150, (this.width - 24) / SECTIONS.length));
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
        if (this.skillPoints < skill.cost()) {
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
        for (int index : BRANCH_NODES[this.activeBranch]) {
            this.drawNode(guiGraphics, index);
        }

        guiGraphics.pose().popPose();

        GuiUtils.centeredText(guiGraphics, this.font, this.title, this.width / 2, 10, 0xFFFFFF);
        this.drawTabs(guiGraphics, mouseX, mouseY);
        this.drawWallet(guiGraphics);
        if (BRANCH_NODES[this.activeBranch].length == 0) {
            GuiUtils.centeredText(guiGraphics, this.font,
                    Component.translatable("naruto.gui.skill.section.empty"),
                    this.width / 2, this.height / 2, 0x9A9A9A);
        }
        GuiUtils.centeredText(guiGraphics, this.font, Component.translatable("naruto.gui.skill.hint"),
                this.width / 2, this.height - 58, 0xAAAAAA);

        super.render(guiGraphics, mouseX, mouseY, partialTick);

        if (this.hovered != null) {
            guiGraphics.renderComponentTooltip(this.font, tooltipFor(this.hovered), mouseX, mouseY);
        }
    }

    /** The two currencies top left, and the exchange label above the buy buttons bottom left. */
    private void drawWallet(GuiGraphics guiGraphics) {
        guiGraphics.drawString(this.font,
                Component.translatable("naruto.gui.skill.points", grouped(this.skillPoints)),
                10, 8, 0xFFD37F, true);
        guiGraphics.drawString(this.font,
                Component.translatable("naruto.gui.skill.xp", grouped(this.xp)),
                10, 19, 0xFFE8E8E8, true);
        guiGraphics.drawString(this.font, Component.translatable("naruto.gui.skill.buy"),
                10, this.height - 44, 0xFFD37F, true);
    }

    /** Both tab rows: the sections on top, the branches of the open section right below. */
    private void drawTabs(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int sectionWidth = this.sectionTabWidth();
        int sectionLeft = this.width / 2 - (SECTIONS.length * sectionWidth) / 2;
        int hoveredSection = sectionAt(mouseX, mouseY);
        for (int i = 0; i < SECTIONS.length; i++) {
            SkillSection section = SECTIONS[i];
            int x = sectionLeft + i * sectionWidth;
            boolean active = i == this.activeSection;
            boolean over = i == hoveredSection;
            drawTab(guiGraphics, x, SECTION_TOP, sectionWidth - 2,
                    active ? section.colour() : (over ? 0xFFAAAAAA : 0xFF4A4A4A),
                    active ? 0xFF2C2C2C : (over ? 0xFF232323 : 0xFF161616));
            GuiUtils.centeredText(guiGraphics, this.font,
                    Component.translatable(section.translationKey()),
                    x + (sectionWidth - 2) / 2, SECTION_TOP + 4,
                    active ? section.colour() : (over ? 0xFFFFFFFF : 0xFFB0B0B0));
        }

        SkillBranch[] branches = SECTION_BRANCHES[this.activeSection];
        int branchLeft = this.width / 2 - (branches.length * BRANCH_TAB_WIDTH) / 2;
        int hoveredBranch = branchAt(mouseX, mouseY);
        for (int i = 0; i < branches.length; i++) {
            SkillBranch branch = branches[i];
            int x = branchLeft + i * BRANCH_TAB_WIDTH;
            boolean active = indexOf(branch) == this.activeBranch;
            boolean over = indexOf(branch) == hoveredBranch;
            int width = BRANCH_TAB_WIDTH - 2;
            drawTab(guiGraphics, x, BRANCH_TOP, width,
                    active ? branch.colour() : (over ? 0xFF8A8A8A : 0xFF3A3A3A),
                    active ? 0xFF242424 : (over ? 0xFF1D1D1D : 0xFF121212));
            GuiUtils.centeredText(guiGraphics, this.font,
                    Component.translatable(branch.translationKey()),
                    x + width / 2, BRANCH_TOP + 4,
                    active ? branch.colour() : (over ? 0xFFE8E8E8 : 0xFF909090));
        }
    }

    private static void drawTab(GuiGraphics guiGraphics, int x, int y, int width, int border, int fill) {
        guiGraphics.fill(x, y, x + width, y + TAB_HEIGHT, border);
        guiGraphics.fill(x + 1, y + 1, x + width - 1, y + TAB_HEIGHT - 1, fill);
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
            for (int i : BRANCH_NODES[this.activeBranch]) {
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
        boolean ready = SkillTree.prerequisitesMet(skill, this.unlocked)
                && this.hasScroll(skill);
        boolean affordable = this.skillPoints >= skill.cost();

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

    private boolean hasScroll(Skill skill) {
        return !skill.requiresScroll() || this.scrolls.contains(skill.scrollKnowledge());
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
            } else if (!this.hasScroll(skill)) {
                lines.add(Component.translatable("naruto.gui.skill.scroll").withStyle(ChatFormatting.RED));
            } else if (this.skillPoints < skill.cost()) {
                lines.add(Component.translatable("naruto.gui.skill.poor", grouped(skill.cost() - this.skillPoints))
                        .withStyle(ChatFormatting.RED));
            }
            lines.add(Component.translatable("naruto.gui.skill.cost", grouped(skill.cost()))
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
    private static String grouped(int points) {
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
