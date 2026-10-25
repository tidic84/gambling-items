package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.arcade.ArcadeSettings;
import dev.gamblingitems.fabric.arcade.CashierMenu;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import dev.gamblingitems.fabric.vault.ArcadeLedger;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The cashier's counter: the prizes the arcade tickets buy on the left, the ticket balance and the
 * last plays on the right, and the tray where items of value become chips and chips items again.
 */
public final class CashierScreen extends CasinoScreen<CashierMenu> {
    private static final int COLUMNS = 4, ROWS = 4, CELL_WIDTH = 56, CELL_HEIGHT = 24, GAP = 2;
    private static final int GRID_X = CasinoLayout.GAME_X + 6, GRID_Y = CasinoLayout.CONTENT_Y + 6;
    private static final int TICKET = 0xffe8c45a;
    private Button toChips, toItems;
    private int page;

    public CashierScreen(CashierMenu menu, Inventory inventory, Component title) { super(menu, inventory, title); }

    @Override protected GameMode mode() { return GameMode.SLOT_MACHINE; }
    @Override protected boolean portable() { return false; }
    @Override protected String key() { return "cashier"; }
    @Override protected ItemStack headerIcon() { return new ItemStack(ModContent.chip(100)); }
    @Override protected Component subtitle() { return tr("cashier_subtitle"); }

