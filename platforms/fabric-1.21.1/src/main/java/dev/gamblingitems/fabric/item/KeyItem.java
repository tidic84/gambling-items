package dev.gamblingitems.fabric.item;

import dev.gamblingitems.core.cases.CaseRarity;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/**
 * A key found on mobs. It is not a stake and has no market value: it opens the case of its rarity,
 * and nothing else. Its colour is the colour of that rarity, on every screen that shows it.
 */
public final class KeyItem extends Item {
    private final CaseRarity rarity;

    public KeyItem(CaseRarity rarity, Properties properties) {
        super(properties);
        this.rarity = rarity;
    }

    public CaseRarity rarity() { return rarity; }

    public static ChatFormatting colourOf(CaseRarity rarity) {
        return switch (rarity) {
            case COMMON -> ChatFormatting.WHITE;
            case UNCOMMON -> ChatFormatting.GREEN;
            case RARE -> ChatFormatting.AQUA;
            case EPIC -> ChatFormatting.LIGHT_PURPLE;
            case LEGENDARY -> ChatFormatting.GOLD;
            case MYTHIC -> ChatFormatting.RED;
        };
    }

    @Override public Component getName(ItemStack stack) {
        return super.getName(stack).copy().withStyle(colourOf(rarity));
    }

    @Override public void appendHoverText(ItemStack stack, TooltipContext context,
                                          List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.gamblingitems.key.help")
                .withStyle(ChatFormatting.GRAY));
    }
}
