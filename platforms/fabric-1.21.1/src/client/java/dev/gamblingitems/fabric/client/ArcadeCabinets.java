package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.CabinetControls;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.core.machines.PlinkoFlight;
import dev.gamblingitems.core.machines.VideoPokerRules;
import dev.gamblingitems.fabric.block.CabinetPlay;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;

import static dev.gamblingitems.fabric.client.MachineCabinet.sector;
import static dev.gamblingitems.fabric.client.SlotCabinet.*;

/**
 * The arcade machines, in the model and canvas spaces of {@link SlotCabinet}: giant Plinko, the Big Six
 * wheel, video poker and the horse race, plus the gold outline of the control under the player's cursor.
 *
 * <p>The giant machines are two blocks wide: u runs to 32 and the canvas of a face centred on u = 8
 * simply reaches further right, to x = 192.
 */
final class ArcadeCabinets {
    /** The silks of the horses and the colours of the Big Six buttons, as the block models light them. */
    static final int[] SILKS = {0xffde2834, 0xff3c6ef0, 0xff28c86e, 0xfff6aa28, 0xffb45af0, 0xfff0ecdc};
    private static final int[] COATS = {0xff7a4a22, 0xff2a2220, 0xffd8d0c0, 0xffa0562a, 0xff5a3a24, 0xff8a7a6a};
    private static final String RANKS = "23456789TJQKA";
    private static final String[] SUITS = {"♠", "♥", "♦", "♣"};

    private ArcadeCabinets() {}

    static void render(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, int[][] table, int[] outcome,
                       boolean shown, double progress, boolean rolling, boolean won, double now, float light) {
        GameMode mode = station.mode();
        String name = Component.translatable("gui.gamblingitems.mode." + mode.id()).getString();
        String rollingKey = "machine_playing." + mode.id();
        switch (mode) {
            case PLINKO -> plinko(station, pose, buffers, font, table, outcome, shown, progress, rolling, won, now, name, rollingKey);
            case BIG_SIX -> bigSix(station, pose, buffers, font, table, outcome, shown, progress, rolling, won, now, name, rollingKey);
            case VIDEO_POKER -> videoPoker(station, pose, buffers, font, table, rolling, won, now, name, light);
            case HORSE_RACE -> horseRace(station, pose, buffers, font, table, outcome, shown, progress, rolling, won, now, name, light);
            default -> {}
        }
    }

    private static String times(int multiplier) {
        return multiplier <= 0 ? "-" : "x" + BigDecimal.valueOf(multiplier, 2).stripTrailingZeros().toPlainString();
    }

    // --- Plinko ---------------------------------------------------------------------------------

    static final float PLINKO_X = 64, PLINKO_TOP = -200, PLINKO_ROW = 10.8f, PLINKO_SPACING = 16;
    /** How far the ball rests in its pocket below the last row of pegs, in canvas pixels; its radius. */
    static final float PLINKO_REST = 17, PLINKO_BALL = 4.8f;

