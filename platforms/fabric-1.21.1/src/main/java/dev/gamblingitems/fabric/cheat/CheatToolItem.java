package dev.gamblingitems.fabric.cheat;

import dev.gamblingitems.core.cheat.CheatRules.Tool;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A trick, carried in the inventory. It does nothing on its own: the game window it belongs to
 * offers to use it, and every use wears it a little more.
 */
public final class CheatToolItem extends Item {
    private final Tool tool;

    public CheatToolItem(Tool tool, Properties properties) {
        super(properties);
        this.tool = tool;
    }

    public Tool tool() { return tool; }

    private void describe(java.util.function.Consumer<Component> lines) {
        lines.accept(Component.translatable("item.gamblingitems." + tool.id() + ".desc").withStyle(ChatFormatting.GRAY));
        lines.accept(Component.translatable("item.gamblingitems.cheat_tool.warning").withStyle(ChatFormatting.DARK_RED));
    }

    //#if MC >= 1.21.5
    //$ @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
    //$         net.minecraft.world.item.component.TooltipDisplay display, java.util.function.Consumer<Component> lines, TooltipFlag flag) {
    //$     describe(lines);
    //$ }
    //#elif MC >= 1.20.5
    @Override public void appendHoverText(ItemStack stack, Item.TooltipContext context,
            java.util.List<Component> lines, TooltipFlag flag) {
        describe(lines::add);
    }
    //#else
    //$ @Override public void appendHoverText(ItemStack stack, net.minecraft.world.level.Level level,
    //$         java.util.List<Component> lines, TooltipFlag flag) {
    //$     describe(lines::add);
    //$ }
    //#endif
}
