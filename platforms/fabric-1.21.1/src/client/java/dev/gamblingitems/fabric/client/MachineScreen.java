package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.fabric.machines.MachineMenu;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/**
 * The window of a prize machine: the machine itself on the left, its exact odds on the right and
 * the credit prepared underneath. The machine only replays the outcome the server already settled.
 */
public final class MachineScreen extends CasinoScreen<MachineMenu> {
    private static final int SHOW_X = 12, SHOW_Y = 34, SHOW_WIDTH = 176, SHOW_HEIGHT = 101;
    private static final int ODDS_X = 196, ODDS_WIDTH = 112, ROW = 11, ODDS_TOP = 66;
    private static final int GLASS = 0x3399c8e0, STEEL = 0xff8795a1, BRASS = GameScreens.GOLD;
    private final CasinoSounds.Reel ticker = new CasinoSounds.Reel();
    private Button play, collect, previous, next;
    private boolean wasPlaying;
    /** The game time a big win showed, or -1. */
    private long celebrateFrom = -1;

    public MachineScreen(MachineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 320, 278);
    }

    @Override protected GameMode mode() { return menu.mode(); }
    @Override protected boolean portable() { return false; }
    @Override protected Component subtitle() {
        return Component.translatable("gui.gamblingitems.machine_subtitle", GameScreens.value(menu.settings().minimumStake()));
    }

    @Override protected void init() {
        super.init();
        play = addRenderableWidget(CasinoButton.primary(tr("machine_play." + mode().id()), button -> send(MachineMenu.PLAY_BUTTON))
                .bounds(leftPos + ODDS_X, topPos + 154, ODDS_WIDTH, 18).build());
        play.setTooltip(Tooltip.create(menu.settings().maximumStake() == 0
                ? Component.translatable("gui.gamblingitems.machine_play_help", GameScreens.value(menu.settings().minimumStake()))
                : Component.translatable("gui.gamblingitems.machine_play_help_max", GameScreens.value(menu.settings().minimumStake()),
                        GameScreens.value(menu.settings().maximumStake()))));
        collect = addRenderableWidget(CasinoButton.builder(tr("collect_winnings"), button -> send(MachineMenu.COLLECT_BUTTON))
                .bounds(leftPos + ODDS_X, topPos + 176, ODDS_WIDTH, 18).build());
        collect.setTooltip(Tooltip.create(tr("collect_help")));
        if (menu.table().choices() > 1) {
            previous = addRenderableWidget(CasinoButton.builder(Component.literal("<"), button -> choose(menu.choice() - 1))
                    .bounds(leftPos + 122, topPos + 145, 18, 18).build());
            previous.setTooltip(Tooltip.create(tr("machine_previous." + mode().id())));
            next = addRenderableWidget(CasinoButton.builder(Component.literal(">"), button -> choose(menu.choice() + 1))
                    .bounds(leftPos + 170, topPos + 145, 18, 18).build());
            next.setTooltip(Tooltip.create(tr("machine_next." + mode().id())));
        }
    }

    private void choose(int choice) {
        if (choice >= 0 && choice < menu.table().choices()) send(MachineMenu.CHOICE_BUTTON + choice);
    }

    @Override protected void containerTick() {
        super.containerTick();
        play.active = menu.canPlay();
        collect.active = menu.winnings() > 0;
        if (previous != null) {
            previous.active = !menu.playing() && menu.choice() > 0;
            next.active = !menu.playing() && menu.choice() < menu.table().choices() - 1;
        }
        boolean playing = menu.playing();
        if (wasPlaying && !playing && menu.state() == MachineMenu.STATE_RESULT) {
            if (menu.lastWin() >= menu.stake() && menu.multiplier() > 0) CasinoSounds.win();
            else CasinoSounds.lose();
            if (menu.lastWin() >= menu.stake() && menu.multiplier() >= MachineMotion.BIG_WIN && minecraft.level != null)
                celebrateFrom = minecraft.level.getGameTime();
        }
        if (!wasPlaying && playing) {
            ticker.reset();
            CasinoSounds.start();
        }
        wasPlaying = playing;
    }

    /** Clicking a claw position or a target in the odds picks it, as the arrows do. */
    @Override protected boolean gameClicked(double mouseX, double mouseY, int button) {
        if (menu.table().choices() <= 1 || menu.playing()) return false;
        for (int choice = 0; choice < menu.table().choices(); choice++) {
            if (inside(mouseX, mouseY, ODDS_X + 4, ODDS_TOP + choice * ROW - 1, ODDS_WIDTH - 8, ROW)) {
                if (choice != menu.choice()) send(MachineMenu.CHOICE_BUTTON + choice);
                return true;
            }
        }
        return false;
    }

    private double progress(float partialTick) {
        if (menu.state() == MachineMenu.STATE_IDLE) return 0;
        if (!menu.playing()) return 1;
        float left = menu.remainingTicks() - partialTick;
        return MachineMotion.progress(1 - Math.max(0, left) / Math.max(1f, menu.settings().playTicks()));
    }

    @Override protected void renderGame(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GameScreens.cabinet(g, x + SHOW_X, y + SHOW_Y, SHOW_WIDTH, SHOW_HEIGHT, lacquer());
        GameScreens.card(g, x + ODDS_X, y + SHOW_Y, ODDS_WIDTH, 116);
        GameScreens.cardHeader(g, x + ODDS_X, y + SHOW_Y, ODDS_WIDTH);
        double progress = progress(partialTick);
        double now = (minecraft.level == null ? 0 : minecraft.level.getGameTime()) + partialTick;
        boolean shown = menu.shown() >= 0;
        ArenaShapes.flush(g);
        g.enableScissor(x + SHOW_X + 1, y + SHOW_Y + 1, x + SHOW_X + SHOW_WIDTH - 1, y + SHOW_Y + 74);
        float cx = x + SHOW_X + SHOW_WIDTH / 2f, cy = y + SHOW_Y + 37;
        switch (mode()) {
            case LUCKY_WHEEL -> wheel(g, cx, cy, progress, shown, now);
            case COIN_PUSHER -> pusher(g, x + SHOW_X, y + SHOW_Y, progress, shown, now);
            case PRIZE_CLAW -> claw(g, x + SHOW_X, y + SHOW_Y, progress, shown);
            case PACHINKO -> pachinko(g, cx, y + SHOW_Y, progress, shown);
            case CAPSULE_GACHA -> gacha(g, cx, cy, progress, shown, now);
            default -> dial(g, x + SHOW_X, y + SHOW_Y, progress, shown, now);
        }
        ArenaShapes.flush(g);
        g.disableScissor();
        status(g, x, y);
        odds(g, x, y, mouseX, mouseY);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.chips_ready", GameScreens.value(menu.stake())),
                x + MachineMenu.STAKE_X, y + MachineMenu.INPUT_Y - 9, 96, GameScreens.MUTED);
        if (menu.table().choices() > 1) {
            String index = (menu.choice() + 1) + "/" + menu.table().choices();
            g.drawCenteredString(font, index, x + 155, y + 150, GameScreens.TEXT);
        }
        GameScreens.slots(g, menu, x, y);
        GameScreens.fitted(g, font, tr("inventory"), x + 12, y + 203, 62, GameScreens.MUTED);
    }

    @Override protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float tick) {
        if (celebrateFrom < 0 || minecraft.level == null) return;
        float age = minecraft.level.getGameTime() - celebrateFrom + tick;
        GameScreens.celebrate(g, font, leftPos + SHOW_X + 4, topPos + SHOW_Y + 6, SHOW_WIDTH - 8, SHOW_HEIGHT - 10, age,
                menu.multiplier());
    }

    /** The lacquer of the cabinet in the world, so the window looks like the machine it was opened from. */
    private int lacquer() {
        return switch (mode()) {
            case LUCKY_WHEEL -> 0xff1f3566;
            case COIN_PUSHER -> 0xff17603e;
            case PRIZE_CLAW -> 0xffb8407a;
            case PACHINKO -> 0xff8f8a80;
            case CAPSULE_GACHA -> 0xffc06a3a;
            default -> 0xff2a2a33;
        };
    }

    /** What the machine is doing, then what it paid, written out rather than told by a colour. */
    private void status(GuiGraphics g, int x, int y) {
        Component status;
        int colour;
        if (menu.playing()) {
            status = tr("machine_playing." + mode().id());
            colour = GameScreens.TEXT;
        } else if (menu.state() == MachineMenu.STATE_IDLE) {
            status = choiceLine();
            colour = GameScreens.MUTED;
        } else if (menu.multiplier() == 0) {
            status = tr("machine_lost");
            colour = GameScreens.RED;
        } else {
            boolean won = menu.lastWin() >= menu.stake();
            status = Component.translatable(won ? "gui.gamblingitems.machine_won" : "gui.gamblingitems.machine_back",
                    GameScreens.multiplier(menu.multiplier()), GameScreens.value(menu.lastWin()));
            colour = won ? GameScreens.GOLD : GameScreens.MUTED;
        }
        GameScreens.fitted(g, font, status, x + SHOW_X + 6, y + SHOW_Y + 78, SHOW_WIDTH - 12, colour);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.winnings_amount", GameScreens.value(menu.winnings())),
                x + SHOW_X + 6, y + SHOW_Y + 89, SHOW_WIDTH - 12, menu.winnings() > 0 ? GameScreens.GOLD : GameScreens.DIM);
    }

    /** The claw position or the target being played, with what it pays and how often. */
    private Component choiceLine() {
        if (menu.table().choices() <= 1) return tr("machine_ready");
        int prize = menu.table().odds(menu.choice()).get(1).multiplier();
        return Component.translatable("gui.gamblingitems.machine_choice." + mode().id(), menu.choice() + 1,
                GameScreens.multiplier(prize), percent(menu.table().chance(menu.choice(), 1)));
    }

    /** The odds of the machine, every line read off the same table the server draws from. */
    private void odds(GuiGraphics g, int x, int y, int mouseX, int mouseY) {
        int left = x + ODDS_X;
        GameScreens.fitted(g, font, tr("machine_odds"), left + 8, y + SHOW_Y + 4, ODDS_WIDTH - 16, GameScreens.GOLD);
        BigDecimal back = menu.table().returnToPlayer(menu.choice()).multiply(menu.settings().returnRate());
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.machine_return", percent(back)),
                left + 8, y + SHOW_Y + 18, ODDS_WIDTH - 16, GameScreens.DIM);
        List<int[]> rows = rows();
        for (int index = 0; index < rows.size() && index < 8; index++) {
            int[] row = rows.get(index);
            int top = y + ODDS_TOP + index * ROW;
            boolean selected = menu.table().choices() > 1 && index == menu.choice();
            boolean hovered = menu.table().choices() > 1 && !menu.playing()
                    && inside(mouseX, mouseY, ODDS_X + 4, ODDS_TOP + index * ROW - 1, ODDS_WIDTH - 8, ROW);
            if (selected || hovered) GameScreens.rounded(g, left + 4, top - 2, ODDS_WIDTH - 8, ROW, selected ? GameScreens.SELECTION : GameScreens.RAISED);
            g.fill(left + 8, top, left + 14, top + 7, MachineMotion.tier(row[0]));
            g.drawString(font, GameScreens.multiplier(row[0]), left + 18, top, GameScreens.TEXT, false);
            String chance = percent(BigDecimal.valueOf(row[1]).divide(BigDecimal.valueOf(row[2]), 8, RoundingMode.HALF_UP));
            GameScreens.rightAligned(g, font, chance, left + ODDS_WIDTH - 8, top, GameScreens.MUTED);
        }
    }

    /**
     * One line per distinct prize, as {multiplier, weight, total}. A claw and a terminal list one
     * line per choice instead, each with the chance of winning it.
     */
    private List<int[]> rows() {
        MachineTable table = menu.table();
        List<int[]> rows = new ArrayList<>();
        if (table.choices() > 1) {
            for (int choice = 0; choice < table.choices(); choice++) {
                var odds = table.odds(choice);
                rows.add(new int[] {odds.get(1).multiplier(), odds.get(1).weight(), odds.get(0).weight() + odds.get(1).weight()});
            }
            return rows;
        }
        int total = 0;
        for (var prize : table.odds(0)) total += prize.weight();
        for (var prize : table.odds(0)) {
            int[] same = null;
            for (int[] row : rows) if (row[0] == prize.multiplier()) same = row;
            if (same == null) rows.add(new int[] {prize.multiplier(), prize.weight(), total});
            else same[1] += prize.weight();
        }
        rows.sort((a, b) -> Integer.compare(b[0], a[0]));
        return rows;
    }

    private static String percent(BigDecimal share) {
        BigDecimal value = share.movePointRight(2);
        int scale = value.compareTo(BigDecimal.ONE) < 0 ? 3 : 2;
        return value.setScale(scale, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "%";
    }

    /** A vertical wheel on its stand: sectors as wide as their weight, a brass pointer at the top. */
    private void wheel(GuiGraphics g, float cx, float cy, double progress, boolean shown, double now) {
        int[] weights = menu.table().weights(), multipliers = menu.table().multipliers();
        double turn = shown ? MachineMotion.wheelTurn(weights, menu.shown(), menu.detail() % 1000, progress) : 0;
        float radius = 31;
        g.fill((int) cx - 3, (int) cy, (int) cx + 3, (int) cy + 40, STEEL);
        GameScreens.rounded(g, (int) cx - 26, (int) cy + 31, 52, 6, 0xff2a313d);
        ArenaShapes.disc(g, cx, cy, radius + 4, BRASS);
        ArenaShapes.disc(g, cx, cy, radius + 2, 0xff0a1019);
        for (int sector = 0; sector < weights.length; sector++) {
            if (weights[sector] == 0) continue;
            double from = (MachineMotion.sectorStart(weights, sector) + turn) * 2 * Math.PI - Math.PI / 2;
            double to = from + MachineMotion.sectorWidth(weights, sector) * 2 * Math.PI;
            ArenaShapes.sector(g, cx, cy, 7, radius, from, to, MachineMotion.tier(multipliers[sector]));
            ArenaShapes.line(g, cx + (float) Math.cos(from) * 7, cy + (float) Math.sin(from) * 7,
                    cx + (float) Math.cos(from) * radius, cy + (float) Math.sin(from) * radius, 0.6f, 0xff0a1019);
        }
        // Lamps around the rim chase while it turns.
        for (int lamp = 0; lamp < 16; lamp++) {
            double angle = lamp * Math.PI / 8;
            boolean lit = menu.playing() ? ((long) (now / 2) + lamp) % 4 == 0 : lamp % 2 == 0;
            ArenaShapes.disc(g, cx + (float) Math.cos(angle) * (radius + 3), cy + (float) Math.sin(angle) * (radius + 3), 1.2f,
                    lit ? GameScreens.TEXT : 0xff6b5631);
        }
        ArenaShapes.disc(g, cx, cy, 6, BRASS);
        ArenaShapes.disc(g, cx, cy, 3, 0xff0a1019);
        ArenaShapes.line(g, cx - 4, cy - radius - 7, cx, cy - radius + 3, 1.5f, GameScreens.TEXT);
        ArenaShapes.line(g, cx + 4, cy - radius - 7, cx, cy - radius + 3, 1.5f, GameScreens.TEXT);
        if (shown && progress >= 1) {
            int sector = Math.max(0, Math.min(multipliers.length - 1, menu.shown()));
            g.drawCenteredString(font, GameScreens.multiplier(multipliers[sector]), (int) cx + 58, (int) cy - 4, GameScreens.GOLD);
        } else if (menu.playing()) {
            // A click as each sector passes the pointer.
            ticker.follow(-turn * weights.length, progress);
        }
    }

    /** A glass case: the pusher plate sweeping a shelf of coins, and the coins it tips into the tray. */
    private void pusher(GuiGraphics g, int x, int y, double progress, boolean shown, double now) {
        int left = x + 30, right = x + 146, shelf = y + 40, tray = y + 66;
        GameScreens.rounded(g, left - 4, y + 4, right - left + 8, 68, 0xff1b2430);
        g.fill(left, y + 8, right, y + 70, 0xff0d141d);
        double plate = MachineMotion.plate(now);
        int plateX = left + (int) (plate * 26);
        g.fill(left, shelf - 14, plateX + 20, shelf, STEEL);
        g.fill(plateX + 18, shelf - 14, plateX + 20, shelf, GameScreens.TEXT);
        g.fill(left, shelf, right, shelf + 3, 0xff6b5631);
        // The shelf of coins: they creep forward with each sweep.
        for (int coin = 0; coin < 9; coin++) {
            float coinX = plateX + 24 + coin * 9 + (float) (plate * 2);
            if (coinX < right - 6) ArenaShapes.rounded(g, coinX, shelf - 4, 8, 4, 2, coin % 3 == 0 ? 0xffb8923f : BRASS);
        }
        int falling = shown ? MachineMotion.coins(menu.multiplier()) : 0;
        for (int coin = 0; coin < falling; coin++) {
            double fall = MachineMotion.coinFall(coin, progress);
            float coinX = right - 8 - coin * 3, coinY = shelf - 4 + (float) (fall * (tray - shelf));
            if (fall > 0) ArenaShapes.rounded(g, coinX + (float) fall * 6, coinY, 8, 4, 2, BRASS);
        }
        g.fill(right - 2, shelf, right, tray, 0xff2a313d);
        g.fill(right - 30, tray, right + 4, tray + 3, 0xff6b5631);
        g.fill(left, y + 8, right, y + 70, GLASS);
        String count = shown && progress >= 1 ? Component.translatable("gui.gamblingitems.machine_coins", falling).getString() : "";
        g.drawCenteredString(font, count, x + 88, y + 2 + 66, GameScreens.GOLD);
    }

    /** The claw over its row of prizes: the rail, the cable, the claw and the chute at the left. */
    private void claw(GuiGraphics g, int x, int y, double progress, boolean shown) {
        int[] multipliers = menu.table().multipliers();
        int count = multipliers.length;
        int left = x + 36, right = x + 164, floor = y + 66, rail = y + 10;
        g.fill(x + 8, y + 6, x + 168, y + 72, 0xff0d141d);
        g.fill(x + 8, rail - 2, x + 168, rail, STEEL);
        g.fill(x + 10, floor - 18, x + 30, floor + 4, 0xff2a313d);
        g.drawCenteredString(font, "▼", x + 20, floor - 14, GameScreens.DIM);
        int playing = shown ? Math.max(0, Math.min(count - 1, menu.shown())) : menu.choice();
        float target = left + (playing + 0.5f) * (right - left) / count;
        MachineMotion.Claw claw = MachineMotion.claw(x + 20, target, shown && menu.detail() == 1, shown ? progress : 0);
        float clawX = (float) claw.rail();
        for (int prize = 0; prize < count; prize++) {
            float px = left + (prize + 0.5f) * (right - left) / count;
            boolean taken = shown && prize == playing && (claw.carrying() || (menu.detail() == 1 && progress >= 1));
            if (taken) continue;
            float size = 4 + Math.min(5, multipliers[prize] / 400f);
            ArenaShapes.disc(g, px, floor - size, size, MachineMotion.tier(multipliers[prize]));
            if (prize == menu.choice() && !menu.playing()) g.fill((int) px - 4, floor + 2, (int) px + 4, floor + 3, BRASS);
        }
        float bottom = rail + 6 + (float) (claw.depth() * (floor - rail - 18));
        g.fill((int) clawX - 6, rail - 3, (int) clawX + 6, rail + 2, BRASS);
        g.fill((int) clawX, rail + 2, (int) clawX + 1, (int) bottom, STEEL);
        float open = (float) (1 - claw.closed()) * 4;
        ArenaShapes.line(g, clawX, bottom, clawX - 3 - open, bottom + 7, 1.2f, GameScreens.TEXT);
        ArenaShapes.line(g, clawX + 1, bottom, clawX + 4 + open, bottom + 7, 1.2f, GameScreens.TEXT);
        if (claw.carrying()) {
            float size = 4 + Math.min(5, multipliers[playing] / 400f);
            ArenaShapes.disc(g, clawX + 0.5f, bottom + 6 + size, size, MachineMotion.tier(multipliers[playing]));
        } else if (shown && claw.prizeFall() > 0 && claw.prizeFall() < 1 && menu.detail() == 0) {
            float px = left + (playing + 0.5f) * (right - left) / count;
            float size = 4 + Math.min(5, multipliers[playing] / 400f);
            ArenaShapes.disc(g, px, (float) (bottom + 6 + (floor - size - bottom - 6) * claw.prizeFall()), size,
                    MachineMotion.tier(multipliers[playing]));
        }
        g.fill(x + 8, y + 6, x + 168, y + 72, GLASS);
    }

    /** Eight rows of pins over nine pockets, and the ball finding its way down. */
    private void pachinko(GuiGraphics g, float cx, int y, double progress, boolean shown) {
        int[] multipliers = menu.table().multipliers();
        float spacing = 11f, top = y + 6, rowHeight = 5.6f;
        for (int row = 0; row < MachineTable.PACHINKO_ROWS; row++) {
            for (int pin = 0; pin <= row; pin++) {
                float px = cx + (pin - row / 2f) * spacing;
                ArenaShapes.disc(g, px, top + row * rowHeight + rowHeight, 1.1f, STEEL);
            }
        }
        float pockets = top + MachineTable.PACHINKO_ROWS * rowHeight + 5;
        int landed = shown && progress >= 1 ? menu.shown() : -1;
        for (int pocket = 0; pocket < MachineTable.PACHINKO_POCKETS; pocket++) {
            float px = cx + (pocket - MachineTable.PACHINKO_ROWS / 2f) * spacing;
            int colour = MachineMotion.tier(multipliers[pocket]);
            GameScreens.rounded(g, (int) (px - 4), (int) pockets, 8, 8, pocket == landed ? GameScreens.TEXT : colour);
        }
        for (int pocket = 0; pocket < MachineTable.PACHINKO_POCKETS; pocket++) {
            float px = cx + (pocket - MachineTable.PACHINKO_ROWS / 2f) * spacing;
            String label = BigDecimal.valueOf(multipliers[pocket], 2).stripTrailingZeros().toPlainString();
            GuiPose.push(g);
            GuiPose.translate(g, px, pockets + 10);
            GuiPose.scale(g, 0.5f);
            g.drawCenteredString(font, label, 0, 0, pocket == landed ? GameScreens.GOLD : GameScreens.MUTED);
            GuiPose.pop(g);
        }
        if (shown) {
            double[] ball = MachineMotion.ball(menu.detail(), progress);
            float bx = cx + (float) ball[0] * spacing / 2, by = top + (float) ball[1] * rowHeight + rowHeight - 3;
            if (progress >= 1) by = pockets + 3;
            ArenaShapes.disc(g, bx, by, 2.2f, GameScreens.TEXT);
            if (menu.playing()) ticker.follow(ball[1], progress);
        } else {
            ArenaShapes.disc(g, cx, top, 2.2f, GameScreens.TEXT);
        }
    }

    /** A glass globe of capsules on a body with a crank, and the capsule that comes out of the chute. */
    private void gacha(GuiGraphics g, float cx, float cy, double progress, boolean shown, double now) {
        int[] multipliers = menu.table().multipliers();
        float globeY = cy - 8;
        GameScreens.rounded(g, (int) cx - 22, (int) cy + 8, 44, 28, GameScreens.RED);
        g.fill((int) cx - 20, (int) cy + 10, (int) cx + 20, (int) cy + 12, BRASS);
        ArenaShapes.disc(g, cx, globeY, 22, 0xff0a1019);
        double shake = menu.playing() && progress < 0.45 ? Math.sin(now * 2.2) * 1.5 : 0;
        for (int capsule = 0; capsule < 14; capsule++) {
            double angle = capsule * 2.399, distance = 4 + (capsule * 37 % 13);
            float px = cx + (float) (Math.cos(angle) * distance + shake), py = globeY + 6 + (float) (Math.sin(angle) * distance * 0.7);
            ArenaShapes.disc(g, px, py, 3.2f, MachineMotion.tier(multipliers[capsule % multipliers.length]));
            g.fill((int) px - 3, (int) py, (int) px + 3, (int) py + 1, 0x55ffffff);
        }
        ArenaShapes.disc(g, cx - 7, globeY - 9, 4, 0x22ffffff);
        // The crank, on the right of the body.
        double crank = shown ? MachineMotion.crank(progress) : 0;
        double angle = crank * 2 * Math.PI;
        float hubX = cx + 12, hubY = cy + 20;
        ArenaShapes.disc(g, hubX, hubY, 4, STEEL);
        ArenaShapes.line(g, hubX, hubY, hubX + (float) Math.cos(angle) * 7, hubY + (float) Math.sin(angle) * 7, 1.6f, BRASS);
        ArenaShapes.disc(g, hubX + (float) Math.cos(angle) * 7, hubY + (float) Math.sin(angle) * 7, 2, GameScreens.TEXT);
        g.fill((int) cx - 14, (int) cy + 24, (int) cx - 2, (int) cy + 34, 0xff0a1019);
        if (shown) {
            int tier = MachineMotion.tier(menu.multiplier());
            double drop = MachineMotion.capsuleDrop(progress), open = MachineMotion.capsuleOpen(progress);
            float capsuleX = cx - 8 + (float) (drop * 50), capsuleY = cy + 29;
            if (drop > 0) {
                float lift = (float) (open * 5);
                ArenaShapes.sector(g, capsuleX, capsuleY - lift, 0, 5, Math.PI, 2 * Math.PI, tier);
                ArenaShapes.sector(g, capsuleX, capsuleY + lift, 0, 5, 0, Math.PI, 0xfff3eee2);
                if (open > 0.6) g.drawCenteredString(font, GameScreens.multiplier(menu.multiplier()), (int) capsuleX, (int) capsuleY - 4,
                        GameScreens.GOLD);
            }
        }
    }

    /** The terminal: a dial whose green arc is exactly the chance of the target, and its readouts. */
    private void dial(GuiGraphics g, int x, int y, double progress, boolean shown, double now) {
        int target = menu.table().multipliers()[shown ? Math.max(0, Math.min(menu.table().choices() - 1, menu.shown())) : menu.choice()];
        float cx = x + 46, cy = y + 38, radius = 28;
        double win = 100.0 / target;
        ArenaShapes.disc(g, cx, cy, radius + 3, BRASS);
        ArenaShapes.sector(g, cx, cy, radius - 9, radius, -Math.PI / 2, -Math.PI / 2 + 2 * Math.PI, 0xff3a1820);
        ArenaShapes.sector(g, cx, cy, radius - 9, radius, -Math.PI / 2, -Math.PI / 2 + win * 2 * Math.PI, GameScreens.GREEN);
        ArenaShapes.disc(g, cx, cy, radius - 10, 0xff0a1019);
        double needle = shown ? MachineMotion.needle(target, menu.detail(), progress) : 0;
        double angle = needle * 2 * Math.PI - Math.PI / 2;
        ArenaShapes.line(g, cx, cy, cx + (float) Math.cos(angle) * (radius + 1), cy + (float) Math.sin(angle) * (radius + 1), 1.4f,
                GameScreens.TEXT);
        ArenaShapes.disc(g, cx, cy, 2.5f, BRASS);
        if (menu.playing()) ticker.follow(needle * 20, progress);
        // The readouts: target, chance, pay, and a risk bar that grows with the target.
        BigDecimal chance = BigDecimal.valueOf(100).divide(BigDecimal.valueOf(target), 8, RoundingMode.HALF_UP);
        long example = dev.gamblingitems.core.machines.MachineTable.payout(1000, target, menu.settings().returnRate());
        int left = x + 84;
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.machine_target", GameScreens.multiplier(target)),
                left, y + 10, 88, GameScreens.TEXT);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.machine_chance", percent(chance)),
                left, y + 22, 88, GameScreens.GREEN_HOVER);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.machine_pays",
                BigDecimal.valueOf(example, 3).stripTrailingZeros().toPlainString() + "x"), left, y + 34, 88, BRASS);
        GameScreens.fitted(g, font, tr("machine_risk"), left, y + 48, 40, GameScreens.DIM);
        double risk = 1 - win;
        GameScreens.bar(g, left + 30, y + 49, 56, 5, risk, risk > 0.9 ? GameScreens.RED : risk > 0.6 ? BRASS : GameScreens.GREEN);
        // A strip of data ticking along the bottom while the terminal computes.
        for (int bar = 0; bar < 14; bar++) {
            double level = menu.playing() ? 0.5 + 0.5 * Math.sin(now * 0.9 + bar * 1.7) : 0.25;
            int height = (int) Math.round(2 + level * 8);
            g.fill(left + bar * 6, y + 70 - height, left + bar * 6 + 4, y + 70, menu.playing() ? GameScreens.ACCENT : 0xff2a313d);
        }
        if (shown && progress >= 1) {
            boolean won = menu.multiplier() > 0;
            g.drawCenteredString(font, tr(won ? "machine_reached" : "machine_missed"), (int) cx, (int) cy - 4,
                    won ? GameScreens.GOLD : GameScreens.RED);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
