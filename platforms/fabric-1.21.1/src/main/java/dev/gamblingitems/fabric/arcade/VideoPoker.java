package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.core.machines.MachineTable;
import dev.gamblingitems.core.machines.VideoPokerRules;
import dev.gamblingitems.fabric.Compat;
import dev.gamblingitems.fabric.block.CabinetPlay;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.machines.MachineMenu;
import dev.gamblingitems.fabric.machines.MachineSettings;
import dev.gamblingitems.fabric.machines.MachineSetup;
import dev.gamblingitems.fabric.value.ItemBank;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import dev.gamblingitems.fabric.vault.VaultSection;
import java.security.SecureRandom;
import java.util.UUID;
import java.util.function.IntUnaryOperator;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;

/**
 * A video poker cabinet, played on its own buttons: the red key deals five cards for the stake in
 * hand, each card (on the screen or by its hold button) is kept or let go, and the red key again
 * draws. The hand lives on the cabinet, so it survives a restart; its stake is already taken.
 *
 * <p>The cabinet carries the hand in {@link GameStationEntity#cards} ("c1,c2,c3,c4,c5"), the cards
 * held in {@link GameStationEntity#targetSlot} (bit i for card i), and its stage in
 * {@link GameStationEntity#phase}: {@link #DEALT} while the player chooses, {@link #DRAWN} after.
 */
public final class VideoPoker {
    public static final int DEALT = 1, DRAWN = 2;
    /** A hand left alone this long is drawn as it stands by the next player who needs the cabinet. */
    public static final int ABANDONED_TICKS = 1_200;
    private static final SecureRandom RANDOM = new SecureRandom();
    static IntUnaryOperator draw = RANDOM::nextInt;

    private VideoPoker() {}

    /** True while a hand waits for its draw. */
    public static boolean open(GameStationEntity cabinet) { return cabinet.phase == DEALT && !cabinet.handOwner.isEmpty(); }

    /** The red key: deals a hand for the stake in hand, or draws the hand this player holds. */
    public static boolean press(ServerPlayer player, GameStationEntity cabinet, MachineSetup setup, ItemStack hand) {
        ServerLevel level = (ServerLevel) player.level();
        long now = level.getGameTime();
        if (now < cabinet.startedAt + cabinet.animationTicks) return CabinetPlay.refuse(player, "world_play_busy");
        if (open(cabinet)) {
            if (cabinet.handOwner.equals(player.getUUID().toString())) {
                settle(level, cabinet, setup);
                return true;
            }
            if (now < cabinet.startedAt + ABANDONED_TICKS) {
                return CabinetPlay.refuse(player, "video_poker_taken", cabinet.playerName);
            }
            // Left alone: the hand is drawn as it stands, and paid to whoever dealt it.
            settle(level, cabinet, setup);
            return true;
        }
        MachineSettings settings = setup.settings();
        long unit = setup.catalog().valueOf(hand.copyWithCount(1));
        if (unit <= 0) return CabinetPlay.refuse(player, "world_hint.video_poker");
        int count = hand.getCount();
        if (settings.maximumStake() > 0) count = (int) Math.min(count, settings.maximumStake() / unit);
        long stake = setup.catalog().valueOf(hand.copyWithCount(Math.max(1, count)));
        if (count <= 0 || !settings.enabled() || !settings.accepts(stake)) {
            return CabinetPlay.refuse(player, "world_play_refused", CabinetPlay.amount(settings.minimumStake()));
        }
        if (!setup.paysTickets()) {
            // Paid in items, the best hand must fit in the machine before the cards are dealt.
            var vault = PlayerVaults.get(player.server).forPlayer(player.getUUID(), VaultSection.VIDEO_POKER);
            if (!ItemBank.canStore(setup.catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE,
                    settings.maximumPayout(stake))) {
                return CabinetPlay.refuse(player, "world_play_refused", CabinetPlay.amount(settings.minimumStake()));
            }
        }
        ItemStack played = hand.copyWithCount(count);
        hand.shrink(count);
        int[] cards = VideoPokerRules.deal(draw);
        cabinet.cards = join(cards);
        cabinet.targetSlot = 0;
        cabinet.phase = DEALT;
        cabinet.handOwner = player.getUUID().toString();
        cabinet.handStake = stake;
        cabinet.multiplier = 0;
        cabinet.tickets = 0;
        cabinet.wheelColours = MachineMenu.encode(settings.table());
        cabinet.animationTicks = settings.playTicks();
        cabinet.show(player.getName().getString(), "", "video_poker_hold", "", false, settings.playTicks());
        Compat.message(player, Component.translatable("gui.gamblingitems.world_play_started",
                Component.literal(count + " × ").append(played.getHoverName())), true);
        return true;
    }

