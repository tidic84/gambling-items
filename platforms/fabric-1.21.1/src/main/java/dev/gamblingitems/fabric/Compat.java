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

    /** The data this mod keeps on one item, under its own key; empty when there is none. */
    public static net.minecraft.nbt.CompoundTag itemData(ItemStack stack, String key) {
        //#if MC >= 1.20.5
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        //#else
        //$ net.minecraft.nbt.CompoundTag root = stack.getTag() == null ? new net.minecraft.nbt.CompoundTag() : stack.getTag();
        //#endif
        return Nbt.child(root, key);
    }

    public static void setItemData(ItemStack stack, String key, net.minecraft.nbt.CompoundTag tag) {
        //#if MC >= 1.20.5
        var data = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        net.minecraft.nbt.CompoundTag root = data == null ? new net.minecraft.nbt.CompoundTag() : data.copyTag();
        root.put(key, tag);
        net.minecraft.world.item.component.CustomData.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, root);
        //#else
        //$ stack.getOrCreateTag().put(key, tag);
        //#endif
    }

    /** Names an item, as an anvil would. */
    public static void name(ItemStack stack, net.minecraft.network.chat.Component name) {
        //#if MC >= 1.20.5
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, name);
        //#else
        //$ stack.setHoverName(name);
        //#endif
    }

    /** True when a player gave this item a name of their own, at an anvil. */
    public static boolean named(ItemStack stack) {
        //#if MC >= 1.20.5
        return stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME);
        //#else
        //$ return stack.hasCustomHoverName();
        //#endif
    }

    /** The lines written under the name of an item. */
    public static void lore(ItemStack stack, java.util.List<net.minecraft.network.chat.Component> lines) {
        //#if MC >= 1.20.5
        stack.set(net.minecraft.core.component.DataComponents.LORE, new net.minecraft.world.item.component.ItemLore(lines));
        //#else
        //$ var list = new net.minecraft.nbt.ListTag();
        //$ for (var line : lines) list.add(net.minecraft.nbt.StringTag.valueOf(net.minecraft.network.chat.Component.Serializer.toJson(line)));
        //$ stack.getOrCreateTagElement("display").put("Lore", list);
        //#endif
    }

    /** True for a command source allowed to judge the casino: the console or an operator. */
    public static boolean operator(net.minecraft.commands.CommandSourceStack source) {
        //#if MC >= 1.21.11
        //$ return source.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
        //#else
        return source.hasPermission(2);
        //#endif
    }
}