    private static void plinko(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, int[][] table,
                               int[] outcome, boolean shown, double progress, boolean rolling, boolean won, double now,
                               String name, String rollingKey) {
        int rows = MachineTable.PLINKO_ROWS;
        int[] multipliers = table[0];
        float pockets = PLINKO_TOP + rows * PLINKO_ROW + 6;
        int landed = shown && progress >= 1 ? outcome[0] : -1;
        PlinkoFlight.Point fall = shown && progress > 0
                ? PlinkoFlight.at(outcome[1], rows, PLINKO_REST / PLINKO_ROW, progress) : null;
        // Where the ball is on the canvas: on its fall, or waiting in the cup on top of the crown.
        float ballX = fall == null ? PLINKO_X : PLINKO_X + (float) fall.column() * PLINKO_SPACING / 2;
        float ballY = fall == null ? -259 - (float) Math.abs(Math.sin(now * 0.05)) * 2
                : PLINKO_TOP + (float) (fall.row() + 1) * PLINKO_ROW - 5;
        surface(pose, 3.9f);
        // The board: deep teal, lighter towards the top.
        for (int band = 0; band < 10; band++) {
            float y1 = -211 + band * 16.2f;
            quad(pose, buffers, -51, y1, 179, y1 + 16.2f, shade(0xff0f5a66, 1.05f - band * 0.06f), 0);
        }
        // The ball's shadow on the board, cast by the light above the glass.
        if (fall != null) sector(pose, buffers, ballX + 2, ballY + 3, 0, PLINKO_BALL * 0.9f, 0, 2 * Math.PI, 0xff08323a, 0.05f);
        for (int pocket = 0; pocket < MachineTable.PLINKO_POCKETS; pocket++) {
            float px = PLINKO_X + (pocket - rows / 2f) * PLINKO_SPACING;
            int multiplier = pocket < multipliers.length ? multipliers[pocket] : 0;
            int colour = pocket == landed ? TEXT : MachineMotion.tier(multiplier);
            quad(pose, buffers, px - 7.4f, pockets, px + 7.4f, pockets + 15, colour, 0.1f);
            quad(pose, buffers, px - 7.4f, pockets, px + 7.4f, pockets + 2.5f, shade(colour, 1.35f) | 0xff000000, 0.12f);
            label(pose, buffers, font, times(multiplier), px, pockets + 9, pocket == landed ? DARK : TEXT, 0.5f, 13.5f, 0.2f);
        }
        pose.popPose();
        // The pegs stand out of the board, real little posts the ball knocks against; the one just struck rings gold.
        for (int row = 0; row < rows; row++) {
            float py = PLINKO_TOP + (row + 1) * PLINKO_ROW;
            for (int pin = 0; pin <= row + 2; pin++) {
                float px = PLINKO_X + (pin - (row + 2) / 2f) * PLINKO_SPACING;
                float u = 8 + px / 8, y = 8 - py / 8;
                boolean struck = fall != null && fall.hitRow() == row && fall.sinceHit() < 0.8
                        && Math.abs(px - PLINKO_X - (float) fall.hitColumn() * PLINKO_SPACING / 2) < 1;
                cube(pose, buffers, u - 0.18f, y - 0.18f, 2.9f, u + 0.18f, y + 0.18f, 3.9f, struck ? GOLD : CHROME, 1);
            }
        }
        surface(pose, 2.6f);
        ball(pose, buffers, ballX, ballY, PLINKO_BALL, fall == null ? 0 : fall.spin());
        pose.popPose();
        // The marquee stands in front of everything else on the crown, so its name is never hidden.
        surface(pose, 0.4f);
        sign(pose, buffers, font, name, -50, -247, 178, -220, rolling, won, now);
        pose.popPose();
        surface(pose, 1.9f);
        status(pose, buffers, font, station, rolling, rollingKey, 4, -26.4f, 124, -8.8f);
        MachineCabinet.odds(pose, buffers, font, GameMode.PLINKO, table, 0, 4, 0.8f, 124, 36);
        pose.popPose();
    }

    /** A ball seen from the front: a disc shaded like a sphere lit from the top left, with a mark that turns as it rolls. */
    private static void ball(PoseStack pose, WorldCanvas buffers, float x, float y, float radius, double spin) {
        float[] sizes = {1, .84f, .66f, .46f, .26f};
        float[] tones = {.48f, .7f, .86f, .97f, 1.08f};
        for (int layer = 0; layer < sizes.length; layer++) {
            float r = radius * sizes[layer], off = (radius - r) * 0.5f;
            sector(pose, buffers, x - off, y - off, 0, r, 0, 2 * Math.PI, shade(0xffdde4ec, tones[layer]), 0.5f + layer * 0.05f);
        }
        float markX = x + (float) Math.cos(spin) * radius * 0.55f, markY = y + (float) Math.sin(spin) * radius * 0.55f;
        sector(pose, buffers, markX, markY, 0, radius * 0.2f, 0, 2 * Math.PI, 0xff7d8a9c, 0.8f);
        sector(pose, buffers, x - radius * 0.4f, y - radius * 0.42f, 0, radius * 0.16f, 0, 2 * Math.PI, 0xffffffff, 0.9f);
    }

    // --- Big Six --------------------------------------------------------------------------------

    private static final float SIX_X = 64, SIX_Y = -136, SIX_RADIUS = 80;

