package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.BaccaratRules;
import dev.gamblingitems.core.machines.CabinetControls;
import dev.gamblingitems.core.machines.CrapsRules;
import dev.gamblingitems.core.machines.KenoBoard;
import dev.gamblingitems.core.machines.ScratchCard;
import dev.gamblingitems.fabric.block.GameStationEntity;
import java.math.BigDecimal;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import static dev.gamblingitems.fabric.client.SlotCabinet.*;

/**
 * The table games on a cabinet, in the spaces of {@link SlotCabinet}: craps with its dice thrown in the
 * tray of the deck, baccarat dealt on the screen, the keno board and the scratch card. They share the
 * bar-top cabinet of the script: the screen from x -52.8 to 52.8 and y -182 to -66, the marquee above it,
 * the paytable on the belly.
 *
 * <p>Everything shown is rebuilt from the settled event and its seed (see {@link CrapsRules},
 * {@link BaccaratRules}, {@link KenoBoard}, {@link ScratchCard}), so every viewer sees the same play.
 */
final class TableCabinets {
    private static final float LEFT = -52.8f, RIGHT = 52.8f, TOP = -182, BOTTOM = -66;
    private static final int FELT = 0xff0f4a30, FELT_BLUE = 0xff13305e, WHITE = 0xfff7f3ea, PIP = 0xff1a1a20;
    /** Baccarat's sides as the deck lights them: player blue, banker red, tie green. */
    private static final int[] SIDES = {0xff3c6ef0, 0xffde2834, 0xff28c86e};
    private static final String RANKS = "A23456789TJQK";
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    private TableCabinets() {}

    static void render(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, int[][] table, int[] outcome,
                       boolean shown, double progress, boolean rolling, boolean won, double now, String name, String rollingKey) {
        GameMode mode = station.mode();
        surface(pose, 2f);
        switch (mode) {
            case CRAPS -> crapsScreen(pose, buffers, font, outcome, shown, progress);
            case BACCARAT -> baccarat(pose, buffers, font, outcome, shown, progress);
            case KENO -> keno(pose, buffers, font, outcome, shown, progress);
            case SCRATCH_CARD -> scratch(pose, buffers, font, table, outcome, shown, progress, now);
            default -> {}
        }
        status(pose, buffers, font, station, rolling, rollingKey, LEFT + 4, BOTTOM - 20, RIGHT - 4, BOTTOM - 3);
        pose.popPose();
        if (mode == GameMode.CRAPS) dice(pose, buffers, outcome, shown, progress);
        keys(pose, buffers, mode, outcome[2], now, rolling);
        surface(pose, 0.8f);
        sign(pose, buffers, font, name, -59, -227, 59, -197, rolling, won, now);
        pose.popPose();
        surface(pose, 1.4f);
        paytable(pose, buffers, font, mode, table);
        pose.popPose();
    }

    private static String times(int multiplier) {
        return multiplier <= 0 ? "-" : "x" + BigDecimal.valueOf(multiplier, 2).stripTrailingZeros().toPlainString();
    }

    private static String text(String key, Object... arguments) {
        return Component.translatable("gui.gamblingitems." + key, arguments).getString();
    }

    /** The betting keys light up under the bet that was played, and pulse together while the cabinet waits. */
    private static void keys(PoseStack pose, WorldCanvas buffers, GameMode mode, int choice, double now, boolean rolling) {
        boolean pulse = (long) (now / 10) % 2 == 0;
        if (mode == GameMode.KENO || mode == GameMode.SCRATCH_CARD) {
            cube(pose, buffers, 10.9f, 14.3f, -1, 14.6f, 14.36f, 1, pulse && !rolling ? 0xffff6a74 : 0xff8a1e28, 1);
            return;
        }
        // Craps: pass, don't pass at places 0 and 2. Baccarat: player, tie, banker at places 0, 1, 2.
        int[] places = mode == GameMode.CRAPS ? new int[] {0, 2} : new int[] {0, 2, 1};
        int[] colours = mode == GameMode.CRAPS ? new int[] {0xff28c86e, 0xffde2834} : SIDES;
        for (int bet = 0; bet < places.length; bet++) {
            float u = (float) (CabinetControls.BET_U + places[bet] * CabinetControls.BET_STEP);
            boolean lit = bet == choice ? !rolling || pulse : !rolling && pulse;
            cube(pose, buffers, u + .3f, 14.1f, -1, u + 3.7f, 14.16f, 1, lit ? colours[bet] : shade(colours[bet], .35f), 1);
        }
    }