    /** A card is kept or let go, by the player whose hand it is, between the deal and the draw. */
    public static boolean hold(ServerPlayer player, GameStationEntity cabinet, int card) {
        if (!open(cabinet) || card < 0 || card >= VideoPokerRules.HAND) return CabinetPlay.refuse(player, "world_hint.video_poker");
        if (!cabinet.handOwner.equals(player.getUUID().toString())) return CabinetPlay.refuse(player, "video_poker_taken", cabinet.playerName);
        cabinet.targetSlot ^= 1 << card;
        cabinet.setChanged();
        cabinet.getLevel().sendBlockUpdated(cabinet.getBlockPos(), cabinet.getBlockState(), cabinet.getBlockState(), 3);
        return true;
    }

    /** Draws the open hand of a cabinet and pays it to whoever dealt it. */
    static void settle(ServerLevel level, GameStationEntity cabinet, MachineSetup setup) {
        int[] first = split(cabinet.cards);
        if (first == null) {
            cabinet.phase = 0;
            cabinet.handOwner = "";
            return;
        }
        MachineSettings settings = setup.settings();
        int[] last = VideoPokerRules.draw(first, cabinet.targetSlot, draw);
        int multiplier = VideoPokerRules.multiplier(settings.table().multipliers(), last);
        long stake = cabinet.handStake;
        long paid = multiplier == 0 ? 0 : MachineTable.payout(stake, multiplier, settings.returnRate());
        UUID owner = UUID.fromString(cabinet.handOwner);
        long now = level.getGameTime();
        long tickets = 0;
        if (setup.paysTickets()) {
            tickets = paid / setup.ticketValue();
            PlayerVaults.get(level.getServer()).arcade().record(owner, cabinet.playerName, GameMode.VIDEO_POKER.id(), stake,
                    multiplier, tickets, now + settings.playTicks());
        } else if (paid > 0) {
            SimpleContainer vault = PlayerVaults.get(level.getServer()).forPlayer(owner, VaultSection.VIDEO_POKER);
            ItemBank.store(setup.catalog(), vault, MachineSettings.FIRST_PAYOUT_SLOT, MachineSettings.VAULT_SIZE, paid);
        }
        int hand = VideoPokerRules.evaluate(last);
        cabinet.cards = join(last);
        cabinet.phase = DRAWN;
        cabinet.handOwner = "";
        cabinet.handStake = 0;
        cabinet.multiplier = multiplier;
        cabinet.tickets = tickets;
        cabinet.animationTicks = settings.playTicks();
        cabinet.show(cabinet.playerName, "", hand == VideoPokerRules.NOTHING ? "video_poker_nothing" : "poker_hand." + hand, "",
                multiplier > 0, settings.playTicks() + settings.resultTicks());
    }

    private static String join(int[] cards) {
        StringBuilder text = new StringBuilder();
        for (int card : cards) {
            if (text.length() > 0) text.append(',');
            text.append(card);
        }
        return text.toString();
    }

    /** The hand a cabinet carries, or null when it carries none it can read. */
    public static int[] split(String cards) {
        String[] parts = cards.split(",");
        if (parts.length != VideoPokerRules.HAND) return null;
        int[] hand = new int[VideoPokerRules.HAND];
        try {
            for (int index = 0; index < hand.length; index++) hand[index] = Integer.parseInt(parts[index].trim());
            VideoPokerRules.evaluate(hand);
            return hand;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