    /** The slots of the wheel in order round it: each symbol's slots spread as evenly as they go. */
    static int[] slots(int[] weights) {
        record Slot(double at, int symbol, int occurrence) {}
        List<Slot> slots = new ArrayList<>();
        for (int symbol = 0; symbol < weights.length; symbol++) {
            for (int occurrence = 0; occurrence < weights[symbol]; occurrence++) {
                slots.add(new Slot((occurrence + 0.5) / weights[symbol] + symbol * 1e-6, symbol, occurrence));
            }
        }
        slots.sort((a, b) -> Double.compare(a.at(), b.at()));
        int[] order = new int[slots.size() * 2];
        for (int index = 0; index < slots.size(); index++) {
            order[index * 2] = slots.get(index).symbol();
            order[index * 2 + 1] = slots.get(index).occurrence();
        }
        return order;
    }

    private static void bigSix(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, int[][] table,
                               int[] outcome, boolean shown, double progress, boolean rolling, boolean won, double now,
                               String name, String rollingKey) {
        int[] multipliers = table[0], weights = table[1];
        int[] order = slots(weights);
        int count = Math.max(1, order.length / 2);
        // Where the drawn slot is, so that the wheel can stop with it under the flapper at the top.
        int target = 0;
        if (shown) {
            for (int index = 0; index < count; index++) {
                if (order[index * 2] == outcome[0] && order[index * 2 + 1] == Math.floorMod(outcome[1], Math.max(1, weights[Math.max(0, Math.min(weights.length - 1, outcome[0]))]))) {
                    target = index;
                    break;
                }
            }
        }
        double settled = -(target + 0.5) / count;
        double turn = shown ? settled - 3 * (1 - GameScreens.eased(progress)) : 0;
        boolean landed = shown && progress >= 1;
        surface(pose, 2f);
        sector(pose, buffers, SIX_X, SIX_Y, 0, SIX_RADIUS + 2, 0, 2 * Math.PI, DARK, 0);
        double nearestPeg = Math.PI;
        for (int index = 0; index < count; index++) {
            double from = (index / (double) count + turn) * 2 * Math.PI - Math.PI / 2, width = 2 * Math.PI / count;
            int symbol = order[index * 2];
            int colour = MachineMotion.tier(symbol < multipliers.length ? multipliers[symbol] : 0) | 0xff000000;
            boolean winner = landed && index == target;
            sector(pose, buffers, SIX_X, SIX_Y, 18, SIX_RADIUS, from, from + width, winner ? shade(colour, 1.4f) : colour, 0.1f);
            sector(pose, buffers, SIX_X, SIX_Y, SIX_RADIUS - 6, SIX_RADIUS, from, from + width, shade(colour, 1.25f), 0.15f);
            sector(pose, buffers, SIX_X, SIX_Y, 18, SIX_RADIUS, from, from + 0.012, GOLD, 0.2f);
            float pegX = SIX_X + (float) Math.cos(from) * (SIX_RADIUS - 2), pegY = SIX_Y + (float) Math.sin(from) * (SIX_RADIUS - 2);
            quad(pose, buffers, pegX - 1.1f, pegY - 1.1f, pegX + 1.1f, pegY + 1.1f, CHROME, 0.3f);
            nearestPeg = Math.min(nearestPeg, Math.abs(Math.IEEEremainder(from + Math.PI / 2, 2 * Math.PI)));
            pose.pushPose();
            pose.translate(SIX_X, SIX_Y, 0);
            pose.mulPose(Axis.ZP.rotation((float) (from + width / 2)));
            label(pose, buffers, font, times(symbol < multipliers.length ? multipliers[symbol] : 0), SIX_RADIUS * 0.66f, 0,
                    winner ? DARK : TEXT, 0.6f, SIX_RADIUS * 0.5f, 0.3f);
            pose.popPose();
        }
        sector(pose, buffers, SIX_X, SIX_Y, 0, 18, 0, 2 * Math.PI, BRASS, 0.3f);
        sector(pose, buffers, SIX_X, SIX_Y, 0, 12, 0, 2 * Math.PI, 0xff4a2470, 0.35f);
        label(pose, buffers, font, "6", SIX_X, SIX_Y, GOLD, 2.2f, 20, 0.4f);
        float kick = shown && progress < 1 ? (float) Math.max(0, 1 - nearestPeg / 0.06) * 0.45f : 0;
        pose.pushPose();
        pose.translate(SIX_X, SIX_Y - SIX_RADIUS - 5, 0);
        pose.mulPose(Axis.ZP.rotation(kick));
        quad(pose, buffers, -2.4f, -4, 2.4f, 5, 0xff6a3a1a, 0.6f);
        quad(pose, buffers, -1.2f, 5, 1.2f, 9, 0xff6a3a1a, 0.6f);
        pose.popPose();
        pose.popPose();
        surface(pose, 4.8f);
        sign(pose, buffers, font, name, -27, -253, 155, -235, rolling, won, now);
        pose.popPose();
        // Over each betting button, what its symbol pays; under them, the status.
        surface(pose, 1.9f);
        quad(pose, buffers, -38, -4, 166, 38, 0xff0b111a, 0);
        for (int symbol = 0; symbol < 7 && symbol < multipliers.length; symbol++) {
            float x = (float) ((CabinetControls.SYMBOL_U + symbol * CabinetControls.SYMBOL_STEP + CabinetControls.SYMBOL_WIDTH / 2 - 8) * 8);
            boolean chosen = landed && outcome[0] == symbol;
            quad(pose, buffers, x - 11, -2, x + 11, 14, chosen ? GOLD : MachineMotion.tier(multipliers[symbol]) | 0xff000000, 0.1f);
            label(pose, buffers, font, times(multipliers[symbol]), x, 6, chosen ? DARK : TEXT, 0.55f, 20, 0.2f);
        }
        status(pose, buffers, font, station, rolling, rollingKey, -36, 18, 164, 36);
        pose.popPose();
    }

