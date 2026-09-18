package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.value.ValueCatalog;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class UpgradeScreen extends AbstractContainerScreen<UpgradeMenu> {
    private static final int INK = 0xff0d131c, PANEL = 0xff172231, BORDER = 0xff304358;
    private static final int TEXT = 0xffe8eff6, MUTED = 0xff91a6ba, GOLD = 0xffffce69, GREEN = 0xff6cdeb7;
    private final List<Integer> filtered = new ArrayList<>();
    private final List<RewardButton> rows = new ArrayList<>();
    private EditBox search;
    private Button spin;
    private Button previous;
    private Button next;
    private int page;

    public UpgradeScreen(UpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
    }

    @Override protected void init() {
        super.init();
        rows.clear();
        search = new EditBox(font, leftPos + 181, topPos + 11, 126, 16, tr("search"));
        search.setMaxLength(64);
        search.setHint(tr("search"));
        search.setResponder(query -> { page = 0; filter(); });
        addRenderableWidget(search);
        for (int row = 0; row < 4; row++) {
            RewardButton button = new RewardButton(leftPos + 181, topPos + 34 + row * 21);
            rows.add(addRenderableWidget(button));
        }
        previous = addRenderableWidget(Button.builder(Component.literal("<"), b -> { page--; refreshRows(); })
                .bounds(leftPos + 181, topPos + 121, 23, 17).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), b -> { page++; refreshRows(); })
                .bounds(leftPos + 284, topPos + 121, 23, 17).build());
        spin = addRenderableWidget(Button.builder(tr("spin"), b -> send(UpgradeMenu.SPIN_BUTTON))
                .bounds(leftPos + 14, topPos + 120, 153, 18).build());
        spin.setTooltip(Tooltip.create(tr("stake_warning")));
        filter();
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }
    private void send(int id) {
        if (minecraft.gameMode != null) minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    private void filter() {
        filtered.clear();
        String query = search.getValue().toLowerCase(Locale.ROOT);
        for (int i = 0; i < menu.catalog().entries().size(); i++) {
            ValueCatalog.Entry entry = menu.catalog().entries().get(i);
            if (entry.id().toString().contains(query)
                    || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)) filtered.add(i);
        }
        refreshRows();
    }
    private void refreshRows() {
        int pages = Math.max(1, (filtered.size() + 3) / 4);
        page = Math.max(0, Math.min(page, pages - 1));
        for (int i = 0; i < rows.size(); i++) {
            int position = page * 4 + i;
            RewardButton row = rows.get(i);
            row.entryIndex = position < filtered.size() ? filtered.get(position) : -1;
            row.visible = row.entryIndex >= 0;
            if (row.visible) {
                var entry = menu.catalog().entries().get(row.entryIndex);
                row.setMessage(entry.stack().getHoverName());
                row.setTooltip(Tooltip.create(Component.translatable("gui.gamblingitems.target_tooltip",
                        entry.stack().getHoverName(), value(entry.value()))));
            }
        }
        previous.active = page > 0;
        next.active = page + 1 < pages;
    }
    private static String value(long value) { return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString(); }
    private static String percent(double chance) {
        return chance > 0 && chance < 0.0001 ? "<0.01%" : String.format(Locale.ROOT, "%.2f%%", chance * 100);
    }

    @Override protected void containerTick() {
        super.containerTick();
        spin.active = menu.canSpin();
        spin.setMessage(menu.isAnimating() ? tr("rolling") : tr("spin"));
        for (RewardButton row : rows) {
            if (row.entryIndex >= 0)
                row.active = !menu.isAnimating() && menu.catalog().entries().get(row.entryIndex).value() > menu.inputValue();
        }
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (mouseX >= leftPos + 12 && mouseX <= leftPos + 166
                && mouseY >= topPos + 142 && mouseY <= topPos + 154) {
            graphics.renderTooltip(font, tr("values_help"), mouseX, mouseY);
        }
    }

    @Override protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GOLD);
        g.drawString(font, title, x + 12, y + 11, TEXT, false);
        g.drawString(font, tr("subtitle"), x + 12, y + 23, MUTED, false);
        g.fill(x + 8, y + 34, x + 174, y + 115, PANEL);
        g.fill(x + 178, y + 32, x + 310, y + 119, PANEL);
        g.drawString(font, tr("input"), x + 16, y + 47, MUTED, false);
        g.drawString(font, tr("reward"), x + 140, y + 47, MUTED, false);
        slot(g, x + 20, y + 62);
        slot(g, x + 146, y + 62);
        if ((menu.getSlot(1).getItem().isEmpty() || menu.isAnimating()) && menu.selected() != null)
            g.renderFakeItem(menu.selected().stack(), x + 146, y + 62);

        double chance = menu.chance();
        double target = menu.result() == 1 ? chance * 0.5 : chance + (1 - chance) * 0.65;
        double progress = menu.result() == 0 ? 0
                : Math.min(1, (UpgradeMenu.ANIMATION_TICKS - menu.remainingTicks() + partialTick) / UpgradeMenu.ANIMATION_TICKS);
        double turns = menu.result() == 0 ? 0 : (6 + target) * (1 - Math.pow(1 - progress, 3));
        int cx = x + 91, cy = y + 71;
        for (int i = 0; i < 180; i++) {
            double angle = i * Math.PI * 2 / 180 - Math.PI / 2;
            int color = (i / 180.0) < chance ? GOLD : BORDER;
            for (int radius = 27; radius < 30; radius++) {
                int px = cx + (int) Math.round(Math.cos(angle) * radius);
                int py = cy + (int) Math.round(Math.sin(angle) * radius);
                g.fill(px, py, px + 2, py + 2, color);
            }
        }
        double marker = turns * Math.PI * 2 - Math.PI / 2;
        int mx = cx + (int) (Math.cos(marker) * 29);
        int my = cy + (int) (Math.sin(marker) * 29);
        g.fill(mx - 2, my - 2, mx + 3, my + 3, TEXT);
        g.drawCenteredString(font, percent(chance), cx, cy - 4, GOLD);

        Component status = menu.isAnimating() ? tr("rolling")
                : menu.result() == 1 ? tr("won")
                : menu.result() == 2 ? tr("lost")
                : !menu.getSlot(1).getItem().isEmpty() ? tr("collect")
                : menu.inputValue() == 0 ? tr("insert")
                : menu.selected() == null ? tr("select") : tr("ready");
        g.drawCenteredString(font, status, x + 91, y + 103,
                menu.result() == 1 && !menu.isAnimating() ? GREEN : MUTED);
        if (filtered.isEmpty()) g.drawCenteredString(font, tr("no_results"), x + 244, y + 68, MUTED);
        g.drawCenteredString(font, (page + 1) + " / " + Math.max(1, (filtered.size() + 3) / 4), x + 244, y + 126, MUTED);
        g.drawString(font, Component.translatable("gui.gamblingitems.value", value(menu.inputValue())), x + 12, y + 144, MUTED, false);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 155 + row * 18);
        for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 213);
        g.drawString(font, tr("inventory"), x + 12, y + 161, MUTED, false);
        g.drawString(font, tr("shift_click"), x + 12, y + 178, MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 217, GREEN, false);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, BORDER);
        g.fill(x, y, x + 16, y + 16, 0xff091018);
    }
    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}

    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // Typing inventory-key characters in the search must not close the menu.
        if (search.isFocused() && key != 256) {
            if (search.keyPressed(key, scan, modifiers) || search.canConsumeInput()) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    private final class RewardButton extends AbstractButton {
        int entryIndex = -1;
        RewardButton(int x, int y) { super(x, y, 126, 20, Component.empty()); }
        @Override public void onPress() { if (entryIndex >= 0) send(entryIndex); }
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
        @Override protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float delta) {
            if (entryIndex < 0) return;
            ValueCatalog.Entry entry = menu.catalog().entries().get(entryIndex);
            boolean selected = menu.selectedIndex() == entryIndex;
            g.fill(getX(), getY(), getX() + width, getY() + height, isHoveredOrFocused() ? 0xff2a3b4e : PANEL);
            if (selected) g.fill(getX(), getY(), getX() + 2, getY() + height, GOLD);
            g.renderFakeItem(entry.stack(), getX() + 4, getY() + 2);
            int color = active ? (selected ? GOLD : TEXT) : MUTED;
            g.drawString(font, font.plainSubstrByWidth(entry.stack().getHoverName().getString(), 99),
                    getX() + 24, getY() + 2, color, false);
            g.drawString(font, value(entry.value()), getX() + 24, getY() + 11, MUTED, false);
        }
    }
}