    // --- Craps ----------------------------------------------------------------------------------

    /** The part of a play each roll of the round takes; the last one settles. */
    private static int rollAt(List<Integer> rolls, double progress) {
        return Math.min(rolls.size() - 1, (int) (progress * rolls.size()));
    }

    private static List<Integer> rolls(int[] outcome, boolean shown) {
        return shown ? CrapsRules.rolls(Math.max(0, Math.min(2, outcome[0])), outcome[1]) : List.of(34);
    }

    private static void crapsScreen(PoseStack pose, WorldCanvas buffers, Font font, int[] outcome, boolean shown, double progress) {
        quad(pose, buffers, LEFT, TOP, RIGHT, BOTTOM, FELT, 0);
        quad(pose, buffers, LEFT + 2, TOP + 2, RIGHT - 2, TOP + 20, 0xff0a3020, 0.05f);
        List<Integer> rolls = rolls(outcome, shown);
        int at = shown ? rollAt(rolls, progress) : 0;
        // A roll has landed once its dice stop, at 60% of its turn (see dice()).
        boolean settled = !shown || progress >= 1 || progress * rolls.size() - at > 0.6;
        int point = CrapsRules.point(rolls);
        boolean pointOn = shown && point > 0 && (at > 0 || settled);
        label(pose, buffers, font, pointOn ? text("craps_point", point) : text("craps_come_out"), 0, TOP + 11, GOLD, 0.8f, 96, 0.1f);
        // The puck: white and ON over the point, black and OFF on the come-out.
        sector(pose, buffers, RIGHT - 12, TOP + 11, 7, pointOn ? WHITE : 0xff202024);
        label(pose, buffers, font, pointOn ? "ON" : "OFF", RIGHT - 12, TOP + 11, pointOn ? PIP : WHITE, 0.45f, 12, 0.3f);
        // The rolls that have landed, newest first.
        int newest = settled ? at : at - 1;
        int lines = shown ? Math.min(6, newest + 1) : 0;
        for (int line = 0; line < lines; line++) {
            int roll = rolls.get(newest - line);
            int total = CrapsRules.total(roll);
            int colour = line == 0 ? (total == 7 ? RED : total == point ? GREEN : TEXT) : SILVER;
            label(pose, buffers, font, roll / 10 + " + " + roll % 10 + " = " + total, 0, TOP + 32 + line * 13, colour,
                    line == 0 ? 0.9f : 0.6f, 90, 0.2f);
        }
        label(pose, buffers, font, text("craps_pass"), -30, BOTTOM - 30, 0xff6cdeb7, 0.55f, 40, 0.2f);
        label(pose, buffers, font, text("craps_dont_pass"), 30, BOTTOM - 30, RED, 0.55f, 44, 0.2f);
    }

    /** The two dice in the tray of the deck: thrown at each roll, they tumble and bounce, then rest on their faces. */
    private static void dice(PoseStack pose, WorldCanvas buffers, int[] outcome, boolean shown, double progress) {
        List<Integer> rolls = rolls(outcome, shown);
        int at = shown ? rollAt(rolls, progress) : 0;
        double within = shown && progress < 1 ? progress * rolls.size() - at : 1;
        int roll = rolls.get(at);
        for (int die = 0; die < 2; die++) {
            int face = die == 0 ? roll / 10 : roll % 10;
            // Thrown from the front towards the backboard, two hops, then still.
            double t = Math.min(1, within / 0.6);
            float restU = die == 0 ? 6.6f : 8.4f, restZ = die == 0 ? 0.4f : 1.2f;
            float u = (float) (restU + (1 - t) * (die == 0 ? -0.4 : 0.6) * Math.sin(t * 9));
            float z = (float) (-1 + (restZ + 1) * t);
            float hop = (float) (Math.abs(Math.sin(t * Math.PI * 2.5)) * 1.6 * (1 - t));
            boolean tumbling = t < 1;
            // While it tumbles a die shows the faces it rolls over.
            int top = tumbling ? 1 + (int) ((within * 37 + die * 3) % 6) : face;
            die(pose, buffers, u, 13.2f + hop, z, top, tumbling);
        }
    }