    // --- Video poker ----------------------------------------------------------------------------

    private static void videoPoker(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, int[][] table,
                                   boolean rolling, boolean won, double now, String name, float light) {
        int[] cards = dev.gamblingitems.fabric.arcade.VideoPoker.split(station.cards);
        int phase = station.phase;
        int held = Math.max(0, station.targetSlot);
        double age = station.getLevel().getGameTime() - station.startedAt;
        surface(pose, 2f);
        quad(pose, buffers, -52.8f, -198, 52.8f, -65.6f, 0xff06223a, 0);
        quad(pose, buffers, -52.8f, -198, 52.8f, -170, 0xff0a3054, 0.05f);
        label(pose, buffers, font, Component.translatable("gui.gamblingitems.video_poker_title").getString(), 0, -189, GOLD, 0.8f, 100, 0.1f);
        String owner = station.playerName.isEmpty() ? Component.translatable("gui.gamblingitems.station_ready").getString() : station.playerName;
        label(pose, buffers, font, owner, 0, -177, SILVER, 0.55f, 100, 0.1f);
        for (int card = 0; card < VideoPokerRules.HAND; card++) {
            float x1 = (float) ((CabinetControls.CARD_U + card * CabinetControls.CARD_STEP - 8) * 8);
            float x2 = x1 + (float) (CabinetControls.CARD_WIDTH * 8);
            boolean keep = (held >> card & 1) == 1;
            // Dealt one after the other; at the draw only the cards let go turn over again.
            boolean turning = (phase == dev.gamblingitems.fabric.arcade.VideoPoker.DEALT || !keep) && age < 4 + card * 3;
            if (cards == null || turning) {
                cardBack(pose, buffers, x1, -156, x2, -92);
            } else {
                card(pose, buffers, font, cards[card], x1, -156, x2, -92, won && phase == dev.gamblingitems.fabric.arcade.VideoPoker.DRAWN);
            }
            if (keep && cards != null) {
                quad(pose, buffers, x1, -166, x2, -158, GOLD, 0.2f);
                label(pose, buffers, font, Component.translatable("gui.gamblingitems.video_poker_held").getString(),
                        (x1 + x2) / 2, -162, DARK, 0.5f, x2 - x1 - 2, 0.3f);
            }
        }
        status(pose, buffers, font, station, rolling, "machine_playing.video_poker", -50, -88, 50, -68);
        pose.popPose();
        // The hold keys light up under the cards kept; the deal key glows while it can be pressed.
        for (int card = 0; card < VideoPokerRules.HAND; card++) {
            float u = (float) (CabinetControls.HOLD_U + card * CabinetControls.HOLD_STEP);
            boolean keep = (held >> card & 1) == 1 && phase == dev.gamblingitems.fabric.arcade.VideoPoker.DEALT;
            cube(pose, buffers, u + 0.2f, 13.8f, -1, u + 1.7f, 13.86f, 1, keep ? 0xffffe27a : 0xff4a3a18, 1);
        }
        boolean pulse = (long) (now / 10) % 2 == 0;
        cube(pose, buffers, 12.3f, 14.2f, -1, 14.9f, 14.26f, 1, pulse ? 0xffff6a74 : 0xff8a1e28, 1);
        surface(pose, 0.8f);
        sign(pose, buffers, font, name, -59, -227, 59, -197, rolling, won, now);
        pose.popPose();
        // The paytable on the belly, every hand with what it pays.
        surface(pose, 1.4f);
        quad(pose, buffers, -50, -30, 50, 41, 0xff0b111a, 0);
        int[] paytable = table[0];
        for (int hand = 0; hand < Math.min(9, paytable.length); hand++) {
            float y = -25 + (8 - hand) * 7.6f;
            label(pose, buffers, font, Component.translatable("gui.gamblingitems.poker_hand." + hand).getString(),
                    -20, y, hand >= 7 ? GOLD : TEXT, 0.45f, 56, 0.2f);
            label(pose, buffers, font, times(paytable[hand]), 33, y, SILVER, 0.45f, 30, 0.2f);
        }
        pose.popPose();
    }

