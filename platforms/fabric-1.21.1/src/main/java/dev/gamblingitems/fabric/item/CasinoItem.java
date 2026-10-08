package dev.gamblingitems.fabric.item;

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

/** The pocket casino: the casino games, as tabs. It joins a nearby table, or opens one where its owner stands. */
public final class CasinoItem extends Item {
    public CasinoItem(Properties properties) { super(properties); }

    //#if MC >= 1.21.2
    //$ @Override public net.minecraft.world.InteractionResult use(Level level, Player player, InteractionHand hand) {
    //#else
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    //#endif
        if (player instanceof ServerPlayer serverPlayer) GameMenus.openCasino(serverPlayer);
        //#if MC >= 1.21.2
        //$ return dev.gamblingitems.fabric.Compat.handled(level);
        //#else
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
        //#endif
    }
}
