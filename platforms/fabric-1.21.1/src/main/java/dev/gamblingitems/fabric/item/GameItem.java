package dev.gamblingitems.fabric.item;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.menu.GameMenus;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** A dedicated handheld game. Its vault is shared with the corresponding station. */
public final class GameItem extends Item {
    private final GameMode mode;

    public GameItem(GameMode mode, Properties properties) {
        super(properties);
        this.mode = mode;
    }

    public GameMode mode() { return mode; }

    public static boolean hasAccess(Player player, GameMode mode) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModContent.TERMINAL)
                    || stack.getItem() instanceof GameItem game && game.mode == mode) return true;
        }
        return false;
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) GameMenus.open(serverPlayer, mode, ContainerLevelAccess.NULL);
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