    @Override protected void init() {
        super.init();
        int x = leftPos + CasinoLayout.SLIP_X + 9, y = topPos + CasinoLayout.SLIP_Y + 52, width = 80;
        toChips = addRenderableWidget(CasinoButton.primary(tr("cashier_to_chips"), button -> send(CashierMenu.TO_CHIPS))
                .bounds(x, y, width, 18).build());
        toChips.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.cashier_to_chips_help",
                GameScreens.value(menu.arcade().chipValue()))));
        toItems = addRenderableWidget(CasinoButton.builder(tr("cashier_to_items"), button -> send(CashierMenu.TO_ITEMS))
                .bounds(x + width + 4, y, width, 18).build());
        toItems.setTooltip(Tooltip.create(tr("cashier_to_items_help")));
    }

    @Override protected void containerTick() {
        super.containerTick();
        toChips.active = menu.itemValue() >= menu.arcade().chipValue();
        toItems.active = menu.chipValue() > 0;
    }

    private List<ArcadeSettings.Prize> prizes() { return menu.arcade().prizes(); }
    private int pages() { return Math.max(1, (prizes().size() + COLUMNS * ROWS - 1) / (COLUMNS * ROWS)); }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GameScreens.card(g, x + CasinoLayout.GAME_X, y + CasinoLayout.CONTENT_Y, CasinoLayout.GAME_WIDTH, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.card(g, x + CasinoLayout.SIDE_X, y + CasinoLayout.CONTENT_Y, CasinoLayout.SIDE_WIDTH, CasinoLayout.CONTENT_HEIGHT);
        slip(g);
        prizes(g, mouseX, mouseY);
        side(g);
        tray(g);
    }

    private void prizes(GuiGraphics g, int mouseX, int mouseY) {
        long balance = menu.balance();
        if (!menu.arcade().tickets()) {
            GameScreens.paragraph(g, font, tr("cashier_no_tickets"), leftPos + GRID_X, topPos + GRID_Y + 4,
                    CasinoLayout.GAME_WIDTH - 12, 6, GameScreens.MUTED);
            return;
        }
        int first = page * COLUMNS * ROWS;
        for (int cell = 0; cell < COLUMNS * ROWS && first + cell < prizes().size(); cell++) {
            ArcadeSettings.Prize prize = prizes().get(first + cell);
            int cx = leftPos + GRID_X + cell % COLUMNS * (CELL_WIDTH + GAP), cy = topPos + GRID_Y + cell / COLUMNS * (CELL_HEIGHT + GAP);
            boolean affordable = balance >= prize.tickets();
            boolean hot = affordable && mouseX >= cx && mouseX < cx + CELL_WIDTH && mouseY >= cy && mouseY < cy + CELL_HEIGHT;
            GameScreens.rounded(g, cx, cy, CELL_WIDTH, CELL_HEIGHT, hot ? GameScreens.HOVER : GameScreens.RAISED);
            ItemStack stack = new ItemStack(Compat.item(prize.item()), prize.count());
            GameScreens.item(g, stack, cx + 3, cy + 4, 1);
            if (prize.count() > 1) g.drawString(font, "x" + prize.count(), cx + 13, cy + 14, GameScreens.TEXT, true);
            g.drawString(font, String.valueOf(prize.tickets()), cx + 22, cy + 4, affordable ? TICKET : GameScreens.DIM, false);
            g.drawString(font, tr("tickets_short"), cx + 22, cy + 13, GameScreens.DIM, false);
            if (hot) GameScreens.outline(g, cx, cy, CELL_WIDTH, CELL_HEIGHT, GameScreens.GOLD);
        }
        if (pages() > 1) {
            GameScreens.rightAligned(g, font, (page + 1) + "/" + pages(), leftPos + CasinoLayout.GAME_X + CasinoLayout.GAME_WIDTH - 6,
                    topPos + CasinoLayout.CONTENT_Y + CasinoLayout.CONTENT_HEIGHT - 10, GameScreens.DIM);
        }
    }

    private void side(GuiGraphics g) {
        int x = leftPos + CasinoLayout.SIDE_X + 7, y = topPos + CasinoLayout.CONTENT_Y + 6;
        g.drawString(font, tr("cashier_balance"), x, y, GameScreens.MUTED, false);
        GameScreens.heading(g, font, Component.literal(String.valueOf(menu.balance())), x, y + 11, 1.5f, TICKET);
        g.drawString(font, tr("cashier_history"), x, y + 30, GameScreens.MUTED, false);
        List<ArcadeLedger.Play> history = menu.setup().history();
        if (history.isEmpty()) g.drawString(font, tr("history_empty_short"), x, y + 42, GameScreens.DIM, false);
        for (int index = 0; index < Math.min(6, history.size()); index++) {
            ArcadeLedger.Play play = history.get(index);
            int line = y + 42 + index * 10;
            GameScreens.fitted(g, font, tr("mode." + play.mode()), x, line, 62, GameScreens.TEXT);
            GameScreens.rightAligned(g, font, play.tickets() > 0 ? "+" + play.tickets() : "-",
                    leftPos + CasinoLayout.SIDE_X + CasinoLayout.SIDE_WIDTH - 7, line, play.tickets() > 0 ? TICKET : GameScreens.DIM);
        }
    }

    private void tray(GuiGraphics g) {
        int x = leftPos + CasinoLayout.SLIP_X + 9, y = topPos + CasinoLayout.SLIP_Y + 8;
        g.drawString(font, tr("cashier_tray"), x, y, GameScreens.MUTED, false);
        GameScreens.rightAligned(g, font, GameScreens.value(menu.itemValue() + menu.chipValue()),
                leftPos + CasinoLayout.SLIP_X + CasinoLayout.SLIP_WIDTH - 9, y, GameScreens.GOLD);
        for (int slot = 0; slot < CashierMenu.TRAY_SLOTS; slot++)
            GameScreens.slot(g, leftPos + CashierMenu.TRAY_X + slot * 18, topPos + CashierMenu.TRAY_Y);
    }

    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !menu.arcade().tickets()) return false;
        int first = page * COLUMNS * ROWS;
        for (int cell = 0; cell < COLUMNS * ROWS && first + cell < prizes().size(); cell++) {
            int cx = GRID_X + cell % COLUMNS * (CELL_WIDTH + GAP), cy = GRID_Y + cell / COLUMNS * (CELL_HEIGHT + GAP);
            if (!inside(mouseX, mouseY, cx, cy, CELL_WIDTH, CELL_HEIGHT)) continue;
            if (menu.balance() < prizes().get(first + cell).tickets()) return true;
            send(CashierMenu.PRIZE + first + cell);
            CasinoSounds.win();
            return true;
        }
        return false;
    }

    @Override protected boolean gameScrolled(double mouseX, double mouseY, double amount) {
        if (pages() <= 1 || !inside(mouseX, mouseY, CasinoLayout.GAME_X, CasinoLayout.CONTENT_Y, CasinoLayout.GAME_WIDTH, CasinoLayout.CONTENT_HEIGHT)) {
            return false;
        }
        page = Math.floorMod(page + (amount < 0 ? 1 : -1), pages());
        return true;
    }
}