    private static void die(PoseStack pose, WorldCanvas buffers, float u, float y, float z, int face, boolean tumbling) {
        float size = 1.1f;
        cube(pose, buffers, u, y, z, u + size, y + size, z + size, tumbling ? 0xffe8e2d4 : WHITE, 1);
        // The pips of the top face, on a three by three grid.
        boolean[] spots = switch (face) {
            case 1 -> new boolean[] {false, false, false, false, true, false, false, false, false};
            case 2 -> new boolean[] {true, false, false, false, false, false, false, false, true};
            case 3 -> new boolean[] {true, false, false, false, true, false, false, false, true};
            case 4 -> new boolean[] {true, false, true, false, false, false, true, false, true};
            case 5 -> new boolean[] {true, false, true, false, true, false, true, false, true};
            default -> new boolean[] {true, false, true, true, false, true, true, false, true};
        };
        float step = size / 3.4f, pip = size / 7;
        for (int index = 0; index < 9; index++) {
            if (!spots[index]) continue;
            float pu = u + size / 2 + (index % 3 - 1) * step - pip / 2, pz = z + size / 2 + (index / 3 - 1) * step - pip / 2;
            cube(pose, buffers, pu, y + size, pz, pu + pip, y + size + 0.02f, pz + pip, face == 1 ? 0xffd2232f : PIP, 1);
        }
    }

    // --- Baccarat -------------------------------------------------------------------------------

    private static void baccarat(PoseStack pose, WorldCanvas buffers, Font font, int[] outcome, boolean shown, double progress) {
        quad(pose, buffers, LEFT, TOP, RIGHT, BOTTOM, FELT_BLUE, 0);
        quad(pose, buffers, -0.6f, TOP + 4, 0.6f, BOTTOM - 26, GOLD, 0.05f);
        label(pose, buffers, font, text("baccarat_player"), -26, TOP + 9, 0xff8fb0ff, 0.7f, 48, 0.1f);
        label(pose, buffers, font, text("baccarat_banker"), 26, TOP + 9, 0xffff8f96, 0.7f, 48, 0.1f);
        if (!shown) {
            for (int side = 0; side < 2; side++) {
                float x = side == 0 ? -42 : 10;
                for (int card = 0; card < 2; card++) back(pose, buffers, x + card * 16, TOP + 20, x + card * 16 + 14, TOP + 44);
            }
            return;
        }
        int result = Math.max(0, Math.min(2, outcome[0]));
        BaccaratRules.Coup coup = BaccaratRules.deal(result, outcome[1]);
        int[][] hands = {coup.player(), coup.banker()};
        // Dealt player, banker, player, banker, then the third cards: each card takes its turn of the play.
        int[] order = new int[hands[0].length + hands[1].length];
        int[] index = new int[order.length];
        for (int turn = 0, p = 0, b = 0; turn < order.length; turn++) {
            boolean player = turn < 4 ? turn % 2 == 0 : turn == 4 && hands[0].length == 3;
            order[turn] = player ? 0 : 1;
            index[turn] = player ? p++ : b++;
        }
        int turned = progress >= 1 ? order.length : (int) (progress * (order.length + 1));
        int[] seen = new int[2];
        for (int turn = 0; turn < order.length; turn++) {
            int side = order[turn], at = index[turn];
            float x = (side == 0 ? -46 : 6) + at * 14;
            float y = TOP + 20 + (at == 2 ? 6 : 0);
            if (turn < turned) {
                int rank = hands[side][at];
                face(pose, buffers, font, rank, (outcome[1] >>> (turn * 2)) & 3, x, y, x + 13, y + 24);
                seen[side] = (seen[side] + BaccaratRules.value(rank)) % 10;
            } else {
                back(pose, buffers, x, y, x + 13, y + 24);
            }
        }
        for (int side = 0; side < 2; side++) {
            label(pose, buffers, font, String.valueOf(seen[side]), side == 0 ? -26 : 26, TOP + 60, TEXT, 1.4f, 20, 0.3f);
        }
        if (progress >= 1) {
            String banner = text(result == BaccaratRules.PLAYER ? "baccarat_player" : result == BaccaratRules.BANKER ? "baccarat_banker" : "baccarat_tie");
            quad(pose, buffers, -30, TOP + 70, 30, TOP + 84, SIDES[result], 0.2f);
            label(pose, buffers, font, banner, 0, TOP + 77, WHITE, 0.8f, 56, 0.3f);
        }
    }

