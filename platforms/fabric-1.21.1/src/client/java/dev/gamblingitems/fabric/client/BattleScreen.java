package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.battle.BattleRules;
import dev.gamblingitems.fabric.battle.BattleLobby;
import dev.gamblingitems.fabric.battle.BattleMenu;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A lobby and its battle: the same case for everyone, round by round, and one winner. */
public final class BattleScreen extends CasinoScreen<BattleMenu> {
    private static final int GX = CasinoLayout.GAME_X, GY = CasinoLayout.CONTENT_Y, GW = CasinoLayout.GAME_WIDTH;
    private static final int SX = CasinoLayout.SIDE_X, SW = CasinoLayout.SIDE_WIDTH;
    private static final int SEAT_WIDTH = 108, SEAT_HEIGHT = 38;
    private static final int ACTION_X = CasinoLayout.SLIP_X + 92, ACTION_WIDTH = CasinoLayout.SLIP_WIDTH - 100;
    private Button join, start, leave, collect, previousCase, nextCase, fewerRounds, moreRounds;
    private BattleLobby.Phase lastPhase;

    public BattleScreen(BattleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected GameMode mode() { return GameMode.CASE_BATTLE; }
    @Override protected boolean portable() { return menu.portable(); }

    @Override protected void init() {
        super.init();
        int ax = leftPos + ACTION_X, ay = topPos + CasinoLayout.SLIP_Y + 8;
        join = addRenderableWidget(CasinoButton.primary(tr("battle_join_short"), button -> send(BattleMenu.JOIN_BUTTON))
                .bounds(ax, ay, ACTION_WIDTH, 20).build());
        start = addRenderableWidget(CasinoButton.primary(tr("battle_start"), button -> send(BattleMenu.START_BUTTON))
                .bounds(ax, ay, ACTION_WIDTH, 20).build());
        leave = addRenderableWidget(CasinoButton.builder(tr("battle_leave"), button -> send(BattleMenu.LEAVE_BUTTON))
                .bounds(ax, ay + 24, ACTION_WIDTH, 18).build());
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_button"), button -> send(BattleMenu.COLLECT_BUTTON))
                .bounds(ax, ay + 46, ACTION_WIDTH, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
        int sx = leftPos + SX, sy = topPos + GY;
        previousCase = addRenderableWidget(CasinoButton.quiet(Component.literal("‹"), button -> chooseCase(-1))
                .bounds(sx + 3, sy + 17, 14, 14).build());
        nextCase = addRenderableWidget(CasinoButton.quiet(Component.literal("›"), button -> chooseCase(1))
                .bounds(sx + SW - 17, sy + 17, 14, 14).build());
        fewerRounds = addRenderableWidget(CasinoButton.quiet(Component.literal("−"), button -> chooseRounds(-1))
                .bounds(sx + 3, sy + 51, 14, 14).build());
        moreRounds = addRenderableWidget(CasinoButton.quiet(Component.literal("+"), button -> chooseRounds(1))
                .bounds(sx + SW - 17, sy + 51, 14, 14).build());
        refresh();
    }

    private void chooseCase(int step) {
        int count = menu.setup().cases().cases().cases().size();
        if (count == 0) return;
        send(BattleMenu.CASE_BUTTON + Math.floorMod(menu.caseIndex() + step, count));
    }

    private void chooseRounds(int step) {
        int rounds = Math.min(BattleRules.MAX_ROUNDS, Math.max(BattleRules.MIN_ROUNDS, menu.rounds() + step));
        send(BattleMenu.ROUNDS_BUTTON + rounds);
    }

    @Override protected void containerTick() {
        super.containerTick();
        if (lastPhase == BattleLobby.Phase.RUNNING && menu.phase() == BattleLobby.Phase.DONE) {
            if (menu.winnerSeat() >= 0 && menu.winnerSeat() == menu.mySeat()) CasinoSounds.win(); else CasinoSounds.lose();
        }
        lastPhase = menu.phase();
        refresh();
    }

    private void refresh() {
        boolean lobby = menu.phase() == BattleLobby.Phase.LOBBY;
        boolean empty = menu.players() == 0;
        join.visible = !menu.seated();
        start.visible = menu.seated();
        join.active = menu.canJoin();
        start.active = menu.canStart();
        leave.active = lobby && menu.seated();
        previousCase.active = nextCase.active = lobby && empty;
        fewerRounds.active = lobby && empty && menu.rounds() > BattleRules.MIN_ROUNDS;
        moreRounds.active = lobby && empty && menu.rounds() < BattleRules.MAX_ROUNDS;
        join.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.battle_entry",
                menu.entry().getCount(), menu.definition().priceStack().getHoverName())));
    }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        CaseDefinition definition = menu.definition();
        GameScreens.card(g, x + GX, y + GY, GW, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.label(g, font, tr("battle_lobby"), x + GX + 8, y + GY + 7, 80);
        Component phase = switch (menu.phase()) {
            case LOBBY -> menu.players() == 0 ? tr("battle_waiting")
                    : Component.translatable("gui.gamblingitems.battle_countdown", (menu.phaseTicks() + 19) / 20, menu.players());
            case RUNNING -> Component.translatable("gui.gamblingitems.battle_round", menu.round(), menu.rounds());
            case DONE -> menu.winnerSeat() >= 0
                    ? Component.translatable("gui.gamblingitems.battle_winner", menu.winnerSeat() + 1, GameScreens.value(menu.prize()))
                    : tr("battle_waiting");
        };
        String text = font.plainSubstrByWidth(phase.getString(), GW - 90);
        GameScreens.rightAligned(g, font, text, x + GX + GW - 8, y + GY + 7,
                menu.phase() == BattleLobby.Phase.DONE ? GameScreens.GOLD : menu.phase() == BattleLobby.Phase.RUNNING ? GameScreens.GREEN : GameScreens.MUTED);
        renderSeats(g, x, y);

        GameScreens.card(g, x + SX, y + GY, SW, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.label(g, font, tr("battle_case"), x + SX + 6, y + GY + 6, SW - 12);
        GameScreens.fitted(g, font, definition.title(), x + SX + 20, y + GY + 20, SW - 40, GameScreens.GOLD);
        GameScreens.label(g, font, tr("battle_rounds_label"), x + SX + 6, y + GY + 40, SW - 12);
        String rounds = Component.translatable("gui.gamblingitems.battle_rounds", menu.rounds()).getString();
        g.drawCenteredString(font, rounds, x + SX + SW / 2, y + GY + 54, GameScreens.TEXT);
        GameScreens.label(g, font, tr("battle_entry_label"), x + SX + 6, y + GY + 74, SW - 12);
        GameScreens.item(g, menu.entry(), x + SX + 6, y + GY + 85, 1);
        g.renderItemDecorations(font, menu.entry(), x + SX + 6, y + GY + 85);
        GameScreens.fitted(g, font, menu.definition().priceStack().getHoverName(), x + SX + 26, y + GY + 89, SW - 32, GameScreens.MUTED);

        slip(g);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.battle_prepared", menu.preparedKeys(), menu.entry().getCount()),
                x + BattleMenu.STAKE_X, y + BattleMenu.INPUT_Y - 11, 84,
                menu.preparedKeys() >= menu.entry().getCount() ? GameScreens.GREEN : GameScreens.MUTED);
        GameScreens.label(g, font, tr("battle_entry_row"), x + BattleMenu.STAKE_X, y + BattleMenu.ENGAGED_Y - 11, 84);
        for (var slot : menu.slots)
            if (!(slot.container instanceof Inventory)) GameScreens.slot(g, x + slot.x, y + slot.y);
    }

    private void renderSeats(GuiGraphics g, int x, int y) {
        for (int seat = 0; seat < BattleRules.MAX_PLAYERS; seat++) {
            int seatX = x + GX + 6 + (seat % 2) * (SEAT_WIDTH + 8);
            int seatY = y + GY + 20 + (seat / 2) * (SEAT_HEIGHT + 6);
            boolean taken = menu.seatTaken(seat);
            boolean champion = menu.winnerSeat() == seat;
            boolean mine = menu.mySeat() == seat;
            if (champion) GameScreens.ring(g, seatX, seatY, SEAT_WIDTH, SEAT_HEIGHT, GameScreens.GOLD);
            else if (mine) GameScreens.ring(g, seatX, seatY, SEAT_WIDTH, SEAT_HEIGHT, GameScreens.ACCENT);
            GameScreens.rounded(g, seatX, seatY, SEAT_WIDTH, SEAT_HEIGHT, taken ? GameScreens.RAISED : GameScreens.HOLE);
            Component label = taken
                    ? (mine ? tr("battle_you") : Component.translatable("gui.gamblingitems.battle_seat", seat + 1))
                    : tr("battle_seat_free");
            GameScreens.fitted(g, font, label, seatX + 6, seatY + 6, SEAT_WIDTH - 12, taken ? GameScreens.TEXT : GameScreens.DIM);
            if (!taken) continue;
            GameScreens.heading(g, font, Component.literal(GameScreens.value(menu.scoreOf(seat))), seatX + 6, seatY + 19, 1,
                    champion ? GameScreens.GOLD : GameScreens.GREEN);
            String opened = menu.openedBy(seat) + "/" + menu.rounds();
            GameScreens.rightAligned(g, font, opened, seatX + SEAT_WIDTH - 6, seatY + 19, GameScreens.DIM);
        }
    }
}
