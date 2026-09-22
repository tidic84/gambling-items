package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.GameMenus;
import dev.gamblingitems.fabric.menu.HubMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The terminal home screen: a gallery of games, one tile each, with the icon of the game.
 * A game that is not implemented is shown greyed out and cannot be opened.
 */
public final class HubScreen extends AbstractContainerScreen<HubMenu> {
    private static final int COLUMNS = 3, TILE_WIDTH = 68, TILE_HEIGHT = 56, GAP = 6;
    private static final int TILE = 0xff172231, TILE_HOVER = 0xff21334a, TILE_OFF = 0xff131a24;

    public HubScreen(HubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        // The gallery grows with the number of games rather than assuming how many there are.
        int rows = (GameMode.values().length + COLUMNS - 1) / COLUMNS;
        imageWidth = 12 + COLUMNS * TILE_WIDTH + (COLUMNS - 1) * GAP + 12;
        imageHeight = 46 + rows * TILE_HEIGHT + (rows - 1) * GAP + 12;
    }

    /** The face of each game in the gallery. */
    private static ItemStack icon(GameMode mode) {
        return new ItemStack(switch (mode) {
            case UPGRADER -> Items.ANVIL;
            case CRASH -> Items.FIREWORK_ROCKET;
            case TRADE_UP -> Items.CRAFTING_TABLE;
            case CASE_OPENING -> Items.CHEST;
            case ROULETTE -> Items.CLOCK;
            case CASE_BATTLE -> Items.NETHERITE_SWORD;
            case BLACKJACK -> Items.PAPER;
            case BINGO -> Items.BOOKSHELF;
            case SLOT_MACHINE -> Items.LEVER;
        });
    }

    private int tileX(int index) { return leftPos + 12 + (index % COLUMNS) * (TILE_WIDTH + GAP); }
    private int tileY(int index) { return topPos + 46 + (index / COLUMNS) * (TILE_HEIGHT + GAP); }

    private int hovered(int mouseX, int mouseY) {
        for (int index = 0; index < GameMode.values().length; index++) {
            int x = tileX(index), y = tileY(index);
            if (mouseX >= x && mouseX < x + TILE_WIDTH && mouseY >= y && mouseY < y + TILE_HEIGHT) return index;
        }
        return -1;
    }

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int index = hovered((int) mouseX, (int) mouseY);
        if (button == 0 && index >= 0) {
            GameMode mode = GameMode.values()[index];
            if (!GameMenus.AVAILABLE.contains(mode)) return true;
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
            }
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        int index = hovered(mouseX, mouseY);
        if (index >= 0) {
            GameMode mode = GameMode.values()[index];
            boolean available = GameMenus.AVAILABLE.contains(mode);
            graphics.renderComponentTooltip(font, List.of(
                    Component.translatable("gui.gamblingitems.mode." + mode.id()),
                    available ? Component.translatable("gui.gamblingitems.mode." + mode.id() + ".help")
                            : Component.translatable("gui.gamblingitems.mode_planned")), mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, GameScreens.BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, GameScreens.INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GameScreens.GOLD);
        g.drawString(font, title, x + 12, y + 12, GameScreens.TEXT, false);
        GameScreens.fitted(g, font, Component.translatable("gui.gamblingitems.hub_subtitle"),
                x + 12, y + 26, imageWidth - 24, GameScreens.MUTED);
        int hovered = hovered(mouseX, mouseY);
        GameMode[] modes = GameMode.values();
        for (int index = 0; index < modes.length; index++) {
            GameMode mode = modes[index];
            boolean available = GameMenus.AVAILABLE.contains(mode);
            int tileLeft = tileX(index), tileTop = tileY(index);
            g.fill(tileLeft, tileTop, tileLeft + TILE_WIDTH, tileTop + TILE_HEIGHT,
                    !available ? TILE_OFF : index == hovered ? TILE_HOVER : TILE);
            g.fill(tileLeft, tileTop, tileLeft + TILE_WIDTH, tileTop + 2,
                    available ? GameScreens.GOLD : GameScreens.BORDER);
            // The icon is drawn twice as large as an inventory item, centred in its tile.
            g.pose().pushPose();
            g.pose().translate(tileLeft + TILE_WIDTH / 2f - 16, tileTop + 8f, 0);
            g.pose().scale(2f, 2f, 1f);
            g.renderFakeItem(icon(mode), 0, 0);
            g.pose().popPose();
            Component name = Component.translatable("gui.gamblingitems.mode." + mode.id());
            String label = font.plainSubstrByWidth(name.getString(), TILE_WIDTH - 8);
            g.drawString(font, label, tileLeft + (TILE_WIDTH - font.width(label)) / 2,
                    tileTop + TILE_HEIGHT - 22, available ? GameScreens.TEXT : GameScreens.MUTED, false);
            Component state = available ? Component.translatable("gui.gamblingitems.mode_open")
                    : Component.translatable("gui.gamblingitems.mode_soon");
            String stateLabel = font.plainSubstrByWidth(state.getString(), TILE_WIDTH - 8);
            g.drawString(font, stateLabel, tileLeft + (TILE_WIDTH - font.width(stateLabel)) / 2,
                    tileTop + TILE_HEIGHT - 11, available ? GameScreens.GREEN : GameScreens.MUTED, false);
        }
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
