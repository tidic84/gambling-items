package dev.gamblingitems.fabric;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The few game calls whose shape changed between Minecraft versions, so the rest of the code
 * reads the same everywhere. tools/port.py selects the branch of each target version.
 */
public final class Compat {
    private Compat() {}

    /** The item registered under this id, or air. */
    public static Item item(ResourceLocation id) {
        //#if MC >= 1.21.2
        //$ return BuiltInRegistries.ITEM.getValue(id);
        //#else
        return BuiltInRegistries.ITEM.get(id);
        //#endif
    }

    /** A game resolves on the server and then animates for a while; the terminal cools down meanwhile. */
    public static boolean coolingDown(Player player) {
        //#if MC >= 1.21.2
        //$ return player.getCooldowns().isOnCooldown(new ItemStack(ModContent.TERMINAL));
        //#else
        return player.getCooldowns().isOnCooldown(ModContent.TERMINAL);
        //#endif
    }

    public static void coolDown(Player player, int ticks) {
        //#if MC >= 1.21.2
        //$ player.getCooldowns().addCooldown(new ItemStack(ModContent.TERMINAL), ticks);
        //#else
        player.getCooldowns().addCooldown(ModContent.TERMINAL, ticks);
        //#endif
    }

    public static float cooldown(Player player) {
        //#if MC >= 1.21.2
        //$ return player.getCooldowns().getCooldownPercent(new ItemStack(ModContent.TERMINAL), 0);
        //#else
        return player.getCooldowns().getCooldownPercent(ModContent.TERMINAL, 0);
        //#endif
    }

    /** The answer to a click that was handled, the same on both sides. */
    public static InteractionResult handled(Level level) {
        //#if MC >= 1.21.2
        //$ return level.isClientSide ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
        //#else
        return InteractionResult.sidedSuccess(level.isClientSide);
        //#endif
    }

    /** A line for one player, above the hotbar or in the chat. */
    public static void message(Player player, net.minecraft.network.chat.Component text, boolean overlay) {
        //#if MC >= 26.1
        //$ if (overlay) player.sendOverlayMessage(text); else player.sendSystemMessage(text);
        //#else
        player.displayClientMessage(text, overlay);
        //#endif
    }
}