    private static void back(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2) {
        quad(pose, buffers, x1, y1, x2, y2, 0xfff3eee2, 0.1f);
        quad(pose, buffers, x1 + 1, y1 + 1, x2 - 1, y2 - 1, 0xff8a1e28, 0.15f);
        quad(pose, buffers, x1 + 3, y1 + 3, x2 - 3, y2 - 3, 0xffa0303c, 0.2f);
    }

    private static void face(PoseStack pose, WorldCanvas buffers, Font font, int rank, int suit, float x1, float y1, float x2, float y2) {
        quad(pose, buffers, x1, y1, x2, y2, 0xffd8d2c4, 0.1f);
        quad(pose, buffers, x1 + .6f, y1 + .6f, x2 - .6f, y2 - .6f, 0xfffbf8f0, 0.15f);
        int colour = suit == 1 || suit == 2 ? 0xffd2232f : 0xff141418;
        String face = rank == 9 ? "10" : String.valueOf(RANKS.charAt(rank));
        label(pose, buffers, font, face, x1 + 4, y1 + 5, colour, 0.6f, 8, 0.3f);
        label(pose, buffers, font, SUITS[suit], (x1 + x2) / 2, (y1 + y2) / 2 + 3, colour, 1.4f, x2 - x1 - 2, 0.3f);
    }

    // --- Keno -----------------------------------------------------------------------------------

    private static void keno(PoseStack pose, WorldCanvas buffers, Font font, int[] outcome, boolean shown, double progress) {
        quad(pose, buffers, LEFT, TOP, RIGHT, BOTTOM, 0xff10162a, 0);
        KenoBoard.Board board = shown ? KenoBoard.layout(Math.max(0, Math.min(5, outcome[0])), outcome[1]) : null;
        int drawn = board == null ? 0 : progress >= 1 ? KenoBoard.DRAWN : (int) (progress * KenoBoard.DRAWN);
        boolean[] out = new boolean[KenoBoard.NUMBERS + 1], spot = new boolean[KenoBoard.NUMBERS + 1];
        if (board != null) {
            for (int index = 0; index < drawn; index++) out[board.drawn()[index]] = true;
            for (int number : board.spots()) spot[number] = true;
        }
        int hits = 0;
        float cell = 9.8f, x0 = -49, y0 = TOP + 3;
        for (int number = 1; number <= KenoBoard.NUMBERS; number++) {
            int column = (number - 1) % 10, row = (number - 1) / 10;
            float x = x0 + column * cell, y = y0 + row * cell;
            boolean hit = out[number] && spot[number];
            if (hit) hits++;
            int colour = hit ? 0xff28c86e : out[number] ? 0xffd9b25a : spot[number] ? 0xff2a3a6a : 0xff1c2440;
            quad(pose, buffers, x + .4f, y + .4f, x + cell - .4f, y + cell - .4f, colour, 0.1f);
            if (spot[number]) quad(pose, buffers, x + .4f, y + cell - 1.6f, x + cell - .4f, y + cell - .4f, 0xff8fb0ff, 0.15f);
            label(pose, buffers, font, String.valueOf(number), x + cell / 2, y + cell / 2, out[number] ? PIP : SILVER, 0.4f, cell - 1, 0.2f);
        }
        if (board != null) {
            label(pose, buffers, font, text("keno_hits", hits), 0, BOTTOM - 29, hits >= 3 ? GREEN : TEXT, 0.7f, 90, 0.2f);
            // The last number drawn, large, as the lounge screen calls it.
            if (drawn > 0 && progress < 1) {
                label(pose, buffers, font, String.valueOf(board.drawn()[drawn - 1]), RIGHT - 12, BOTTOM - 29, GOLD, 1, 20, 0.3f);
            }
        }
    }

    // --- Scratch card ---------------------------------------------------------------------------

