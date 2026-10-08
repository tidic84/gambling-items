package dev.gamblingitems.fabric.item;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
//#if MC < 1.21.2
import net.minecraft.world.InteractionResultHolder;
//#endif
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The exchange terminal: the item games, as tabs. The pocket casino holds the casino games. */
public final class TerminalItem extends Item {
    public TerminalItem(Properties properties) { super(properties); }

    /** Anywhere in the inventory, offhand included, each portable item gives access to its own games only. */
    public static boolean hasAccess(Player player, GameMode mode) {
        var item = GameMenus.TERMINAL.contains(mode) ? ModContent.TERMINAL : ModContent.POCKET_CASINO;
        return player.getInventory().contains(new ItemStack(item));
    }

    //#if MC >= 1.21.2
    //$ @Override public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
    //#else
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    //#endif
        if (player instanceof ServerPlayer serverPlayer) GameMenus.openTerminal(serverPlayer);
        //#if MC >= 1.21.2
        //$ return dev.gamblingitems.fabric.Compat.handled(level);
        //#else
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        //#endif
    }
}
