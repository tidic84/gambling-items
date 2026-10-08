package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.client.mixin.MouseHandlerAccessor;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.menu.TerminalTabs;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
//#if MC < 26.3
import org.lwjgl.glfw.GLFW;
//#endif

/**
 * A casino window: the header (the tabs of a portable item, or the name of the game at its block), the rules,
 * the player's inventory and a betting slip, on the grid of {@link CasinoLayout}.
 *
 * <p>The window is drawn at the size of the GUI, and only scaled down when it would not fit.
 */
public abstract class CasinoScreen<M extends AbstractContainerMenu> extends AbstractContainerScreen<M> {
    /** Changing tab swaps windows; the cursor stays where the player left it. */
    private static final long CURSOR_MEMORY_MS = 600;
    private static double cursorX = -1, cursorY;
    private static long closedAt;
    private boolean opened;
    private float canvasScale = 1;
    protected final GameRules rules;

    protected CasinoScreen(M menu, Inventory inventory, Component title) {
        this(menu, inventory, title, CasinoLayout.WIDTH, CasinoLayout.HEIGHT);
    }

    /** A window of its own size, for the casino tables that lay out more than the shared grid. */
    protected CasinoScreen(M menu, Inventory inventory, Component title, int width, int height) {
        //#if MC >= 26.1
        //$ super(menu, inventory, title, width, height);
        //#else
        super(menu, inventory, title);
        imageWidth = width;
        imageHeight = height;
        //#endif
        rules = new GameRules(mode().id());
    }

    /** The game this window plays, for its icon, its rules and its tab. */
    protected abstract GameMode mode();
    /** Whether the window was opened from a portable item, which shows the tabs of its games. */
    protected abstract boolean portable();
    /** A line about the stakes, shown beside the name of the game at its block. */
    protected Component subtitle() { return tr(GameMenus.TERMINAL.contains(mode()) ? "station" : "casino_table"); }
    protected static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

    public static ItemStack icon(GameMode mode) {
        return new ItemStack(switch (mode) {
            case UPGRADER -> Items.DIAMOND_SWORD;
            case TRADE_UP -> Items.DIAMOND;
            case CRASH -> Items.FIREWORK_ROCKET;
            case CASE_OPENING -> Items.CHEST;
            case ROULETTE -> Items.CLOCK;
            case CASE_BATTLE -> Items.NETHERITE_SWORD;
            case BLACKJACK -> Items.PAPER;
            case BINGO -> Items.BOOK;
            case SLOT_MACHINE -> Items.GOLD_INGOT;
        });
    }

    @Override protected void init() {
        canvasScale = Math.max(.25f, Math.min(1f, Math.min((width - 8f) / imageWidth, (height - 8f) / imageHeight)));
        width = Math.round(width / canvasScale);
        height = Math.round(height / canvasScale);
        super.init();
        if (!opened) {
            opened = true;
            restoreCursor();
        }
        int right = leftPos + imageWidth - 8;
        var close = addRenderableWidget(CasinoButton.quiet(Component.literal("×"), ignored -> onClose())
                .bounds(right - 18, topPos + 5, 18, 18).build());
        close.setTooltip(Tooltip.create(tr("close")));
        var help = rules.button(right - 40, topPos + 5, 18, 18);
        addRenderableWidget(help);
        if (portable()) {
            // Where every name does not fit, the other tabs keep only their icon; their tooltip names them.
            var family = GameMenus.family(mode());
            int room = imageWidth - 16 - 46, needed = 0;
            for (GameMode mode : family) needed += Tab.width(font, mode, false) + 2;
            boolean compact = needed > room;
            int x = leftPos + 8;
            for (GameMode mode : family)
                x += addRenderableWidget(new Tab(mode, x, topPos + 5, compact && mode != mode())).getWidth() + 2;
        }
    }