    private static void scratch(PoseStack pose, WorldCanvas buffers, Font font, int[][] table, int[] outcome, boolean shown,
                                double progress, double now) {
        quad(pose, buffers, LEFT, TOP, RIGHT, BOTTOM, 0xff2a1a38, 0);
        quad(pose, buffers, -40, TOP + 4, 40, BOTTOM - 24, 0xfff3e4b0, 0.05f);
        quad(pose, buffers, -38, TOP + 6, 38, TOP + 16, 0xffc8402c, 0.1f);
        label(pose, buffers, font, text("scratch_title"), 0, TOP + 11, WHITE, 0.55f, 72, 0.2f);
        int[] multipliers = table[0];
        int prize = Math.max(0, Math.min(multipliers.length - 1, outcome[0]));
        int[] cells = shown ? ScratchCard.layout(multipliers, prize, outcome[1]) : null;
        int[] order = ScratchCard.scratchOrder(shown ? outcome[1] : 0);
        int scratched = !shown ? 0 : progress >= 1 ? ScratchCard.CELLS : (int) (progress * ScratchCard.CELLS);
        boolean[] open = new boolean[ScratchCard.CELLS];
        for (int index = 0; index < scratched; index++) open[order[index]] = true;
        boolean glow = shown && progress >= 1 && multipliers[prize] > 0 && (long) (now / 6) % 2 == 0;
        for (int index = 0; index < ScratchCard.CELLS; index++) {
            float x = -36 + (index % 3) * 24.4f, y = TOP + 19 + (index / 3) * 22.6f;
            if (!open[index]) {
                // The silver coating, and the scratch marks of the cell being scratched now.
                quad(pose, buffers, x, y, x + 23, y + 21, 0xffaab4be, 0.1f);
                quad(pose, buffers, x + 2, y + 3, x + 21, y + 5, 0xffc8d0d8, 0.15f);
                if (shown && index == order[Math.min(ScratchCard.CELLS - 1, scratched)] && progress < 1) {
                    float part = (float) ((progress * ScratchCard.CELLS) % 1);
                    quad(pose, buffers, x, y, x + 23 * part, y + 21, 0xfff8eed0, 0.2f);
                }
                continue;
            }
            boolean winning = cells[index] == prize && multipliers[prize] > 0;
            quad(pose, buffers, x, y, x + 23, y + 21, winning && glow ? GOLD : 0xfff8eed0, 0.1f);
            String face = cells[index] == ScratchCard.BLANK ? "★" : times(multipliers[cells[index]]);
            label(pose, buffers, font, face, x + 11.5f, y + 10.5f, cells[index] == ScratchCard.BLANK ? 0xff8a7a9a : 0xff5a1830,
                    0.8f, 21, 0.3f);
        }
    }

    // --- The belly: what each game pays ----------------------------------------------------------

    private static void paytable(PoseStack pose, WorldCanvas buffers, Font font, GameMode mode, int[][] table) {
        quad(pose, buffers, -48, -26, 48, 37, 0xff0b111a, 0);
        int[] multipliers = table[0];
        String[] names;
        switch (mode) {
            case CRAPS -> names = new String[] {text("craps_pass"), text("craps_dont_pass")};
            case BACCARAT -> names = new String[] {text("baccarat_player"), text("baccarat_banker"), text("baccarat_tie")};
            case KENO -> {
                names = new String[multipliers.length];
                for (int hits = 0; hits < names.length; hits++) names[hits] = text("keno_hits", hits);
            }
            default -> {
                names = new String[multipliers.length];
                for (int prize = 0; prize < names.length; prize++) names[prize] = "3 ×";
            }
        }
        int lines = Math.min(names.length, 7);
        for (int line = 0; line < lines; line++) {
            // The best line on top.
            int entry = mode == GameMode.CRAPS || mode == GameMode.BACCARAT ? line : names.length - 1 - line;
            float y = -20 + line * 8.6f;
            boolean pays = multipliers[entry] > 0;
            String what = mode == GameMode.SCRATCH_CARD ? names[entry] + " " + times(multipliers[entry]) : names[entry];
            label(pose, buffers, font, what, -16, y, pays ? TEXT : SILVER, 0.5f, 56, 0.2f);
            label(pose, buffers, font, times(multipliers[entry]), 32, y, pays ? GOLD : SILVER, 0.5f, 26, 0.2f);
        }
    }

    private static void sector(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, int colour) {
        MachineCabinet.sector(pose, buffers, x, y, 0, radius, 0, 2 * Math.PI, colour, 0.2f);
    }
}
