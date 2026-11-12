package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.core.prediction.PredictionMarket.Status;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.Nbt;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.prediction.MarketKind;
import dev.gamblingitems.fabric.prediction.MarketView;
import dev.gamblingitems.fabric.prediction.PredictionMarkets;
import dev.gamblingitems.fabric.prediction.PredictionMenu;
import dev.gamblingitems.fabric.prediction.PredictionSettings;
import dev.gamblingitems.fabric.prediction.Tickets;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The prediction market: the questions on the left with the probability the traders give them, the
 * selected one on the right with what the prepared stake would buy, and the form that opens a new
 * one. Every number shown comes from the server; this window only sends intentions.
 */
public final class PredictionScreen extends CasinoScreen<PredictionMenu> {
    private static final int WIDTH = 376, HEIGHT = 288;
    private static final int LIST_X = 10, LIST_Y = 34, LIST_WIDTH = 180, ROW_HEIGHT = 18, LIST_TOP = LIST_Y + 18;
    private static final int DETAIL_X = 196, DETAIL_Y = 34, DETAIL_WIDTH = 170, DETAIL_HEIGHT = 128;
    private static final int SLIP_Y = 166, SIDE_X = 186, SIDE_Y = 204;
    private static final int YES = 0xff2fbf71, NO = 0xffd9475e;
    private boolean creating;
    private Button previous, next, market, create, buyYes, buyNo, proposeYes, proposeNo, dispute;
    private Button kind, subjectPrevious, subjectNext, targetPrevious, targetNext, timePrevious, timeNext, publish;
    private Button cash, collect, claim;
    private final List<Button> marketButtons = new ArrayList<>(), createButtons = new ArrayList<>();