    private void restoreCursor() {
        if (cursorX < 0 || Util.getMillis() - closedAt > CURSOR_MEMORY_MS) return;
        //#if MC >= 26.3
        //$ // The window is an SDL window since 26.3.
        //$ org.lwjgl.sdl.SDLMouse.SDL_WarpMouseInWindow(minecraft.getWindow().handle(), (float) cursorX, (float) cursorY);
        //#elif MC >= 1.21.9
        //$ GLFW.glfwSetCursorPos(minecraft.getWindow().handle(), cursorX, cursorY);
        //#else
        GLFW.glfwSetCursorPos(minecraft.getWindow().getWindow(), cursorX, cursorY);
        //#endif
        ((MouseHandlerAccessor) minecraft.mouseHandler).gamblingitems$setX(cursorX);
        ((MouseHandlerAccessor) minecraft.mouseHandler).gamblingitems$setY(cursorY);
    }

    @Override public void removed() {
        super.removed();
        cursorX = minecraft.mouseHandler.xpos();
        cursorY = minecraft.mouseHandler.ypos();
        closedAt = Util.getMillis();
    }

    protected void send(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    /** Takes a won item into the inventory, exactly as a shift-click on its slot would. */
    protected void collect(int slot) {
        slotClicked(menu.getSlot(slot), slot, 0, net.minecraft.world.inventory.ClickType.QUICK_MOVE);
    }

    //#if MC >= 26.1
    //$ // Since 26.1 a screen extracts its frame; the background of the window is drawn here, inside the canvas scale.
    //$ @Override public final void extractRenderState(GuiGraphics g, int mouseX, int mouseY, float tick) {
    //#else
    @Override public final void render(GuiGraphics g, int mouseX, int mouseY, float tick) {
    //#endif
        GuiPose.push(g);
        try {
            GuiPose.scale(g, canvasScale);
            int x = Math.round(mouseX / canvasScale), y = Math.round(mouseY / canvasScale);
            //#if MC >= 26.1
            //$ renderBg(g, tick, x, y);
            //$ super.extractRenderState(g, x, y, tick);
            //#else
            super.render(g, x, y, tick);
            //#endif
            renderOverlay(g, x, y, tick);
            if (rules.open()) rules.render(g, font, width, height);
            //#if MC < 26.1
            else renderTooltip(g, x, y);
            //#endif
        } finally {
            GuiPose.pop(g);
        }
    }

    /** Drawn above the slots and the buttons: results, banners, the tooltips of drawn lists. */
    protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float tick) {}

    //#if MC >= 26.1
    //$ private void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
    //#else
    @Override protected final void renderBg(GuiGraphics g, float tick, int mouseX, int mouseY) {
    //#endif
        int x = leftPos, y = topPos;
        GameScreens.window(g, x, y, imageWidth, imageHeight);
        if (!portable()) {
            GameScreens.item(g, icon(mode()), x + 9, y + 6, 1);
            GameScreens.heading(g, font, tr("mode." + mode().id()), x + 30, y + 10, 1);
            int after = x + 38 + font.width(tr("mode." + mode().id()));
            GameScreens.fitted(g, font, subtitle(), after, y + 10, x + imageWidth - 56 - after, GameScreens.DIM);
        }
        renderGame(g, tick, mouseX, mouseY);
        for (Slot slot : menu.slots)
            if (slot.container instanceof Inventory && slot.isActive()) GameScreens.slot(g, x + slot.x, y + slot.y);
    }

