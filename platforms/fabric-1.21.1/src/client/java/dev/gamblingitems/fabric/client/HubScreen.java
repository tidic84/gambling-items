package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.menu.HubMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The terminal home screen. Games that are not implemented are shown, never opened. */
public final class HubScreen extends AbstractContainerScreen<HubMenu> {
    private static final int INK = 0xff0d131c, BORDER = 0xff304358;
    private static final int TEXT = 0xffe8eff6, MUTED = 0xff91a6ba, GOLD = 0xffffce69;

    public HubScreen(HubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 236;
        imageHeight = 180;
    }

    @Override protected void init() {
        super.init();
        GameMode[] modes = GameMode.values();
        for (int index = 0; index < modes.length; index++) {
            GameMode mode = modes[index];
            boolean available = GameMenus.AVAILABLE.contains(mode);
            int button = index;
            Button widget = Button.builder(Component.translatable("gui.gamblingitems.mode." + mode.id()), pressed -> {
                if (minecraft.gameMode != null) {
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
                }
            }).bounds(leftPos + 18, topPos + 40 + index * 22, 200, 20).build();
            widget.active = available;
            widget.setTooltip(Tooltip.create(available
                    ? Component.translatable("gui.gamblingitems.mode." + mode.id() + ".help")
                    : Component.translatable("gui.gamblingitems.mode_planned")));
            addRenderableWidget(widget);
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GOLD);
        g.drawString(font, title, x + 18, y + 12, TEXT, false);
        g.drawString(font, Component.translatable("gui.gamblingitems.hub_subtitle"), x + 18, y + 24, MUTED, false);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