    private static void cardBack(PoseStack pose, WorldCanvas buffers, float x1, float y1, float x2, float y2) {
        quad(pose, buffers, x1, y1, x2, y2, 0xfff3eee2, 0.1f);
        quad(pose, buffers, x1 + 1.5f, y1 + 1.5f, x2 - 1.5f, y2 - 1.5f, 0xffa0202c, 0.15f);
        for (int stripe = 0; stripe < 6; stripe++) {
            float y = y1 + 4 + stripe * (y2 - y1 - 8) / 6;
            quad(pose, buffers, x1 + 3, y, x2 - 3, y + 2, 0xffc8404c, 0.2f);
        }
    }

    private static void card(PoseStack pose, WorldCanvas buffers, Font font, int card, float x1, float y1, float x2, float y2,
                             boolean glow) {
        quad(pose, buffers, x1, y1, x2, y2, glow ? GOLD : 0xffd8d2c4, 0.1f);
        quad(pose, buffers, x1 + 0.8f, y1 + 0.8f, x2 - 0.8f, y2 - 0.8f, 0xfffbf8f0, 0.15f);
        int suit = VideoPokerRules.suit(card), rank = VideoPokerRules.rank(card);
        int colour = suit == 1 || suit == 2 ? 0xffd2232f : 0xff141418;
        String face = rank == 8 ? "10" : String.valueOf(RANKS.charAt(rank));
        label(pose, buffers, font, face, x1 + 5, y1 + 7, colour, 0.9f, 10, 0.3f);
        label(pose, buffers, font, SUITS[suit], (x1 + x2) / 2, (y1 + y2) / 2 + 4, colour, 2.4f, x2 - x1 - 3, 0.3f);
        label(pose, buffers, font, face, x2 - 5, y2 - 7, colour, 0.9f, 10, 0.3f);
    }

    // --- Horse race -----------------------------------------------------------------------------

    private static final float START_U = 4.4f, FINISH_U = 28.6f;

