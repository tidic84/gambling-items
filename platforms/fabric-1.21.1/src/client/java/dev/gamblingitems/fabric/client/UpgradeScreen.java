package dev.gamblingitems.fabric.client;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.menu.CasinoLayout;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import dev.gamblingitems.fabric.value.ValueCatalog;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** One item staked for a better one: the odds ring in the middle, the targets on the side. */
public final class UpgradeScreen extends CasinoScreen<UpgradeMenu> {
    private static final int GX = CasinoLayout.GAME_X, GY = CasinoLayout.CONTENT_Y, GW = CasinoLayout.GAME_WIDTH;
    private static final int SX = CasinoLayout.SIDE_X, SW = CasinoLayout.SIDE_WIDTH;
    private static final int LIST_Y = GY + 22, LIST_HEIGHT = CasinoLayout.CONTENT_HEIGHT - 24;
    private static final int RING_X = GX + 118, RING_Y = GY + 46, RADIUS = 26;
    private final List<Integer> filtered = new ArrayList<>();
    private final OddsList targets = new OddsList();
    private EditBox search;
    private Button spin;
    private boolean wasAnimating;

    public UpgradeScreen(UpgradeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override protected GameMode mode() { return GameMode.UPGRADER; }
    @Override protected boolean portable() { return menu.portable(); }

    @Override protected void init() {
        String query = search == null ? "" : search.getValue();
        super.init();
        search = new EditBox(font, leftPos + SX + 8, topPos + GY + 8, SW - 14, 10, tr("search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setHint(Component.literal(tr("search").getString()).withStyle(style -> style.withColor(GameScreens.DIM)));
        search.setValue(query);
        search.setResponder(ignored -> { targets.reset(); filter(); });
        addRenderableWidget(search);
        spin = addRenderableWidget(CasinoButton.primary(tr("spin"), b -> {
                    if (rewarded()) collect(1); else send(UpgradeMenu.SPIN_BUTTON);
                })
                .bounds(leftPos + CasinoLayout.SLIP_X + 8, topPos + CasinoLayout.SLIP_Y + 52, CasinoLayout.SLIP_WIDTH - 16, 20).build());
        spin.setTooltip(Tooltip.create(tr("stake_warning")));
        filter();
    }

    private void filter() {
        filtered.clear();
        String query = search.getValue().strip().toLowerCase(Locale.ROOT);
        long input = menu.inputValue();
        for (int i = 0; i < menu.catalog().entries().size(); i++) {
            ValueCatalog.Entry entry = menu.catalog().entries().get(i);
            boolean matches = query.startsWith("@") ? entry.id().getNamespace().contains(query.substring(1))
                    : entry.id().toString().contains(query)
                    || entry.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(query);
            if (matches) filtered.add(i);
        }
        // With a stake, reachable targets come first, best odds first. Without one, the prizes lead.
        filtered.sort((a, b) -> {
            long va = menu.catalog().entries().get(a).value(), vb = menu.catalog().entries().get(b).value();
            if (input <= 0) return Long.compare(vb, va);
            boolean ra = va > input, rb = vb > input;
            if (ra != rb) return ra ? -1 : 1;
            return ra ? Long.compare(va, vb) : Long.compare(vb, va);
        });
    }

    private static String percent(double chance) {
        return chance > 0 && chance < 0.0001 ? "<0.01%" : String.format(Locale.ROOT, "%.2f%%", chance * 100);
    }

    private long lastInput = -1;

    private boolean rewarded() { return !menu.isAnimating() && !menu.getSlot(1).getItem().isEmpty(); }

    @Override protected void containerTick() {
        super.containerTick();
        if (menu.inputValue() != lastInput) { lastInput = menu.inputValue(); filter(); }
        spin.active = menu.canSpin() || rewarded();
        spin.setMessage(menu.isAnimating() ? tr("rolling") : !menu.getSlot(1).getItem().isEmpty() ? tr("collect_short") : tr("spin"));
        if (wasAnimating && !menu.isAnimating()) {
            if (menu.result() == 1) CasinoSounds.win(); else CasinoSounds.lose();
        }
        wasAnimating = menu.isAnimating();
    }

    private List<OddsList.Row> rows() {
        List<OddsList.Row> rows = new ArrayList<>();
        long input = menu.inputValue();
        for (int index : filtered) {
            ValueCatalog.Entry entry = menu.catalog().entries().get(index);
            boolean reachable = input > 0 && entry.value() > input;
            rows.add(new OddsList.Row(entry.stack(), reachable ? percent(menu.chanceFor(entry)) : GameScreens.value(entry.value()),
                    reachable ? GameScreens.GREEN : GameScreens.MUTED,
                    Component.translatable("gui.gamblingitems.arena.item_value", GameScreens.value(entry.value())),
                    input <= 0 || reachable, menu.selectedIndex() == index));
        }
        return rows;
    }

    @Override protected void renderGame(GuiGraphics g, float tick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        ValueCatalog.Entry target = menu.selected();
        boolean rewarded = !menu.getSlot(1).getItem().isEmpty() && !menu.isAnimating();
        GameScreens.card(g, x + GX, y + GY, GW, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.label(g, font, tr("input"), x + GX + 8, y + GY + 7, 80);
        String targetLabel = tr("target").getString().toUpperCase(Locale.ROOT);
        GameScreens.rightAligned(g, font, targetLabel, x + GX + GW - 8, y + GY + 7, GameScreens.DIM);

        GameScreens.well(g, font, x + 30, y + 72, menu.getSlot(0).getItem().isEmpty(), 0);
        GameScreens.fitted(g, font, Component.literal(GameScreens.value(menu.inputValue())), x + 26, y + 96, 40, GameScreens.MUTED);
        if (rewarded) GameScreens.ring(g, x + 202, y + 68, 24, 24, GameScreens.GOLD);
        GameScreens.well(g, font, x + 206, y + 72, target == null, 0);
        if (target != null) {
            if (menu.getSlot(1).getItem().isEmpty() || menu.isAnimating()) {
                g.renderFakeItem(target.stack(), x + 206, y + 72);
                g.fill(x + 206, y + 72, x + 222, y + 88, 0x60000000 | (GameScreens.HOLE & 0xffffff));
            }
            GameScreens.rightAligned(g, font, GameScreens.value(target.value()), x + 226, y + 96, GameScreens.MUTED);
        }
        renderRing(g, x + RING_X, y + RING_Y, tick);

        Component status = menu.isAnimating() ? tr("rolling")
                : menu.result() == 1 ? tr("won") : menu.result() == 2 ? tr("lost")
                : rewarded ? tr("collect")
                : menu.inputValue() == 0 ? tr("insert")
                : target == null ? tr("select") : tr("ready");
        int colour = menu.isAnimating() ? GameScreens.MUTED : menu.result() == 1 ? GameScreens.GOLD
                : menu.result() == 2 ? GameScreens.RED : menu.canSpin() ? GameScreens.GREEN : GameScreens.MUTED;
        int width = font.width(status);
        GameScreens.fitted(g, font, status, x + GX + Math.max(8, (GW - width) / 2), y + GY + 92, GW - 16, colour);

        GameScreens.card(g, x + SX, y + GY, SW, CasinoLayout.CONTENT_HEIGHT);
        GameScreens.rounded(g, x + SX + 4, y + GY + 4, SW - 8, 15, GameScreens.HOLE);
        List<OddsList.Row> rows = rows();
        if (rows.isEmpty()) GameScreens.paragraph(g, font, tr("no_results"), x + SX + 8, y + LIST_Y + 4, SW - 16, 3, GameScreens.DIM);
        else targets.render(g, font, rows, x + SX + 4, y + LIST_Y, SW - 6, LIST_HEIGHT, mouseX, mouseY);

        slip(g);
        int sx = x + CasinoLayout.SLIP_X + 8, sy = y + CasinoLayout.SLIP_Y + 8;
        GameScreens.label(g, font, tr("chance"), sx, sy, 80);
        GameScreens.heading(g, font, Component.literal(percent(menu.chance())), sx, sy + 12, 2,
                menu.chance() > 0 ? GameScreens.GREEN : GameScreens.DIM);
        GameScreens.label(g, font, tr("payout"), sx + 100, sy, 70);
        if (target != null && menu.inputValue() > 0) {
            double factor = (double) target.value() / menu.inputValue();
            g.drawString(font, String.format(Locale.ROOT, "x%.2f", factor), sx + 100, sy + 16, GameScreens.GOLD, false);
        } else g.drawString(font, "—", sx + 100, sy + 16, GameScreens.DIM, false);
    }

    /** The odds as an arc, and the marker that runs around it until it stops on the server's result. */
    private void renderRing(GuiGraphics g, int cx, int cy, float tick) {
        double chance = menu.chance();
        int steps = 72;
        int win = menu.isAnimating() || menu.result() == 0 ? GameScreens.GREEN : menu.result() == 1 ? GameScreens.GOLD : GameScreens.RED;
        for (int i = 0; i < steps; i++) {
            double a1 = i * Math.PI * 2 / steps - Math.PI / 2, a2 = (i + 1.15) * Math.PI * 2 / steps - Math.PI / 2;
            int colour = (i + .5) / steps < chance ? win : GameScreens.HOLE;
            ArenaShapes.line(g, (float) (cx + Math.cos(a1) * RADIUS), (float) (cy + Math.sin(a1) * RADIUS),
                    (float) (cx + Math.cos(a2) * RADIUS), (float) (cy + Math.sin(a2) * RADIUS), 6, colour);
        }
        double target = menu.result() == 1 ? chance * 0.5 : chance + (1 - chance) * 0.6;
        double progress = menu.result() == 0 ? 0
                : Math.min(1, (UpgradeMenu.ANIMATION_TICKS - menu.remainingTicks() + tick) / UpgradeMenu.ANIMATION_TICKS);
        double turns = menu.result() == 0 ? 0 : (5 + target) * GameScreens.eased(progress);
        double marker = turns * Math.PI * 2 - Math.PI / 2;
        float mx = (float) (cx + Math.cos(marker) * (RADIUS - 9)), my = (float) (cy + Math.sin(marker) * (RADIUS - 9));
        ArenaShapes.line(g, (float) (cx + Math.cos(marker) * (RADIUS - 15)), (float) (cy + Math.sin(marker) * (RADIUS - 15)),
                mx, my, 2, GameScreens.TEXT);
        String label = percent(chance);
        g.drawString(font, label, cx - font.width(label) / 2, cy - 4, chance > 0 ? GameScreens.TEXT : GameScreens.DIM, false);
    }

    @Override protected void renderOverlay(GuiGraphics g, int mouseX, int mouseY, float tick) {
        targets.tooltip(g, font, rows(), leftPos + SX + 4, topPos + LIST_Y, SW - 6, LIST_HEIGHT, mouseX, mouseY);
    }

    @Override protected boolean gameClicked(double x, double y, int button) {
        int index = targets.at(filtered.size(), leftPos + SX + 4, topPos + LIST_Y, SW - 6, LIST_HEIGHT, x, y);
        if (button != 0 || index < 0 || menu.isAnimating()) return false;
        send(filtered.get(index));
        return true;
    }

    @Override protected boolean gameScrolled(double x, double y, double amount) {
        if (!inside(x, y, SX, GY, SW, CasinoLayout.CONTENT_HEIGHT)) return false;
        targets.scroll(filtered.size(), LIST_HEIGHT, amount);
        return true;
    }

    //#if MC >= 1.21.9
    //$ @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
    //$     // Typing inventory-key characters in the search must not close the menu.
    //$     if (search.isFocused() && event.key() != 256) {
    //$         if (search.keyPressed(event) || search.canConsumeInput()) return true;
    //$     }
    //$     return super.keyPressed(event);
    //$ }
    //#else
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        // Typing inventory-key characters in the search must not close the menu.
        if (search.isFocused() && key != 256) {
            if (search.keyPressed(key, scan, modifiers) || search.canConsumeInput()) return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
    //#endif
}
