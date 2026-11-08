package dev.gamblingitems.fabric.arcade;

import dev.gamblingitems.fabric.vault.ArcadeLedger;
import dev.gamblingitems.fabric.vault.PlayerVaults;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Where a cabinet hands its tickets over: the ledger kept with the players' vaults. */
public final class Arcade {
    private Arcade() {}

    public static ArcadeLedger ledger(ServerPlayer player) { return PlayerVaults.get(player.server).arcade(); }

    /**
     * Settles a play in tickets: what it paid, in tickets of {@code ticketValue}, counted once the
     * machine reveals it at {@code revealAt}. A client copy of a menu records nothing.
     */
    public static long pay(Player player, String mode, long stake, int multiplier, long paid, long ticketValue, long revealAt) {
        long tickets = paid <= 0 ? 0 : paid / ticketValue;
        if (player instanceof ServerPlayer server) {
            ledger(server).record(server.getUUID(), server.getName().getString(), mode, stake, multiplier, tickets, revealAt);
        }
        return tickets;
    }

    /** Empties the credit slots of a vault: the arcade keeps what was played. */
    public static void keepStake(Container vault, int from, int to) {
        for (int slot = from; slot < to; slot++) vault.setItem(slot, ItemStack.EMPTY);
    }
}
