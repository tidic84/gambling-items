package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpTable;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class TradeUpScreen extends AbstractContainerScreen<TradeUpMenu> {
    private static final int INK = 0xff0d131c, PANEL = 0xff172231, BORDER = 0xff304358;
    private static final int TEXT = 0xffe8eff6, MUTED = 0xff91a6ba, GOLD = 0xffffce69, GREEN = 0xff6cdeb7;
    private static final int CELL = 22, VISIBLE_CELLS = 7, LOOPS = 4;
    private Button trade;
    /** Kept so the reel can still show the contract after the server has consumed the stake. */
    private TradeUpTable shown;

    public TradeUpScreen(TradeUpMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
    }

    /** Every game explains itself, in the language of the player. */
    private final GameRules rules = new GameRules("trade_up");

    @Override public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // While the rules are up they take every click, so nothing is played by accident.
        if (rules.open()) {
            rules.close();
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override protected void init() {
        super.init();
        addRenderableWidget(rules.button(leftPos + imageWidth - 30, topPos + 6));
        trade = addRenderableWidget(Button.builder(tr("trade"), button -> {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, TradeUpMenu.SPIN_BUTTON);
            }
        }).bounds(leftPos + 14, topPos + 120, 153, 18).build());
        trade.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.trade_warning",
                menu.setup().settings().requiredUnits(), ratio())));
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }
    private String ratio() {
        return BigDecimal.valueOf(menu.setup().settings().unitRatioBasisPoints(), 4)
                .stripTrailingZeros().toPlainString();
    }
    private static String value(long value) { return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString(); }

    @Override protected void containerTick() {
        super.containerTick();
        trade.active = menu.canSpin();
        trade.setMessage(menu.isAnimating() ? tr("rolling") : tr("trade"));
        // The contract stays on screen until the reward is collected, then follows the slots again.
        if (!menu.isAnimating() && menu.reward().isEmpty()) shown = menu.table().orElse(null);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (rules.open()) {
            rules.render(graphics, font, width, height);
            return;
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GOLD);
        g.drawString(font, title, x + 12, y + 11, TEXT, false);
        g.drawString(font, Component.translatable("gui.gamblingitems.trade_up_subtitle",
                menu.setup().settings().requiredUnits(), ratio()), x + 12, y + 23, MUTED, false);
        g.fill(x + 8, y + 34, x + 174, y + 115, PANEL);
        g.fill(x + 178, y + 32, x + 310, y + 119, PANEL);
        renderReel(g, x, y, partialTick);
        g.drawString(font, tr("input"), x + 16, y + 78, MUTED, false);
        g.drawString(font, tr("reward"), x + 140, y + 78, MUTED, false);
        for (int slot = 0; slot < TradeUpMenu.INPUT_SLOTS; slot++) slot(g, x + 20 + slot * 22, y + 88);
        slot(g, x + 146, y + 88);
        renderStatus(g, x, y);
        renderContract(g, x, y);
        g.drawString(font, Component.translatable("gui.gamblingitems.value", value(menu.stakeValue())),
                x + 12, y + 144, MUTED, false);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 155 + row * 18);
        for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 213);
        g.drawString(font, tr("inventory"), x + 12, y + 161, MUTED, false);
        g.drawString(font, tr("shift_click"), x + 12, y + 178, MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 217, GREEN, false);
    }

    private void renderReel(GuiGraphics g, int x, int y, float partialTick) {
        int left = x + 12, right = x + 170, top = y + 40, bottom = y + 72;
        g.fill(left, top, right, bottom, 0xff091018);
        List<ValueCatalog.Entry> rewards = shown == null ? List.of() : shown.rewards();
        if (rewards.isEmpty()) {
            g.drawCenteredString(font, tr("no_contract"), (left + right) / 2, top + 13, MUTED);
        } else {
            double position = reelPosition(rewards.size(), partialTick);
            int centre = (left + right) / 2 - 8;
            int cell = (int) Math.floor(position);
            double shift = position - cell;
            g.enableScissor(left + 1, top + 1, right - 1, bottom - 1);
            for (int offset = -VISIBLE_CELLS / 2; offset <= VISIBLE_CELLS / 2; offset++) {
                int index = Math.floorMod(cell + offset, rewards.size());
                int drawX = centre + (int) Math.round((offset - shift) * CELL);
                g.renderFakeItem(rewards.get(index).stack(), drawX, top + 8);
            }
            g.disableScissor();
            g.fill(centre - 2, top + 1, centre - 1, bottom - 1, GOLD);
            g.fill(centre + 17, top + 1, centre + 18, bottom - 1, GOLD);
        }
        g.fill(left, top, right, top + 1, BORDER);
        g.fill(left, bottom - 1, right, bottom, BORDER);
    }

    /** The reel only replays the decision the server already made. */
    private double reelPosition(int size, float partialTick) {
        int index = menu.resultIndex();
        if (index < 0 || index >= size) return 0;
        double stop = (double) LOOPS * size + index;
        if (!menu.isAnimating()) return stop;
        double progress = Math.min(1, (TradeUpMenu.ANIMATION_TICKS - menu.remainingTicks() + partialTick)
                / TradeUpMenu.ANIMATION_TICKS);
        return stop * (1 - Math.pow(1 - progress, 3));
    }

    private void renderStatus(GuiGraphics g, int x, int y) {
        int units = menu.stakedUnits();
        int required = menu.setup().settings().requiredUnits();
        Component status;
        int color = MUTED;
        if (menu.isAnimating()) {
            status = tr("rolling");
        } else if (!menu.reward().isEmpty()) {
            status = tr("collect");
            color = GREEN;
        } else if (units == 0) {
            status = Component.translatable("gui.gamblingitems.trade_insert", required);
        } else if (units != required) {
            status = Component.translatable("gui.gamblingitems.trade_units", units, required);
        } else if (menu.table().isEmpty()) {
            status = tr("trade_impossible");
        } else {
            status = tr("ready");
        }
        g.drawCenteredString(font, status, x + 91, y + 106, color);
    }

    private void renderContract(GuiGraphics g, int x, int y) {
        g.drawString(font, tr("contract"), x + 183, y + 36, MUTED, false);
        if (shown == null) {
            g.drawCenteredString(font, tr("no_contract"), x + 244, y + 70, MUTED);
            return;
        }
        List<ValueCatalog.Entry> rewards = shown.rewards();
        for (int index = 0; index < rewards.size() && index < 6; index++) {
            int row = y + 48 + index * 12;
            ItemStack stack = rewards.get(index).stack();
            g.renderFakeItem(stack, x + 182, row - 4);
            boolean won = !menu.isAnimating() && menu.resultIndex() == index && !menu.reward().isEmpty();
            g.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), 62),
                    x + 200, row, won ? GREEN : TEXT, false);
            String chance = shown.percentOf(index).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
            g.drawString(font, chance, x + 306 - font.width(chance), row, won ? GREEN : GOLD, false);
        }
        g.drawString(font, Component.translatable("gui.gamblingitems.average",
                        value(shown.averageValue(menu.setup()).setScale(0, RoundingMode.HALF_UP).longValueExact()),
                        value(shown.totalStake())),
                x + 183, y + 108, MUTED, false);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, BORDER);
        g.fill(x, y, x + 16, y + 16, 0xff091018);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
