package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.StationPanel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws the public screen of a station. It shows what happened, never anyone's inventory. */
public final class GameStationRenderer implements BlockEntityRenderer<GameStationEntity> {
    private static final int TEXT = 0xffe8eff6, GOLD = 0xffffce69, GREEN = 0xff6cdeb7;
    private final Font font;

    public GameStationRenderer(BlockEntityRendererProvider.Context context) { font = context.getFont(); }

    @Override public void render(GameStationEntity station, float partialTick, PoseStack pose,
                                 MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState().getValue(GameStationBlock.FACING).toYRot()));
        pose.translate(0, 0, 0.515);
        pose.scale(1f / 128, -1f / 128, 1f / 128);
        rectangle(pose, buffers, -188, -188, 188, 60, 0xff304358, 0);
        rectangle(pose, buffers, -186, -186, 186, 58, 0xff0d131c, 1);
        rectangle(pose, buffers, -186, -186, 186, -183, GOLD, 2);
        rectangle(pose, buffers, -180, -146, 180, -49, 0xff172231, 2);
        line(pose, buffers, Component.translatable("gui.gamblingitems.station." + station.mode().id()).getString(), -177, GOLD);
        boolean rolling = station.animating();
        String owner = station.idle() ? station.previewPlayer : station.playerName;
        line(pose, buffers, owner.isEmpty() ? Component.translatable("gui.gamblingitems.station_ready").getString() : owner, -162, TEXT);
        String status = station.idle() ? station.previewText : rolling ? station.rollingText : result(station);
        marquee(pose, buffers, status, -43, station.highlight && !rolling ? GREEN : GOLD, station.getLevel().getGameTime());
        String detail = station.publicBets.isEmpty() ? station.previewPlayer + " : " + station.previewText : station.publicBets;
        String[] entries = detail.split("\n");
        marquee(pose, buffers, entries[(int) (station.getLevel().getGameTime() / 50 % entries.length)], -27, TEXT,
                station.getLevel().getGameTime());
        animation(station, partialTick, pose, buffers);
        int hovered = -1;
        var hit = net.minecraft.client.Minecraft.getInstance().hitResult;
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var state = station.getLevel().getBlockState(blockHit.getBlockPos());
            if (state.getBlock() instanceof GameStationBlock && GameStationBlock.anchor(blockHit.getBlockPos(), state).equals(station.getBlockPos()))
                hovered = StationPanel.button(station.mode(), station.getBlockState().getValue(GameStationBlock.FACING), blockHit, station.getBlockPos());
        }
        for (var control : StationPanel.controls(station.mode(), station.phase)) {
            int button = control.action();
            int x = control.x(), y = control.y();
            String key = control.label();
            int color = switch (key) {
                case "red" -> 0xff943d4b;
                case "black" -> 0xff303542;
                case "green", "play", "cash_out" -> 0xff216e5b;
                default -> 0xff293d52;
            };
            boolean locked = switch (station.mode()) {
                case ROULETTE -> button >= 3 && button <= 5 && station.phase >= 2;
                case CRASH -> button == 4 && station.phase == 3;
                default -> button == 4 && station.animating();
            };
            if (locked) color = 0xff1c2631;
            if (hovered == button && !locked) {
                rectangle(pose, buffers, x - 1, y - 1, x + control.width() + 1, y + control.height() + 1, GOLD);
                color = 0xff41607a;
            }
            rectangle(pose, buffers, x, y, x + control.width(), y + control.height(), color, 4);
            String text = Component.translatable("gui.gamblingitems.panel." + key).getString();
            pose.pushPose();
            pose.translate(x + control.width() / 2f, y + (control.height() - 8) / 2f, 0.02);
            float scale = Math.min(1f, (control.width() - 10f) / Math.max(1, font.width(text)));
            pose.scale(scale, scale, scale);
            line(pose, buffers, text, 0, TEXT);
            pose.popPose();
        }
        pose.popPose();
    }

    @Override public boolean shouldRenderOffScreen(GameStationEntity station) { return true; }

    private void animation(GameStationEntity station, float partial, PoseStack pose, MultiBufferSource buffers) {
        double now = station.getLevel().getGameTime() + partial;
        double progress = Math.max(0, Math.min(1, (now - station.startedAt) / Math.max(1, station.durationTicks)));
        switch (station.mode()) {
            case CASE_OPENING, TRADE_UP -> reel(station, progress, pose, buffers);
            case ROULETTE -> {
                String[] colours = station.wheelColours.isEmpty() ? new String[]{"green", "red", "black", "red", "black"}
                        : station.wheelColours.split(",");
                double spin = station.phase == 2 ? Math.max(0, Math.min(1,
                        1 - (station.phaseEnd - now) / Math.max(1, station.animationTicks))) : 1;
                double target = Math.max(0, station.targetSlot) + 0.5;
                double turn = station.targetSlot < 0 ? 0 : (6 * Math.PI * 2 - target / colours.length * Math.PI * 2)
                        * (1 - Math.pow(1 - spin, 3));
                for (int i = 0; i < 240; i++) {
                    double angle = i * Math.PI * 2 / 240;
                    int index = (int) (i / 240.0 * colours.length);
                    int colour = colours[index].equals("green") ? GREEN : colours[index].equals("red") ? 0xffd65a68 : 0xff394657;
                    arc(pose, buffers, angle + turn, angle + turn + Math.PI * 2 / 240, 25, 42, colour);
                }
                dot(pose, buffers, 0, -143, GOLD, 4);
                dot(pose, buffers, 0, -133, TEXT, 2);
                line(pose, buffers, station.phase == 1 ? countdown(station, now) : station.phase == 2 ? "..." : station.rollingText,
                        -102, GOLD);
                label(pose, buffers, "ROULETTE", -170, -137, GOLD);
                label(pose, buffers, Component.translatable("gui.gamblingitems.panel.bet").getString(), 90, -137, TEXT);
                label(pose, buffers, station.playerName, 90, -122, GOLD);
            }
            case CRASH -> {
                rectangle(pose, buffers, -160, -65, 160, -64, 0xff304358);
                rectangle(pose, buffers, -160, -138, -159, -64, 0xff304358);
                double height = Math.min(70, Math.log(Math.max(1, station.multiplier / 100.0)) * 32);
                for (int x = 0; x < 280; x++) {
                    double t = x / 279.0;
                    dot(pose, buffers, -155 + x, -67 - height * t * t,
                            station.phase == 3 ? 0xffd65a68 : GREEN, 1);
                }
                line(pose, buffers, station.phase == 1 ? countdown(station, now)
                        : String.format(java.util.Locale.ROOT, "%.2fx", Math.max(100, station.multiplier) / 100.0), -130, GOLD);
            }
            case UPGRADER -> {
                double chance = 0;
                try { chance = Double.parseDouble(station.rollingText.replace("%", "")) / 100; }
                catch (NumberFormatException ignored) { }
                for (int i = 0; i < 180; i++) {
                    double angle = i * Math.PI * 2 / 180;
                    arc(pose, buffers, angle, angle + Math.PI * 2 / 180, 35, 39,
                            i / 180.0 < chance ? GREEN : 0xff304358);
                }
                // Stop within the matching sector; the server has already decided win/loss.
                double target = station.highlight ? chance / 2 : chance + (1 - chance) / 2;
                double angle = (5 + target) * Math.PI * 2 * (1 - Math.pow(1 - progress, 3));
                if (!station.idle()) dot(pose, buffers, Math.sin(angle) * 37, -98 - Math.cos(angle) * 37, GOLD, 4);
                line(pose, buffers, station.rollingText, -102, TEXT);
                item(station, station.resultItem, 100, -99, pose, buffers);
            }
            default -> { }
        }
    }

    private String countdown(GameStationEntity station, double now) {
        return Math.max(0, (int) Math.ceil((station.phaseEnd - now) / 20)) + " s";
    }

    private void reel(GameStationEntity station, double progress, PoseStack pose, MultiBufferSource buffers) {
        String[] items = station.reelItems.isEmpty() ? new String[]{"minecraft:chest"} : station.reelItems.split(",");
        int result = 0;
        for (int i = 0; i < items.length; i++) if (items[i].equals(station.resultItem)) { result = i; break; }
        double position = station.idle() ? 0 : (4 * items.length + result) * (1 - Math.pow(1 - progress, 3));
        int cell = (int) Math.floor(position);
        for (int offset = -4; offset <= 4; offset++) {
            int x = (int) Math.round((offset - (position - cell)) * 40);
            if (x < -150 || x > 150) continue;
            rectangle(pose, buffers, x - 18, -126, x + 18, -75, 0xff091018);
            item(station, items[Math.floorMod(cell + offset, items.length)], x, -103, pose, buffers);
        }
        rectangle(pose, buffers, -20, -131, 20, -129, GOLD);
        rectangle(pose, buffers, -20, -72, 20, -70, GOLD);
        rectangle(pose, buffers, -21, -131, -19, -70, GOLD);
        rectangle(pose, buffers, 19, -131, 21, -70, GOLD);
        dot(pose, buffers, 0, -138, GOLD, 3);
    }

    private void item(GameStationEntity station, String id, int x, int y, PoseStack pose, MultiBufferSource buffers) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return;
        pose.pushPose();
        pose.translate(x, y, 6);
        pose.scale(24, -24, 0.1f);
        net.minecraft.client.Minecraft.getInstance().getItemRenderer().renderStatic(
                new ItemStack(BuiltInRegistries.ITEM.get(key)), net.minecraft.world.item.ItemDisplayContext.GUI,
                0xf000f0, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY,
                pose, buffers, station.getLevel(), 0);
        pose.popPose();
    }

    private void dot(PoseStack pose, MultiBufferSource buffers, double x, double y, int colour, int radius) {
        rectangle(pose, buffers, (int) x - radius, (int) y - radius, (int) x + radius, (int) y + radius, colour, 5);
    }

    /** Adjacent ring segments share edges instead of overlapping coloured squares. */
    private void arc(PoseStack pose, MultiBufferSource buffers, double a, double b, float inner, float outer, int colour) {
        var vertices = buffers.getBuffer(RenderType.gui());
        var matrix = pose.last().pose();
        vertices.addVertex(matrix, (float) Math.sin(a) * inner, -98 - (float) Math.cos(a) * inner, 4).setColor(colour);
        vertices.addVertex(matrix, (float) Math.sin(b) * inner, -98 - (float) Math.cos(b) * inner, 4).setColor(colour);
        vertices.addVertex(matrix, (float) Math.sin(b) * outer, -98 - (float) Math.cos(b) * outer, 4).setColor(colour);
        vertices.addVertex(matrix, (float) Math.sin(a) * outer, -98 - (float) Math.cos(a) * outer, 4).setColor(colour);
    }

    private void label(PoseStack pose, MultiBufferSource buffers, String text, int x, int y, int colour) {
        pose.pushPose();
        pose.translate(0, 0, 8);
        font.drawInBatch(font.plainSubstrByWidth(text, 84), x, y, colour, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xf000f0);
        pose.popPose();
    }

    private void marquee(PoseStack pose, MultiBufferSource buffers, String text, int y, int color, long ticks) {
        if (font.width(text) > 348) {
            String padded = text + "   |   ";
            int offset = (int) (ticks / 5 % padded.length());
            text = padded.substring(offset) + padded.substring(0, offset);
        }
        line(pose, buffers, text, y, color);
    }

    private void rectangle(PoseStack pose, MultiBufferSource buffers, int x1, int y1, int x2, int y2, int color) {
        rectangle(pose, buffers, x1, y1, x2, y2, color, 3);
    }

    // Separate depth planes prevent coplanar quads from fighting in world-space rendering.
    private void rectangle(PoseStack pose, MultiBufferSource buffers, int x1, int y1, int x2, int y2, int color, float depth) {
        var vertices = buffers.getBuffer(RenderType.gui());
        var matrix = pose.last().pose();
        vertices.addVertex(matrix, x1, y1, depth).setColor(color);
        vertices.addVertex(matrix, x1, y2, depth).setColor(color);
        vertices.addVertex(matrix, x2, y2, depth).setColor(color);
        vertices.addVertex(matrix, x2, y1, depth).setColor(color);
    }

    private String result(GameStationEntity station) {
        if (station.resultItem.isEmpty()) {
            return Component.translatable("gui.gamblingitems." + station.resultKey).getString();
        }
        ResourceLocation id = ResourceLocation.tryParse(station.resultItem);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return station.resultItem;
        return font.plainSubstrByWidth(new ItemStack(BuiltInRegistries.ITEM.get(id)).getHoverName().getString(), 94);
    }

    private void line(PoseStack pose, MultiBufferSource buffers, String text, int y, int color) {
        text = font.plainSubstrByWidth(text, 348);
        pose.pushPose();
        pose.translate(0, 0, 8);
        font.drawInBatch(text, -font.width(text) / 2f, y, color, false,
                pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
        pose.popPose();
    }
}