    private static void horseRace(GameStationEntity station, PoseStack pose, WorldCanvas buffers, Font font, int[][] table,
                                  int[] outcome, boolean shown, double progress, boolean rolling, boolean won, double now,
                                  String name, float light) {
        int[] multipliers = table[0];
        int horses = Math.min(6, Math.max(2, multipliers.length));
        long time = station.getLevel().getGameTime();
        boolean betting = station.phase == 0 && station.phaseEnd > time;
        boolean racing = shown && station.phase == 1;
        for (int horse = 0; horse < horses; horse++) {
            double along = racing ? stride(horse, outcome[0], outcome[1], progress) : 0;
            float u = START_U + (float) along * (FINISH_U - START_U);
            float y = 13.6f + horse * 1.6f, z = 4.4f + horse * 1.8f;
            boolean running = racing && progress < 1;
            horse(pose, buffers, u, y, z, horse, running ? now * 0.9 + horse : 0, light);
        }
        // The tote board: the countdown and the bets while they are open, then the race and its winner.
        surface(pose, 14.3f);
        quad(pose, buffers, -28, -207, 156, -135, 0xff06090f, 0);
        label(pose, buffers, font, name, 64, -199, GOLD, 0.8f, 170, 0.1f);
        if (betting) {
            long seconds = Math.max(0, (station.phaseEnd - time + 19) / 20);
            label(pose, buffers, font, Component.translatable("gui.gamblingitems.horse_race_open", seconds).getString(),
                    64, -186, GREEN, 0.75f, 170, 0.1f);
            String[] bets = station.publicBets.isEmpty() ? new String[0] : station.publicBets.split("\n");
            for (int line = 0; line < Math.min(4, bets.length); line++) {
                String[] bet = bets[bets.length - 1 - line].split(":");
                if (bet.length < 3) continue;
                int horse = parse(bet[1]);
                label(pose, buffers, font, bet[0] + "  →  #" + (horse + 1), 64, -173 + line * 9,
                        horse >= 0 && horse < SILKS.length ? SILKS[horse] : TEXT, 0.55f, 170, 0.1f);
            }
        } else if (racing && progress < 1) {
            int leader = 0;
            double best = -1;
            for (int horse = 0; horse < horses; horse++) {
                double along = stride(horse, outcome[0], outcome[1], progress);
                if (along > best) {
                    best = along;
                    leader = horse;
                }
            }
            label(pose, buffers, font, Component.translatable("gui.gamblingitems.horse_race_leader", leader + 1).getString(),
                    64, -180, SILKS[leader], 1.1f, 170, 0.1f);
        } else if (racing) {
            label(pose, buffers, font, Component.translatable("gui.gamblingitems.horse_race_winner_is", outcome[0] + 1,
                    times(outcome[0] < multipliers.length ? multipliers[outcome[0]] : 0)).getString(), 64, -180,
                    outcome[0] >= 0 && outcome[0] < SILKS.length ? SILKS[outcome[0]] : GOLD, 1f, 170, 0.1f);
            if (station.tickets > 0) {
                label(pose, buffers, font, Component.translatable("gui.gamblingitems.tickets_plus", station.tickets).getString(),
                        64, -164, GOLD, 0.7f, 170, 0.1f);
            }
        } else {
            label(pose, buffers, font, Component.translatable("gui.gamblingitems.horse_race_idle").getString(),
                    64, -176, SILVER, 0.7f, 170, 0.1f);
        }
        pose.popPose();
        surface(pose, 7.8f);
        sign(pose, buffers, font, name, -43, -253, 171, -227, rolling, won, now);
        pose.popPose();
        // Over each betting button, its horse and what it pays.
        surface(pose, 1.9f);
        for (int horse = 0; horse < horses; horse++) {
            float x = (float) ((CabinetControls.LANE_U + horse * CabinetControls.LANE_STEP + CabinetControls.LANE_WIDTH / 2 - 8) * 8);
            boolean winner = racing && progress >= 1 && outcome[0] == horse;
            quad(pose, buffers, x - 13, -18, x + 13, 0, winner ? GOLD : SILKS[horse], 0.1f);
            label(pose, buffers, font, "#" + (horse + 1), x, -13, DARK, 0.55f, 24, 0.2f);
            label(pose, buffers, font, times(multipliers[horse]), x, -5, DARK, 0.5f, 24, 0.2f);
        }
        status(pose, buffers, font, station, rolling, "machine_playing.horse_race", -28, 18, 156, 36);
        pose.popPose();
    }