    public PredictionScreen(PredictionMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, WIDTH, HEIGHT);
    }

    @Override protected GameMode mode() { return null; }
    @Override protected boolean portable() { return menu.portable(); }
    @Override protected String key() { return "prediction_market"; }
    @Override protected ItemStack headerIcon() { return new ItemStack(ModContent.PREDICTION_TICKET); }
    @Override protected List<GameMode> tabFamily() { return GameMenus.CASINO; }
    @Override protected boolean casinoTabs() { return true; }
    @Override protected boolean predictionScreen() { return true; }
    @Override protected Component subtitle() {
        return Component.translatable("gui.gamblingitems.market.subtitle", GameScreens.value(menu.settings().shareValue()));
    }

    @Override protected void init() {
        super.init();
        marketButtons.clear();
        createButtons.clear();
        int x = leftPos, y = topPos;
        previous = addRenderableWidget(CasinoButton.quiet(Component.literal("<"), b -> send(PredictionMenu.PAGE_PREVIOUS))
                .bounds(x + LIST_X + LIST_WIDTH - 50, y + LIST_Y + 2, 14, 14).build());
        next = addRenderableWidget(CasinoButton.quiet(Component.literal(">"), b -> send(PredictionMenu.PAGE_NEXT))
                .bounds(x + LIST_X + LIST_WIDTH - 16, y + LIST_Y + 2, 14, 14).build());
        int half = (DETAIL_WIDTH - 12) / 2;
        market = addRenderableWidget(CasinoButton.builder(tr("market.tab.trade"), b -> creating = false)
                .bounds(x + DETAIL_X + 4, y + DETAIL_Y + 3, half, 14).build());
        create = addRenderableWidget(CasinoButton.builder(tr("market.tab.create"), b -> creating = true)
                .bounds(x + DETAIL_X + 8 + half, y + DETAIL_Y + 3, half, 14).build());
        create.setTooltip(Tooltip.create(tr("market.create_help")));

        int buyY = y + DETAIL_Y + DETAIL_HEIGHT - 38;
        buyYes = market(CasinoButton.primary(tr("market.buy_yes"), b -> send(PredictionMenu.BUY_YES))
                .bounds(x + DETAIL_X + 4, buyY, half, 16).build());
        buyNo = market(CasinoButton.risk(tr("market.buy_no"), b -> send(PredictionMenu.BUY_NO))
                .bounds(x + DETAIL_X + 8 + half, buyY, half, 16).build());
        proposeYes = market(CasinoButton.builder(tr("market.propose_yes"), b -> send(PredictionMenu.PROPOSE_YES))
                .bounds(x + DETAIL_X + 4, buyY + 19, half, 14).build());
        proposeNo = market(CasinoButton.builder(tr("market.propose_no"), b -> send(PredictionMenu.PROPOSE_NO))
                .bounds(x + DETAIL_X + 8 + half, buyY + 19, half, 14).build());
        dispute = market(CasinoButton.risk(tr("market.dispute"), b -> send(PredictionMenu.DISPUTE))
                .bounds(x + DETAIL_X + 4, buyY + 19, DETAIL_WIDTH - 8, 14).build());
        dispute.setTooltip(Tooltip.create(tr("market.dispute_help")));

        int row = y + DETAIL_Y + 22, arrow = 14, field = x + DETAIL_X + DETAIL_WIDTH - 4 - arrow;
        kind = form(CasinoButton.builder(Component.empty(), b -> send(PredictionMenu.DRAFT_KIND))
                .bounds(x + DETAIL_X + 56, row, DETAIL_WIDTH - 60, 14).build());
        subjectPrevious = form(arrow(x + DETAIL_X + 56, row + 17, "<", PredictionMenu.SUBJECT_PREVIOUS));
        subjectNext = form(arrow(field, row + 17, ">", PredictionMenu.SUBJECT_NEXT));
        targetPrevious = form(arrow(x + DETAIL_X + 56, row + 34, "<", PredictionMenu.TARGET_PREVIOUS));
        targetNext = form(arrow(field, row + 34, ">", PredictionMenu.TARGET_NEXT));
        timePrevious = form(arrow(x + DETAIL_X + 56, row + 51, "<", PredictionMenu.TIME_PREVIOUS));
        timeNext = form(arrow(field, row + 51, ">", PredictionMenu.TIME_NEXT));
        publish = form(CasinoButton.primary(tr("market.publish"), b -> send(PredictionMenu.PUBLISH))
                .bounds(x + DETAIL_X + 4, y + DETAIL_Y + DETAIL_HEIGHT - 20, DETAIL_WIDTH - 8, 16).build());

        cash = addRenderableWidget(CasinoButton.builder(tr("market.cash"), b -> send(PredictionMenu.CASH))
                .bounds(x + LIST_X, y + SLIP_Y + 14, 120, 16).build());
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_short"), b -> send(PredictionMenu.COLLECT))
                .bounds(x + SIDE_X, y + SIDE_Y + 38, WIDTH - SIDE_X - 10, 16).build());
        collect.setTooltip(Tooltip.create(tr("market.collect_help")));
        claim = addRenderableWidget(CasinoButton.builder(tr("market.claim"), b -> send(PredictionMenu.CLAIM))
                .bounds(x + SIDE_X, y + SIDE_Y + 58, WIDTH - SIDE_X - 10, 16).build());
        claim.setTooltip(Tooltip.create(tr("market.claim_help")));
        refresh();
    }

    private Button market(Button button) {
        marketButtons.add(addRenderableWidget(button));
        return button;
    }

    private Button form(Button button) {
        createButtons.add(addRenderableWidget(button));
        return button;
    }

    private Button arrow(int x, int y, String label, int action) {
        return CasinoButton.quiet(Component.literal(label), b -> send(action)).bounds(x, y, 14, 14).build();
    }

    @Override protected void containerTick() {
        super.containerTick();
        refresh();
    }

    /** Which controls a question offers depends on its state and on who is looking at it. */
    private void refresh() {
        previous.active = menu.page() > 0;
        next.active = menu.page() + 1 < menu.pages();
        MarketView view = menu.selectedView();
        boolean trading = view != null && view.status() == Status.OPEN;
        for (Button button : marketButtons) button.visible = !creating;
        for (Button button : createButtons) button.visible = creating;
        buyYes.active = trading && menu.quote(Side.YES) > 0 && menu.bannedSeconds() == 0;
        buyNo.active = trading && menu.quote(Side.NO) > 0 && menu.bannedSeconds() == 0;
        buyYes.setMessage(trading && menu.stake() > 0 ? Component.translatable("gui.gamblingitems.market.buy_yes_amount",
                Tickets.shares(menu.quote(Side.YES))) : tr("market.buy_yes"));
        buyNo.setMessage(trading && menu.stake() > 0 ? Component.translatable("gui.gamblingitems.market.buy_no_amount",
                Tickets.shares(menu.quote(Side.NO))) : tr("market.buy_no"));
        boolean answerable = view != null && view.kind() == MarketKind.CUSTOM && view.mine()
                && (view.status() == Status.OPEN || view.status() == Status.CLOSED);
        proposeYes.visible = proposeNo.visible = !creating && answerable;
        dispute.visible = !creating && view != null && view.status() == Status.PROPOSED && !view.mine();

        CompoundTag draft = menu.draft();
        MarketKind draftKind = draftKind(draft);
        kind.setMessage(tr("market.kind." + draftKind.id()));
        subjectPrevious.visible = subjectNext.visible = creating && draftKind.aboutPlayer();
        targetPrevious.visible = targetNext.visible = creating && draftKind != MarketKind.CUSTOM;
        publish.active = Nbt.string(draft, "refusal").isEmpty() && menu.bannedSeconds() == 0;
        publish.setMessage(menu.stake() > 0 ? Component.translatable("gui.gamblingitems.market.publish_amount",
                GameScreens.value(menu.stake())) : tr("market.publish"));

        int ticket = menu.ticketState();
        cash.active = ticket == PredictionMarkets.Quote.SELL || ticket == PredictionMarkets.Quote.REDEEM
                || ticket == PredictionMarkets.Quote.WORTHLESS;
        cash.setMessage(switch (ticket) {
            case PredictionMarkets.Quote.SELL -> Component.translatable("gui.gamblingitems.market.sell_amount", GameScreens.value(menu.ticketValue()));
            case PredictionMarkets.Quote.REDEEM -> Component.translatable("gui.gamblingitems.market.redeem_amount", GameScreens.value(menu.ticketValue()));
            case PredictionMarkets.Quote.WORTHLESS -> tr("market.discard");
            case PredictionMarkets.Quote.WAITING -> tr("market.waiting");
            default -> tr("market.cash");
        });
        collect.active = menu.winnings() > 0;
        collect.setMessage(menu.winnings() > 0 ? Component.translatable("gui.gamblingitems.collect_amount",
                GameScreens.value(menu.winnings())) : tr("collect_short"));
        claim.active = menu.credit() > 0;
        claim.setMessage(menu.credit() > 0 ? Component.translatable("gui.gamblingitems.market.claim_amount",
                GameScreens.value(menu.credit())) : tr("market.claim"));
    }

    private static MarketKind draftKind(CompoundTag draft) {
        try {
            return MarketKind.fromId(Nbt.string(draft, "kind"));
        } catch (IllegalArgumentException missing) {
            return MarketKind.ADVANCEMENT;
        }
    }

    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        if (rules.open()) return false;
        for (int row = 0; row < PredictionMenu.ROWS; row++) {
            if (menu.row(row) != null && inside(mouseX, mouseY, LIST_X, LIST_TOP + row * ROW_HEIGHT, LIST_WIDTH, ROW_HEIGHT - 2)) {
                send(PredictionMenu.FIRST_SELECT + row);
                creating = false;
                return true;
            }
        }
        return false;
    }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        renderList(g, x, y, mouseX, mouseY);
        GameScreens.card(g, x + DETAIL_X, y + DETAIL_Y, DETAIL_WIDTH, DETAIL_HEIGHT);
        // The open side of the panel is underlined, as the tabs of a portable item are.
        Button open = creating ? create : market;
        ArenaShapes.rounded(g, open.getX() + 6, open.getY() + open.getHeight() + 1, open.getWidth() - 12, 2, 1, GameScreens.GREEN);
        if (creating) renderForm(g, x + DETAIL_X + 6, y + DETAIL_Y + 22);
        else renderDetail(g, x + DETAIL_X + 6, y + DETAIL_Y + 22);
        renderSlip(g, x, y);
    }

    private void renderList(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        GameScreens.card(g, x + LIST_X, y + LIST_Y, LIST_WIDTH, DETAIL_HEIGHT);
        GameScreens.label(g, font, tr("market.list"), x + LIST_X + 6, y + LIST_Y + 6, 100);
        String pages = (menu.page() + 1) + "/" + menu.pages();
        g.drawCenteredString(font, pages, x + LIST_X + LIST_WIDTH - 26, y + LIST_Y + 5, GameScreens.MUTED);
        boolean any = false;
        for (int row = 0; row < PredictionMenu.ROWS; row++) {
            MarketView view = menu.row(row);
            if (view == null) continue;
            any = true;
            int top = y + LIST_TOP + row * ROW_HEIGHT, left = x + LIST_X + 3;
            boolean selected = row == menu.selected();
            boolean hovered = inside(mouseX, mouseY, LIST_X, LIST_TOP + row * ROW_HEIGHT, LIST_WIDTH, ROW_HEIGHT - 2);
            GameScreens.rounded(g, left, top, LIST_WIDTH - 6, ROW_HEIGHT - 2,
                    selected ? GameScreens.SELECTION : hovered ? GameScreens.HOVER : GameScreens.RAISED);
            GameScreens.fitted(g, font, view.text(), left + 4, top + 1, LIST_WIDTH - 50, GameScreens.TEXT);
            String price = view.price() / 100 + "%";
            g.drawString(font, price, left + LIST_WIDTH - 10 - font.width(price), top + 1,
                    view.status() == Status.OPEN ? YES : GameScreens.MUTED, false);
            GameScreens.bar(g, left + 4, top + 11, 60, 3, view.price() / 10_000.0, YES);
            GuiPose.push(g);
            GuiPose.translate(g, left + 70, top + 10);
            GuiPose.scale(g, 0.75f);
            GameScreens.fitted(g, font, status(view), 0, 0, (int) ((LIST_WIDTH - 82) / 0.75f), statusColour(view));
            GuiPose.pop(g);
        }
        if (!any) GameScreens.paragraph(g, font, tr("market.empty"), x + LIST_X + 8, y + LIST_TOP + 4,
                LIST_WIDTH - 16, 6, GameScreens.MUTED);
    }

    private Component status(MarketView view) {
        return switch (view.status()) {
            case OPEN -> Component.translatable("gui.gamblingitems.market.closes_in", duration(view.closesIn()));
            case PROPOSED -> Component.translatable("gui.gamblingitems.market.proposed_short", MarketView.side(view.proposal()),
                    duration(view.challengeIn()));
            case RESOLVED -> Component.translatable("gui.gamblingitems.market.resolved_short", MarketView.side(view.outcome()));
            default -> tr("market.status." + view.status().id());
        };
    }

    private static int statusColour(MarketView view) {
        return switch (view.status()) {
            case OPEN -> GameScreens.MUTED;
            case PROPOSED, CLOSED -> GameScreens.GOLD;
            case DISPUTED, CANCELLED -> GameScreens.RED;
            case RESOLVED -> view.outcome() == Side.YES ? YES : NO;
        };
    }

    /** A clock read the way a player says it: minutes, hours, days. */
    private static Component duration(long millis) {
        long minutes = Math.max(0, (millis + 59_999) / 60_000);
        if (minutes < 60) return Component.translatable("gui.gamblingitems.market.minutes", minutes);
        if (minutes < 2 * 1_440) return Component.translatable("gui.gamblingitems.market.hours_minutes", minutes / 60, minutes % 60);
        return Component.translatable("gui.gamblingitems.market.days", minutes / 1_440);
    }

    private void renderDetail(GuiGraphics g, int x, int y) {
        MarketView view = menu.selectedView();
        int width = DETAIL_WIDTH - 12;
        if (view == null) {
            GameScreens.paragraph(g, font, tr("market.select"), x, y, width, 4, GameScreens.MUTED);
            return;
        }
        GameScreens.paragraph(g, font, view.text(), x, y, width, 2, GameScreens.TEXT);
        int priceY = y + 22;
        GameScreens.heading(g, font, Component.literal(view.price() / 100 + "%"), x, priceY, 2, YES);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.market.yes_no",
                view.price() / 100, 100 - view.price() / 100), x + 56, priceY + 4, width - 56, GameScreens.MUTED);
        GameScreens.bar(g, x, priceY + 18, width, 4, view.price() / 10_000.0, YES);
        GameScreens.fitted(g, font, status(view), x, priceY + 26, width, statusColour(view));
        Component who = view.house() ? tr("market.by_house")
                : Component.translatable("gui.gamblingitems.market.by", view.creator());
        if (view.kind().counted()) who = who.copy().append(" · ").append(Component.translatable(
                "gui.gamblingitems.market.progress", view.progress(), view.threshold()));
        GameScreens.fitted(g, font, who, x, priceY + 37, width, GameScreens.DIM);
        if (menu.bannedSeconds() > 0) {
            GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.security.still_banned", menu.bannedSeconds()),
                    x, priceY + 37, width, GameScreens.RED);
        } else if (view.aboutViewer() && view.status() == Status.OPEN && view.kind().sabotage() != null) {
            // Betting on oneself to fail is allowed, and watched.
            GameScreens.fitted(g, font, tr("market.insider_warning"), x, priceY + 37, width, GameScreens.RED);
        }
    }

    private void renderForm(GuiGraphics g, int x, int y) {
        CompoundTag draft = menu.draft();
        MarketKind kind = draftKind(draft);
        int width = DETAIL_WIDTH - 12, value = x + 66, valueWidth = DETAIL_WIDTH - 98;
        GameScreens.fitted(g, font, tr("market.form.kind"), x, y + 3, 48, GameScreens.MUTED);
        GameScreens.fitted(g, font, tr("market.form.subject"), x, y + 20, 48, GameScreens.MUTED);
        GameScreens.fitted(g, font, tr(kind == MarketKind.ADVANCEMENT ? "market.form.advancement"
                : kind == MarketKind.CUSTOM ? "market.form.question" : "market.form.threshold"), x, y + 37, 48, GameScreens.MUTED);
        GameScreens.fitted(g, font, tr("market.form.duration"), x, y + 54, 48, GameScreens.MUTED);
        String subject = Nbt.string(draft, "subject");
        GameScreens.fitted(g, font, kind.aboutPlayer() ? Component.literal(subject.isEmpty() ? "-" : subject)
                : tr("market.form.nobody"), value, y + 20, valueWidth, GameScreens.TEXT);
        Component target = switch (kind) {
            case ADVANCEMENT -> MarketView.advancement(Nbt.string(draft, "target"));
            case CUSTOM -> Nbt.string(draft, "question").isEmpty() ? tr("market.form.paper")
                    : Component.literal(Nbt.string(draft, "question"));
            default -> Component.literal(String.valueOf(Nbt.integer(draft, "threshold", 1)));
        };
        GameScreens.fitted(g, font, target, kind == MarketKind.CUSTOM ? x + 52 : value, y + 37,
                kind == MarketKind.CUSTOM ? width - 52 : valueWidth, GameScreens.TEXT);
        GameScreens.fitted(g, font, duration(Nbt.integer(draft, "minutes", 60) * 60_000L), value, y + 54, valueWidth, GameScreens.TEXT);
        Component preview = MarketView.question(kind, subject, Nbt.string(draft, "target"),
                Nbt.integer(draft, "threshold", 1), Nbt.string(draft, "question"));
        GameScreens.paragraph(g, font, preview, x, y + 70, width, 1, GameScreens.GOLD);
        String refusal = Nbt.string(draft, "refusal");
        Component hint = refusal.isEmpty() ? Component.translatable("gui.gamblingitems.market.form.ready",
                GameScreens.value(menu.stake()), menu.settings().feeBasisPoints() / 100.0)
                : Component.translatable("gui.gamblingitems.market.refused." + refusal,
                        GameScreens.value(menu.settings().minimumSubsidy()));
        GameScreens.fitted(g, font, hint, x, y + 82, width, refusal.isEmpty() ? GameScreens.MUTED : GameScreens.RED);
    }

    /** The stake, the ticket and the written question, under the market. */
    private void renderSlip(GuiGraphics g, int x, int y) {
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.market.stake_row", GameScreens.value(menu.stake())),
                x + PredictionMenu.STAKE_X, y + SLIP_Y, 96, GameScreens.MUTED);
        GameScreens.fitted(g, font, tr("market.ticket"), x + PredictionMenu.TICKET_X - 2, y + SLIP_Y, 30, GameScreens.MUTED);
        GameScreens.fitted(g, font, Component.literal("?"), x + PredictionMenu.QUESTION_X + 5, y + SLIP_Y, 12, GameScreens.MUTED);
        for (int index = 0; index < PredictionSettings.STAKE_SLOTS; index++)
            GameScreens.slot(g, x + PredictionMenu.STAKE_X + index * 18, y + PredictionMenu.STAKE_Y);
        GameScreens.slot(g, x + PredictionMenu.TICKET_X, y + PredictionMenu.TICKET_Y);
        GameScreens.slot(g, x + PredictionMenu.QUESTION_X, y + PredictionMenu.QUESTION_Y);
        if (menu.ticketShares() > 0) {
            GameScreens.fitted(g, font, Component.translatable("item.gamblingitems.prediction_ticket.shares",
                    Tickets.shares(menu.ticketShares())), x + LIST_X + 126, y + SLIP_Y + 18, 96, GameScreens.MUTED);
        }
        GameScreens.fitted(g, font, tr("market.ticket_help"), x + LIST_X, y + SLIP_Y + 2, 196, GameScreens.DIM);
        GameScreens.label(g, font, tr("winnings"), x + SIDE_X, y + SIDE_Y, 80);
        GameScreens.fitted(g, font, Component.literal(GameScreens.value(menu.winnings())), x + SIDE_X, y + SIDE_Y + 12, 80,
                menu.winnings() > 0 ? GameScreens.GOLD : GameScreens.MUTED);
        GameScreens.label(g, font, tr("market.credit"), x + SIDE_X + 92, y + SIDE_Y, 80);
        GameScreens.fitted(g, font, Component.literal(GameScreens.value(menu.credit())), x + SIDE_X + 92, y + SIDE_Y + 12, 80,
                menu.credit() > 0 ? GameScreens.GOLD : GameScreens.MUTED);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
