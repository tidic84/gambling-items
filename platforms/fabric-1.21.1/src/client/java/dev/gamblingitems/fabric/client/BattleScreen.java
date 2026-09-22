package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.battle.BattleRules;
import dev.gamblingitems.fabric.battle.BattleMenu;
import dev.gamblingitems.fabric.battle.BattleLobby;
import dev.gamblingitems.fabric.cases.CaseDefinition;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** A lobby and its battle: the same case for everyone, round by round, and one winner. */
public final class BattleScreen extends AbstractContainerScreen<BattleMenu> {
    private static final int SEAT_WIDTH = 100, SEAT_HEIGHT = 46;
    private Button join, leave, start, collect, previousCase, nextCase, fewerRounds, moreRounds;

    public BattleScreen(BattleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 260;
    }

    /** Every game explains itself, in the language of the player. */
    private final GameRules rules = new GameRules("case_battle");

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While the rules are up they take every click, so nothing is played by accident.
        if (rules.open()) {
            rules.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(rules.button(leftPos + imageWidth - 30, topPos + 6));
        join = addRenderableWidget(Button.builder(tr("battle_join"), button -> click(BattleMenu.JOIN_BUTTON))
                .bounds(leftPos + 236, topPos + 128, 108, 18).build());
        leave = addRenderableWidget(Button.builder(tr("battle_leave"), button -> click(BattleMenu.LEAVE_BUTTON))
                .bounds(leftPos + 236, topPos + 106, 52, 18).build());
        start = addRenderableWidget(Button.builder(tr("battle_start"), button -> click(BattleMenu.START_BUTTON))
                .bounds(leftPos + 292, topPos + 106, 52, 18).build());
        collect = addRenderableWidget(Button.builder(tr("collect_winnings"),
                        button -> click(BattleMenu.COLLECT_BUTTON))
                .bounds(leftPos + 236, topPos + 150, 108, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
        previousCase = addRenderableWidget(Button.builder(Component.literal("<"),
                        button -> chooseCase(-1)).bounds(leftPos + 236, topPos + 62, 18, 18).build());
        nextCase = addRenderableWidget(Button.builder(Component.literal(">"),
                        button -> chooseCase(1)).bounds(leftPos + 326, topPos + 62, 18, 18).build());
        fewerRounds = addRenderableWidget(Button.builder(Component.literal("-"),
                        button -> chooseRounds(-1)).bounds(leftPos + 236, topPos + 84, 18, 18).build());
        moreRounds = addRenderableWidget(Button.builder(Component.literal("+"),
                        button -> chooseRounds(1)).bounds(leftPos + 326, topPos + 84, 18, 18).build());
    }

    private void click(int button) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }

    private void chooseCase(int step) {
        int count = menu.setup().cases().cases().cases().size();
        if (count == 0) return;
        click(BattleMenu.CASE_BUTTON + Math.floorMod(menu.caseIndex() + step, count));
    }

    private void chooseRounds(int step) {
        int rounds = Math.min(BattleRules.MAX_ROUNDS, Math.max(BattleRules.MIN_ROUNDS, menu.rounds() + step));
        click(BattleMenu.ROUNDS_BUTTON + rounds);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }

    @Override protected void containerTick() {
        super.containerTick();
        boolean lobby = menu.phase() == BattleLobby.Phase.LOBBY;
        boolean empty = menu.players() == 0;
        join.active = menu.canJoin();
        leave.active = lobby && menu.seated();
        start.active = menu.canStart();
        collect.active = true;
        previousCase.active = lobby && empty;
        nextCase.active = lobby && empty;
        fewerRounds.active = lobby && empty && menu.rounds() > BattleRules.MIN_ROUNDS;
        moreRounds.active = lobby && empty && menu.rounds() < BattleRules.MAX_ROUNDS;
        join.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.battle_entry",
                menu.entry().getCount(), menu.definition().priceStack().getHoverName())));
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rules.open()) {
            rules.render(graphics, font, width, height);
            return;
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, GameScreens.BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, GameScreens.INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GameScreens.GOLD);
        g.drawString(font, title, x + 12, y + 11, GameScreens.TEXT, false);
        CaseDefinition definition = menu.definition();
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.battle_subtitle",
                        definition.title(), menu.rounds()), x + 12, y + 22, imageWidth - 24, GameScreens.MUTED);
        g.fill(x + 8, y + 32, x + 228, y + 170, GameScreens.PANEL);
        g.fill(x + 232, y + 32, x + 352, y + 170, GameScreens.PANEL);
        renderSeats(g, x, y);
        renderLobby(g, x, y, definition);
        g.drawString(font, tr("battle_entry_row"), x + BattleMenu.STAKE_X,
                y + BattleMenu.ENGAGED_Y - 9, GameScreens.MUTED, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.battle_prepared",
                        menu.preparedKeys(), menu.entry().getCount()),
                x + BattleMenu.STAKE_X, y + BattleMenu.INPUT_Y - 9, 96, GameScreens.MUTED);
        GameScreens.slots(g, menu, x, y);
        g.drawString(font, tr("inventory"), x + 12, y + 183, GameScreens.MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 239, GameScreens.GREEN, false);
    }

    private void renderSeats(GuiGraphics g, int x, int y) {
        for (int seat = 0; seat < BattleRules.MAX_PLAYERS; seat++) {
            int seatX = x + 12 + (seat % 2) * (SEAT_WIDTH + 8);
            int seatY = y + 40 + (seat / 2) * (SEAT_HEIGHT + 8);
            boolean taken = menu.seatTaken(seat);
            boolean champion = menu.winnerSeat() == seat;
            g.fill(seatX, seatY, seatX + SEAT_WIDTH, seatY + SEAT_HEIGHT,
                    taken ? 0xff1d2a3c : 0xff141a24);
            g.fill(seatX, seatY, seatX + SEAT_WIDTH, seatY + 2,
                    champion ? GameScreens.GOLD : taken ? GameScreens.GREEN : GameScreens.BORDER);
            Component label = taken
                    ? Component.translatable("gui.gamblingitems.battle_seat", seat + 1)
                    : tr("battle_seat_free");
            GameScreens.fitted(g, font, label, seatX + 6, seatY + 8, SEAT_WIDTH - 12,
                    taken ? GameScreens.TEXT : GameScreens.MUTED);
            if (!taken) continue;
            GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.battle_score",
                            GameScreens.value(menu.scoreOf(seat))), seatX + 6, seatY + 20, SEAT_WIDTH - 12,
                    champion ? GameScreens.GOLD : GameScreens.GREEN);
            GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.battle_opened",
                            menu.openedBy(seat), menu.rounds()), seatX + 6, seatY + 32, SEAT_WIDTH - 12,
                    GameScreens.MUTED);
            if (menu.mySeat() == seat) {
                g.fill(seatX + SEAT_WIDTH - 6, seatY + 6, seatX + SEAT_WIDTH - 2, seatY + 10, GameScreens.GOLD);
            }
        }
    }

    private void renderLobby(GuiGraphics g, int x, int y, CaseDefinition definition) {
        g.drawString(font, tr("battle_lobby"), x + 236, y + 38, GameScreens.MUTED, false);
        Component phase = switch (menu.phase()) {
            case LOBBY -> menu.players() == 0 ? tr("battle_waiting")
                    : Component.translatable("gui.gamblingitems.battle_countdown",
                            (menu.phaseTicks() + 19) / 20, menu.players());
            case RUNNING -> Component.translatable("gui.gamblingitems.battle_round",
                    menu.round(), menu.rounds());
            case DONE -> menu.winnerSeat() >= 0
                    ? Component.translatable("gui.gamblingitems.battle_winner",
                            menu.winnerSeat() + 1, GameScreens.value(menu.prize()))
                    : tr("battle_waiting");
        };
        GameScreens.fitted(g, font, phase, x + 236, y + 48, 108,
                menu.phase() == BattleLobby.Phase.DONE ? GameScreens.GOLD : GameScreens.TEXT);
        String name = font.plainSubstrByWidth(definition.title().getString(), 64);
        g.drawString(font, name, x + 258 + (64 - font.width(name)) / 2, y + 68, GameScreens.TEXT, false);
        String rounds = Component.translatable("gui.gamblingitems.battle_rounds", menu.rounds()).getString();
        g.drawString(font, rounds, x + 258 + (64 - font.width(rounds)) / 2, y + 90, GameScreens.TEXT, false);
        g.renderFakeItem(definition.priceStack(), x + 236, y + 168 - 168);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