    private static int parse(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    /**
     * How far along the track a horse is, from 0 to 1. The winner crosses the line first; the others
     * cross after it, and each surges and fades on the way, so the lead changes before the end.
     */
    static double stride(int horse, int winner, int seed, double progress) {
        double finish = horse == winner ? 0.86 : 0.9 + 0.09 * hash(horse * 31 + seed);
        double along = Math.min(1, progress / finish);
        double surge = 0.06 * Math.sin(progress * Math.PI * (2 + hash(horse + seed * 7) * 2) + horse * 1.7) * Math.sin(progress * Math.PI);
        return Math.max(0, Math.min(1, along + (along < 1 ? surge : 0)));
    }

    private static double hash(int value) {
        int x = value * 0x9E3779B1;
        x ^= x >>> 15;
        x *= 0x85EBCA6B;
        x ^= x >>> 13;
        return (x & 0xffff) / 65535.0;
    }

    /** A horse and its jockey, nose at u, standing on y, galloping by its phase. */
    private static void horse(PoseStack pose, WorldCanvas buffers, float u, float y, float z, int index, double phase, float light) {
        int coat = COATS[index % COATS.length], silk = SILKS[index % SILKS.length];
        float bob = phase == 0 ? 0 : (float) Math.abs(Math.sin(phase)) * 0.25f;
        float stride = phase == 0 ? 0 : (float) Math.sin(phase) * 0.35f;
        cube(pose, buffers, u - 2.3f, y + 1 + bob, z - 0.4f, u - 0.6f, y + 1.8f + bob, z + 0.4f, coat, light);
        cube(pose, buffers, u - 0.9f, y + 1.5f + bob, z - 0.25f, u - 0.3f, y + 2.5f + bob, z + 0.25f, coat, light);
        cube(pose, buffers, u - 0.5f, y + 2.1f + bob, z - 0.25f, u + 0.3f, y + 2.6f + bob, z + 0.25f, coat, light);
        cube(pose, buffers, u - 0.8f, y + 2.3f + bob, z - 0.08f, u - 0.4f, y + 2.8f + bob, z + 0.08f, 0xff1a1410, light);
        cube(pose, buffers, u - 2.8f, y + 1.1f + bob, z - 0.12f, u - 2.3f, y + 1.7f + bob, z + 0.12f, 0xff1a1410, light);
        for (int leg = 0; leg < 4; leg++) {
            float lu = (leg < 2 ? u - 1 : u - 2.1f) + (leg % 2 == 0 ? stride : -stride);
            float lz = leg % 2 == 0 ? z - 0.25f : z + 0.15f;
            cube(pose, buffers, lu, y, lz, lu + 0.22f, y + 1 + bob, lz + 0.12f, shade(coat, 0.8f), light);
        }
        cube(pose, buffers, u - 1.7f, y + 1.8f + bob, z - 0.3f, u - 1.05f, y + 2.6f + bob, z + 0.3f, silk, light);
        cube(pose, buffers, u - 1.6f, y + 2.6f + bob, z - 0.2f, u - 1.15f, y + 3f + bob, z + 0.2f, shade(silk, 1.3f) | 0xff000000, light);
    }

    // --- The control under the cursor -----------------------------------------------------------

    /** Outlines in gold the control the player is pointing at, so a click lands where they expect. */
    static void hover(GameStationEntity station, PoseStack pose, WorldCanvas buffers, double now) {
        var hit = Minecraft.getInstance().hitResult;
        if (!(hit instanceof BlockHitResult block) || station.getLevel() == null) return;
        var state = station.getLevel().getBlockState(block.getBlockPos());
        if (!(state.getBlock() instanceof SlotMachineBlock)) return;
        if (!SlotMachineBlock.anchor(block.getBlockPos(), state).equals(station.getBlockPos())) return;
        GameMode mode = station.mode();
        CabinetControls.Control control = CabinetPlay.control(mode, station.getBlockState(), station.getBlockPos(), block.getLocation());
        if (control == CabinetControls.Control.NONE) return;
        int alpha = 0x90 + (int) (0x50 * Math.sin(now * 0.3));
        int colour = alpha << 24 | (GOLD & 0xffffff);
        for (CabinetControls.Box box : CabinetControls.boxes(mode)) {
            if (box.control() != control) continue;
            outline(pose, buffers, (float) box.u1(), (float) box.y1(), (float) box.z1(), (float) box.u2(), (float) box.y2(),
                    (float) box.z2(), colour);
        }
    }

    private static void outline(PoseStack pose, WorldCanvas buffers, float u1, float y1, float z1, float u2, float y2, float z2,
                                int colour) {
        float t = 0.12f;
        float[] us = {u1, u2}, ys = {y1, y2}, zs = {z1, z2};
        for (float y : ys) for (float z : zs) cube(pose, buffers, u1, y - t, z - t, u2, y + t, z + t, colour, 1);
        for (float u : us) for (float z : zs) cube(pose, buffers, u - t, y1, z - t, u + t, y2, z + t, colour, 1);
        for (float u : us) for (float y : ys) cube(pose, buffers, u - t, y - t, z1, u + t, y + t, z2, colour, 1);
    }
}
