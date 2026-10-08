package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpTable;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Five comparable items in, one better item out: the contract, its odds, and the reel that settles it. */
public final class TradeUpScreen extends CasinoScreen<TradeUpMenu> {
    private static final int LOOPS = 5, CELL = 30;
    private static final int GX = CasinoLayout.GAME_X, GY = CasinoLayout.CONTENT_Y;
    private static final int SX = CasinoLayout.SIDE_X, SW = CasinoLayout.SIDE_WIDTH;
    private static final int LIST_Y = GY + 18, LIST_HEIGHT = CasinoLayout.CONTENT_HEIGHT - 22;
    private final OddsList odds = new OddsList();
    private final CasinoSounds.Reel ticks = new CasinoSounds.Reel();
    private Button trade;
    /** Kept so the reel can still show the contract after the server has consumed the stake. */
    private TradeUpTable shown;
    private boolean wasAnimating;
    /** The value played, still shown while the reel turns after the server has taken the items. */
    private long stake;

    public TradeUpScreen(TradeUpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected GameMode mode() { return GameMode.TRADE_UP; }
    @Override protected boolean portable() { return menu.portable(); }

    @Override protected void init() {
        super.init();
        trade = addRenderableWidget(CasinoButton.primary(tr("trade"), button -> {
                    if (settled()) collect(TradeUpMenu.REWARD_SLOT); else send(TradeUpMenu.SPIN_BUTTON);
                })
                .bounds(leftPos + CasinoLayout.SLIP_X + 8, topPos + CasinoLayout.SLIP_Y + 52, CasinoLayout.SLIP_WIDTH - 16, 20).build());
        trade.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.trade_warning",
                menu.setup().settings().requiredUnits(), ratio())));
        refresh();
    }

    private String ratio() {
        return BigDecimal.valueOf(menu.setup().settings().unitRatioBasisPoints(), 4).stripTrailingZeros().toPlainString();
    }

    @Override protected void containerTick() {
        super.containerTick();
        // The contract stays on screen until the reward is collected, then follows the slots again.
        if (!menu.isAnimating() && menu.reward().isEmpty()) {
            TradeUpTable table = menu.table().orElse(null);
            if (table == null || shown == null || !table.rewards().equals(shown.rewards())) odds.reset();
            shown = table;
        }
        if (wasAnimating && !menu.isAnimating() && !menu.reward().isEmpty()) {
            CasinoSounds.win();
            if (shown != null) odds.reveal(menu.resultIndex(), shown.rewards().size(), LIST_HEIGHT);
        }
        if (!menu.isAnimating() && menu.reward().isEmpty()) stake = menu.stakeValue();
        wasAnimating = menu.isAnimating();
        refresh();
    }

    private boolean settled() { return !menu.isAnimating() && !menu.reward().isEmpty(); }

    private void refresh() {
        trade.active = menu.canSpin() || settled();
        int required = menu.setup().settings().requiredUnits();
        trade.setMessage(menu.isAnimating() ? tr("rolling")
                : !menu.reward().isEmpty() ? tr("collect_short")
                : Component.translatable("gui.gamblingitems.arena.exchange", required));
    }

    private List<OddsList.Row> rows() {
        List<OddsList.Row> rows = new ArrayList<>();
        if (shown == null) return rows;
        boolean settled = !menu.isAnimating() && !menu.reward().isEmpty();
        for (int i = 0; i < shown.rewards().size(); i++) {
            ValueCatalog.Entry entry = shown.rewards().get(i);
            String chance = shown.percentOf(i).setScale(1, RoundingMode.HALF_UP).toPlainString() + "%";
            rows.add(new OddsList.Row(entry.stack(), chance, GameScreens.GOLD,
                    Component.translatable("gui.gamblingitems.arena.item_value", GameScreens.value(entry.value())),
                    true, settled && menu.resultIndex() == i));
        }
        return rows;
    }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        int required = menu.setup().settings().requiredUnits();
        GameScreens.card(g, x + GX, y + GY, CasinoLayout.GAME_WIDTH, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.label(g, font, tr("arena.contract_items"), x + GX + 8, y + GY + 7, 150);
        String units = menu.stakedUnits() + " / " + required;
        GameScreens.rightAligned(g, font, units, x + GX + CasinoLayout.GAME_WIDTH - 8, y + GY + 7,
                menu.stakedUnits() == required ? GameScreens.GREEN : GameScreens.GOLD);
        if (menu.isAnimating()) {
            renderReel(g, x, y, tick);
        } else {
            for (int index = 0; index < TradeUpMenu.INPUT_SLOTS; index++)
                GameScreens.well(g, font, x + TradeUpMenu.INPUT_X + index * TradeUpMenu.INPUT_GAP, y + TradeUpMenu.INPUT_Y,
                        !menu.getSlot(index).hasItem(), menu.getSlot(index).hasItem() ? GameScreens.GREEN : 0);
            g.drawCenteredString(font, "→", x + TradeUpMenu.REWARD_X - 18, y + TradeUpMenu.REWARD_Y + 4, GameScreens.DIM);
            boolean won = !menu.reward().isEmpty();
            if (won) GameScreens.ring(g, x + TradeUpMenu.REWARD_X - 4, y + TradeUpMenu.REWARD_Y - 4, 24, 24, GameScreens.GOLD);
            GameScreens.well(g, font, x + TradeUpMenu.REWARD_X, y + TradeUpMenu.REWARD_Y, false, 0);
            if (!won) g.drawCenteredString(font, "?", x + TradeUpMenu.REWARD_X + 8, y + TradeUpMenu.REWARD_Y + 4, GameScreens.DIM);
        }
        renderStatus(g, x, y);
        GameScreens.paragraph(g, font, Component.translatable("gui.gamblingitems.trade_up_subtitle", required, ratio()),
                x + GX + 8, y + GY + 80, CasinoLayout.GAME_WIDTH - 16, 2, GameScreens.DIM);

        GameScreens.card(g, x + SX, y + GY, SW, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.label(g, font, tr("possible_rewards"), x + SX + 6, y + GY + 7, SW - 12);
        if (shown == null) {
            GameScreens.paragraph(g, font, tr("no_contract_hint"), x + SX + 6, y + LIST_Y + 4, SW - 12, 6, GameScreens.DIM);
        } else {
            odds.render(g, font, rows(), x + SX + 4, y + LIST_Y, SW - 6, LIST_HEIGHT, mouseX, mouseY);
        }

        slip(g);
        int sx = x + CasinoLayout.SLIP_X + 8, sy = y + CasinoLayout.SLIP_Y + 8;
        boolean settled = !menu.reward().isEmpty() && !menu.isAnimating();
        GameScreens.label(g, font, tr(settled ? "reward" : "stake"), sx, sy, 80);
        GameScreens.heading(g, font, Component.literal(GameScreens.value(settled ? menu.catalog().valueOf(menu.reward()) : menu.isAnimating() ? stake : menu.stakeValue())),
                sx, sy + 12, 2, settled ? GameScreens.GOLD : GameScreens.TEXT);
        GameScreens.label(g, font, tr("average_reward"), sx + 92, sy, 80);
        g.drawString(font, shown == null ? "—" : GameScreens.value(shown.averageValue(menu.setup()).setScale(0, RoundingMode.HALF_UP).longValueExact()),
                sx + 92, sy + 16, GameScreens.GOLD, false);
    }

    private void renderStatus(GuiGraphics g, int x, int y) {
        int units = menu.stakedUnits();
        int required = menu.setup().settings().requiredUnits();
        Component status;
        int colour = GameScreens.MUTED;
        if (menu.isAnimating()) {
            status = tr("rolling");
        } else if (!menu.reward().isEmpty()) {
            status = Component.translatable("gui.gamblingitems.won_item", menu.reward().getHoverName());
            colour = GameScreens.GOLD;
        } else if (units == 0) {
            status = Component.translatable("gui.gamblingitems.trade_insert", required);
        } else if (units != required) {
            status = Component.translatable("gui.gamblingitems.trade_units", units, required);
        } else if (menu.table().isEmpty()) {
            status = tr("trade_impossible");
            colour = GameScreens.RED;
        } else {
            status = tr("ready");
            colour = GameScreens.GREEN;
        }
        GameScreens.fitted(g, font, status, x + GX + 8, y + GY + 64, CasinoLayout.GAME_WIDTH - 16, colour);
    }

    private void renderReel(GuiGraphics g, int x, int y, float tick) {
        List<ItemStack> items = shown == null ? List.of() : shown.rewards().stream().map(ValueCatalog.Entry::stack).toList();
        double progress = Math.min(1, (TradeUpMenu.ANIMATION_TICKS - menu.remainingTicks() + tick) / TradeUpMenu.ANIMATION_TICKS);
        int index = menu.resultIndex();
        double stop = items.isEmpty() || index < 0 ? 0 : (double) LOOPS * items.size() + index;
        double position = stop * GameScreens.eased(progress);
        ticks.follow(position, progress);
        GameScreens.reel(g, items, position, x + GX + 6, y + GY + 18, CasinoLayout.GAME_WIDTH - 12, 40, CELL, 1.5f);
    }

    @Override protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float tick) {
        if (shown != null) odds.tooltip(g, font, rows(), leftPos + SX + 4, topPos + LIST_Y, SW - 6, LIST_HEIGHT, mouseX, mouseY);
    }

    @Override protected boolean gameScrolled(double x, double y, double amount) {
        if (shown == null || !inside(x, y, SX, GY, SW, CasinoLayout.CONTENT_HEIGHT)) return false;
        odds.scroll(shown.rewards().size(), LIST_HEIGHT, amount);
        return true;
    }
}