    /** The game itself, under its slots and buttons. */
    protected abstract void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY);

    /** The betting slip under the game: a card for the numbers and the button that plays. */
    protected void slip(GuiGraphics g) {
        GameScreens.card(g, leftPos + CasinoLayout.SLIP_X, topPos + CasinoLayout.SLIP_Y,
                CasinoLayout.SLIP_WIDTH, CasinoLayout.SLIP_HEIGHT);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}

    //#if MC >= 1.21.9
    //$ // Mouse events carry their position since 1.21.9; they are rescaled to the canvas like the drawing.
    //$ private net.minecraft.client.input.MouseButtonEvent scaled(net.minecraft.client.input.MouseButtonEvent event) {
    //$     return new net.minecraft.client.input.MouseButtonEvent(event.x() / canvasScale, event.y() / canvasScale, event.buttonInfo());
    //$ }
    //$
    //$ @Override public final boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
    //$     if (rules.open()) { rules.close(); return true; }
    //$     var scaled = scaled(event);
    //$     return gameClicked(scaled.x(), scaled.y(), scaled.button()) || super.mouseClicked(scaled, doubleClick);
    //$ }
    //$ @Override public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) { return super.mouseReleased(scaled(event)); }
    //$ @Override public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double dx, double dy) {
    //$     return super.mouseDragged(scaled(event), dx / canvasScale, dy / canvasScale);
    //$ }
    //#else
    @Override public final boolean mouseClicked(double x, double y, int button) {
        // While the rules are up they take every click, so nothing is played by accident.
        if (rules.open()) { rules.close(); return true; }
        return gameClicked(x / canvasScale, y / canvasScale, button) || super.mouseClicked(x / canvasScale, y / canvasScale, button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        return super.mouseReleased(x / canvasScale, y / canvasScale, button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return super.mouseDragged(x / canvasScale, y / canvasScale, button, dx / canvasScale, dy / canvasScale);
    }
    //#endif

    /** A click on something the game draws itself rather than with a widget. */
    protected boolean gameClicked(double x, double y, int button) { return false; }

    //#if MC >= 1.20.2
    @Override public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return gameScrolled(x / canvasScale, y / canvasScale, dy) || super.mouseScrolled(x / canvasScale, y / canvasScale, dx, dy);
    }
    //#else
    //$ @Override public boolean mouseScrolled(double x, double y, double amount) {
    //$     return gameScrolled(x / canvasScale, y / canvasScale, amount) || super.mouseScrolled(x / canvasScale, y / canvasScale, amount);
    //$ }
    //#endif
    protected boolean gameScrolled(double x, double y, double amount) { return false; }

    protected boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= leftPos + x && mouseX < leftPos + x + width && mouseY >= topPos + y && mouseY < topPos + y + height;
    }

    /** One game of a portable item. The open one is lit; the others switch game without closing the window. */
    private final class Tab extends AbstractButton {
        private final GameMode target;
        private final boolean compact;

        static int width(net.minecraft.client.gui.Font font, GameMode mode, boolean compact) {
            return compact ? 24 : 30 + font.width(tr("tab." + mode.id()));
        }

        Tab(GameMode target, int x, int y, boolean compact) {
            super(x, y, width(font, target, compact), 18, tr("tab." + target.id()));
            this.target = target;
            this.compact = compact;
            setTooltip(Tooltip.create(compact ? tr("mode." + target.id()).copy().append("\n").append(tr("arena.description." + target.id()))
                    : tr("arena.description." + target.id())));
        }

        private boolean current() { return target == mode(); }

        //#if MC >= 1.21.9
        //$ @Override public void onPress(net.minecraft.client.input.InputWithModifiers input) { if (!current()) send(TerminalTabs.button(target)); }
        //#else
        @Override public void onPress() { if (!current()) send(TerminalTabs.button(target)); }
        //#endif

        @Override protected void updateWidgetNarration(NarrationElementOutput output) { defaultButtonNarrationText(output); }

        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float tick) {
            boolean hot = isHoveredOrFocused();
            if (current()) GameScreens.rounded(g, getX(), getY(), width, height, GameScreens.RAISED);
            else if (hot) GameScreens.rounded(g, getX(), getY(), width, height, GameScreens.PANEL);
            GameScreens.item(g, icon(target), getX() + (compact ? 6 : 5), getY() + 3.5f, .75f);
            if (!compact) g.drawString(font, getMessage(), getX() + 22, getY() + 5,
                    current() || hot ? GameScreens.TEXT : GameScreens.MUTED, false);
            if (current()) ArenaShapes.rounded(g, getX() + 6, getY() + height + 2, width - 12, 2, 1, GameScreens.GREEN);
        }
    }
}
