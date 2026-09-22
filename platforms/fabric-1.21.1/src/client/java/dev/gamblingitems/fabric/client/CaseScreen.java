package dev.gamblingitems.fabric.client;

import dev.gamblingitems.fabric.cases.CaseDefinition;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.cases.CaseReward;
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

/** Pick a case, pay the announced price, watch the reel stop on what the server already drew. */
public final class CaseScreen extends AbstractContainerScreen<CaseMenu> {
    private static final int INK = 0xff0d131c, PANEL = 0xff172231, BORDER = 0xff304358;
    private static final int TEXT = 0xffe8eff6, MUTED = 0xff91a6ba, GOLD = 0xffffce69, GREEN = 0xff6cdeb7;
    private static final int CELL = 22, VISIBLE_CELLS = 7, LOOPS = 4, LISTED_REWARDS = 7;
    private Button previous, next, open;

    public CaseScreen(CaseMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 320;
        imageHeight = 238;
    }

    /** Every game explains itself, in the language of the player. */
    private final GameRules rules = new GameRules("case_opening");

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
        previous = addRenderableWidget(Button.builder(Component.literal("<"), button -> select(-1))
                .bounds(leftPos + 14, topPos + 76, 18, 18).build());
        next = addRenderableWidget(Button.builder(Component.literal(">"), button -> select(1))
                .bounds(leftPos + 152, topPos + 76, 18, 18).build());
        open = addRenderableWidget(Button.builder(tr("open_case"), button -> {
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, CaseMenu.OPEN_BUTTON);
            }
        }).bounds(leftPos + 14, topPos + 130, 156, 18).build());
        open.setTooltip(Tooltip.create(tr("case_warning")));
    }

    /** The client only asks for a selection; the server owns it and refuses it while a case is opening. */
    private void select(int step) {
        int count = menu.setup().cases().cases().size();
        if (minecraft.gameMode == null || count == 0) return;
        int index = Math.floorMod(menu.selectedIndex() + step, count);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, index);
    }

    private static Component tr(String key) { return Component.translatable("gui.gamblingitems." + key); }
    private static String value(long value) { return BigDecimal.valueOf(value, 3).stripTrailingZeros().toPlainString(); }

    @Override protected void containerTick() {
        super.containerTick();
        boolean idle = !menu.isAnimating();
        previous.active = idle;
        next.active = idle;
        open.active = menu.canOpen();
        open.setMessage(menu.isAnimating() ? tr("rolling") : tr("open_case"));
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
        CaseDefinition definition = menu.selected();
        g.fill(x, y, x + imageWidth, y + imageHeight, BORDER);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + imageHeight - 1, INK);
        g.fill(x + 1, y + 1, x + imageWidth - 1, y + 3, GOLD);
        g.drawString(font, title, x + 12, y + 11, TEXT, false);
        g.drawString(font, tr("cases_subtitle"), x + 12, y + 23, MUTED, false);
        g.fill(x + 8, y + 34, x + 174, y + 150, PANEL);
        g.fill(x + 178, y + 32, x + 310, y + 150, PANEL);
        renderReel(g, x, y, definition, partialTick);
        g.drawCenteredString(font, definition == null ? tr("no_case").getString() : definition.title().getString(),
                x + 91, y + 81, GOLD);
        g.drawString(font, tr("price"), x + 16, y + 90, MUTED, false);
        g.drawString(font, tr("reward"), x + 140, y + 90, MUTED, false);
        slot(g, x + 20, y + 100);
        slot(g, x + 146, y + 100);
        if (definition != null) {
            g.renderFakeItem(definition.priceStack(), x + 44, y + 100);
            g.renderItemDecorations(font, definition.priceStack(), x + 44, y + 100);
        }
        renderStatus(g, x, y, definition);
        renderTable(g, x, y, definition);
        for (int row = 0; row < 3; row++)
            for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 155 + row * 18);
        for (int col = 0; col < 9; col++) slot(g, x + 79 + col * 18, y + 213);
        g.drawString(font, tr("inventory"), x + 12, y + 161, MUTED, false);
        g.drawString(font, tr("shift_click"), x + 12, y + 178, MUTED, false);
        g.drawString(font, tr("protected"), x + 12, y + 217, GREEN, false);
    }

    private void renderReel(GuiGraphics g, int x, int y, CaseDefinition definition, float partialTick) {
        int left = x + 12, right = x + 170, top = y + 40, bottom = y + 72;
        g.fill(left, top, right, bottom, 0xff091018);
        List<CaseReward> rewards = definition == null ? List.of() : definition.rewards();
        if (rewards.isEmpty()) {
            g.drawCenteredString(font, tr("no_case"), (left + right) / 2, top + 13, MUTED);
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
        double progress = Math.min(1, (CaseMenu.ANIMATION_TICKS - menu.remainingTicks() + partialTick)
                / CaseMenu.ANIMATION_TICKS);
        return stop * (1 - Math.pow(1 - progress, 3));
    }

    private void renderStatus(GuiGraphics g, int x, int y, CaseDefinition definition) {
        Component status;
        int color = MUTED;
        if (menu.isAnimating()) {
            status = tr("rolling");
        } else if (!menu.reward().isEmpty()) {
            status = tr("collect");
            color = GREEN;
        } else if (definition == null) {
            status = tr("no_case");
        } else if (!menu.isPaid()) {
            status = Component.translatable("gui.gamblingitems.case_insert",
                    definition.priceCount(), definition.priceStack().getHoverName());
        } else {
            status = tr("ready");
        }
        g.drawCenteredString(font, status, x + 91, y + 120, color);
    }

    private void renderTable(GuiGraphics g, int x, int y, CaseDefinition definition) {
        g.drawString(font, tr("contract"), x + 183, y + 36, MUTED, false);
        if (definition == null) {
            g.drawCenteredString(font, tr("no_case"), x + 244, y + 80, MUTED);
            return;
        }
        List<CaseReward> rewards = definition.rewards();
        int listed = Math.min(rewards.size(), LISTED_REWARDS);
        for (int index = 0; index < listed; index++) {
            int row = y + 48 + index * 13;
            ItemStack stack = rewards.get(index).stack();
            g.renderFakeItem(stack, x + 182, row - 4);
            g.renderItemDecorations(font, stack, x + 182, row - 4);
            boolean won = !menu.isAnimating() && menu.resultIndex() == index && !menu.reward().isEmpty();
            g.drawString(font, font.plainSubstrByWidth(stack.getHoverName().getString(), 58),
                    x + 202, row, won ? GREEN : TEXT, false);
            String chance = definition.percentOf(index).setScale(2, RoundingMode.HALF_UP).toPlainString() + "%";
            g.drawString(font, chance, x + 306 - font.width(chance), row, won ? GREEN : GOLD, false);
        }
        if (rewards.size() > listed) {
            g.drawString(font, Component.translatable("gui.gamblingitems.more_rewards", rewards.size() - listed),
                    x + 183, y + 48 + listed * 13, MUTED, false);
        }
        long price = definition.priceValue(menu.catalog());
        String average = value(definition.averageValue(menu.catalog())
                .setScale(0, RoundingMode.HALF_UP).longValueExact());
        // A key has no market value, so there is no return to compare the average to.
        g.drawString(font, price > 0
                        ? Component.translatable("gui.gamblingitems.average", average, value(price))
                        : Component.translatable("gui.gamblingitems.average_key", average),
                x + 183, y + 138, MUTED, false);
    }

    private static void slot(GuiGraphics g, int x, int y) {
        g.fill(x - 1, y - 1, x + 17, y + 17, BORDER);
        g.fill(x, y, x + 16, y + 16, 0xff091018);
    }

    @Override protected void renderLabels(GuiGraphics g, int x, int y) {}
}
