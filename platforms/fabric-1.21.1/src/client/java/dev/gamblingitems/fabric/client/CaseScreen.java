package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.cases.CaseReward;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Pick a case, pay the announced price, watch the reel stop on what the server already drew. */
public final class CaseScreen extends CasinoScreen<CaseMenu> {
    private static final int LOOPS = 5, CELL = 30;
    private static final int GX = CasinoLayout.GAME_X, GY = CasinoLayout.CONTENT_Y, GW = CasinoLayout.GAME_WIDTH;
    private static final int SX = CasinoLayout.SIDE_X, SW = CasinoLayout.SIDE_WIDTH;
    private static final int LIST_Y = GY + 18, LIST_HEIGHT = CasinoLayout.CONTENT_HEIGHT - 22;
    private final OddsList contents = new OddsList();
    private final CasinoSounds.Reel ticks = new CasinoSounds.Reel();
    private Button previous, next, open;
    private boolean wasAnimating;
    private int shownCase = -1;

    public CaseScreen(CaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected GameMode mode() { return GameMode.CASE_OPENING; }
    @Override protected boolean portable() { return menu.portable(); }

    @Override protected void init() {
        super.init();
        previous = addRenderableWidget(CasinoButton.quiet(Component.literal("‹"), button -> select(-1))
                .bounds(leftPos + GX + 4, topPos + GY + 3, 16, 14).build());
        next = addRenderableWidget(CasinoButton.quiet(Component.literal("›"), button -> select(1))
                .bounds(leftPos + GX + GW - 20, topPos + GY + 3, 16, 14).build());
        previous.setTooltip(Tooltip.create(tr("previous_case")));
        next.setTooltip(Tooltip.create(tr("next_case")));
        open = addRenderableWidget(CasinoButton.primary(tr("open_case"), button -> {
                    if (settled()) collect(CaseMenu.REWARD_SLOT); else send(CaseMenu.OPEN_BUTTON);
                })
                .bounds(leftPos + CasinoLayout.SLIP_X + 8, topPos + CasinoLayout.SLIP_Y + 52, CasinoLayout.SLIP_WIDTH - 16, 20).build());
        open.setTooltip(Tooltip.create(tr("case_warning")));
        refresh();
    }

    /** The client only asks for a selection; the server owns it and refuses it while a case is opening. */
    private void select(int step) {
        int count = menu.setup().cases().cases().size();
        if (count == 0) return;
        send(Math.floorMod(menu.selectedIndex() + step, count));
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (menu.selectedIndex() != shownCase) { shownCase = menu.selectedIndex(); contents.reset(); }
        if (wasAnimating && !menu.isAnimating() && !menu.reward().isEmpty()) {
            CasinoSounds.win();
            CaseDefinition definition = menu.selected();
            if (definition != null) contents.reveal(menu.resultIndex(), definition.rewards().size(), LIST_HEIGHT);
        }
        if (!menu.isAnimating()) ticks.reset();
        wasAnimating = menu.isAnimating();
        refresh();
    }

    private boolean settled() { return !menu.isAnimating() && !menu.reward().isEmpty(); }

    private void refresh() {
        boolean idle = !menu.isAnimating();
        boolean several = menu.setup().cases().cases().size() > 1;
        previous.active = idle && several;
        next.active = idle && several;
        open.active = menu.canOpen() || settled();
        open.setMessage(menu.isAnimating() ? tr("rolling") : !menu.reward().isEmpty() ? tr("collect_short") : tr("open_case"));
    }

    private List<OddsList.Row> rows(CaseDefinition definition) {
        List<OddsList.Row> rows = new ArrayList<>();
        if (definition == null) return rows;
        boolean settled = !menu.isAnimating() && !menu.reward().isEmpty();
        for (int i = 0; i < definition.rewards().size(); i++) {
            CaseReward reward = definition.rewards().get(i);
            String chance = definition.percentOf(i).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
            rows.add(new OddsList.Row(reward.stack(), chance, GameScreens.GOLD,
                    Component.translatable("gui.gamblingitems.arena.item_value", GameScreens.value(menu.catalog().valueOf(reward.stack()))),
                    true, settled && menu.resultIndex() == i));
        }
        return rows;
    }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        CaseDefinition definition = menu.selected();
        GameScreens.card(g, x + GX, y + GY, GW, CasinoLayout.CONTENT_HEIGHT);
        Component name = definition == null ? tr("no_case") : definition.title();
        g.drawCenteredString(font, name, x + GX + GW / 2, y + GY + 6, GameScreens.GOLD);
        renderReel(g, x, y, definition, tick);

        GameScreens.well(g, font, x + 22, y + 110, menu.payment().isEmpty(), menu.isPaid() ? GameScreens.GREEN : 0);
        boolean won = !menu.reward().isEmpty() && !menu.isAnimating();
        if (won) GameScreens.ring(g, x + 202, y + 106, 24, 24, GameScreens.GOLD);
        GameScreens.well(g, font, x + 206, y + 110, false, 0);
        if (!won) g.drawCenteredString(font, "?", x + 214, y + 114, GameScreens.DIM);
        renderStatus(g, x, y, definition);

        GameScreens.card(g, x + SX, y + GY, SW, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.label(g, font, tr("case_contents"), x + SX + 6, y + GY + 7, SW - 12);
        contents.render(g, font, rows(definition), x + SX + 4, y + LIST_Y, SW - 6, LIST_HEIGHT, mouseX, mouseY);

        slip(g);
        int sx = x + CasinoLayout.SLIP_X + 8, sy = y + CasinoLayout.SLIP_Y + 8;
        if (definition == null) return;
        GameScreens.label(g, font, tr("price"), sx, sy, 80);
        GameScreens.item(g, definition.priceStack(), sx, sy + 12, 1);
        g.renderItemDecorations(font, definition.priceStack(), sx, sy + 12);
        GameScreens.fitted(g, font, definition.priceStack().getHoverName(), sx + 20, sy + 16, 70, GameScreens.TEXT);
        GameScreens.label(g, font, tr("average_reward"), sx + 100, sy, 70);
        g.drawString(font, GameScreens.value(definition.averageValue(menu.catalog()).setScale(0, RoundingMode.HALF_UP).longValueExact()),
                sx + 100, sy + 16, GameScreens.GOLD, false);
    }

    private void renderReel(GuiGraphics g, int x, int y, CaseDefinition definition, float tick) {
        List<ItemStack> items = definition == null ? List.of() : definition.rewards().stream().map(CaseReward::stack).toList();
        int index = menu.resultIndex();
        double stop = items.isEmpty() || index < 0 ? 0 : (double) LOOPS * items.size() + index;
        double progress = menu.isAnimating()
                ? Math.min(1, (CaseMenu.ANIMATION_TICKS - menu.remainingTicks() + tick) / CaseMenu.ANIMATION_TICKS) : 1;
        double position = stop * GameScreens.eased(progress);
        if (menu.isAnimating()) ticks.follow(position, progress);
        GameScreens.reel(g, items, position, x + GX + 6, y + GY + 22, GW - 12, 44, CELL, 1.5f);
    }

    private void renderStatus(GuiGraphics g, int x, int y, CaseDefinition definition) {
        Component status;
        int colour = GameScreens.MUTED;
        if (menu.isAnimating()) {
            status = tr("rolling");
        } else if (!menu.reward().isEmpty()) {
            status = Component.translatable("gui.gamblingitems.won_item", menu.reward().getHoverName());
            colour = GameScreens.GOLD;
        } else if (definition == null) {
            status = tr("no_case");
        } else if (!menu.isPaid()) {
            status = Component.translatable("gui.gamblingitems.case_insert", definition.priceCount(),
                    definition.priceStack().getHoverName());
        } else {
            status = tr("ready");
            colour = GameScreens.GREEN;
        }
        GameScreens.fitted(g, font, status, x + 48, y + 114, 146, colour);
    }

    @Override protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float tick) {
        contents.tooltip(g, font, rows(menu.selected()), leftPos + SX + 4, topPos + LIST_Y, SW - 6, LIST_HEIGHT, mouseX, mouseY);
    }

    @Override protected boolean gameScrolled(double x, double y, double amount) {
        CaseDefinition definition = menu.selected();
        if (definition == null || !inside(x, y, SX, GY, SW, CasinoLayout.CONTENT_HEIGHT)) return false;
        contents.scroll(definition.rewards().size(), LIST_HEIGHT, amount);
        return true;
    }
}
