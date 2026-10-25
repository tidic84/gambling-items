package dev.gamblingitems.fabric.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.StationPanel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Draws the public screen of a station. It shows what happened, never anyone's inventory. */
//#if MC >= 1.21.9
//$ public final class GameStationRenderer implements BlockEntityRenderer<GameStationEntity, GameStationRenderer.State> {
//#else
public final class GameStationRenderer implements BlockEntityRenderer<GameStationEntity> {
//#endif
    private static final int TEXT = 0xffe8eff6, GOLD = 0xffffce69, GREEN = 0xff6cdeb7;
    private final Font font;

    public GameStationRenderer(BlockEntityRendererProvider.Context context) {
        //#if MC >= 1.21.9
        //$ font = context.font();
        //#else
        font = context.getFont();
        //#endif
    }

    //#if MC >= 1.21.9
    //$ /** Since 1.21.9 a frame first extracts what it draws; a station is read when it is drawn, on the same thread. */
    //$ public static final class State extends net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState {
    //$     GameStationEntity station;
    //$     float partialTick;
    //$ }
    //$
    //$ @Override public State createRenderState() { return new State(); }
    //$
    //$ @Override public void extractRenderState(GameStationEntity station, State state, float partialTick,
    //$         net.minecraft.world.phys.Vec3 camera, net.minecraft.client.renderer.feature.ModelFeatureRenderer.CrumblingOverlay crumbling) {
    //$     BlockEntityRenderer.super.extractRenderState(station, state, partialTick, camera, crumbling);
    //$     state.station = station;
    //$     state.partialTick = partialTick;
    //$ }
    //$
    //$ @Override public void submit(State state, PoseStack pose, net.minecraft.client.renderer.SubmitNodeCollector collector,
    //$         net.minecraft.client.renderer.state.CameraRenderState camera) {
    //$     draw(state.station, state.partialTick, pose, new WorldCanvas(collector));
    //$ }
    //#elif MC >= 1.21.5
    //$ @Override public void render(GameStationEntity station, float partialTick, PoseStack pose,
    //$         net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay, net.minecraft.world.phys.Vec3 camera) {
    //$     draw(station, partialTick, pose, new WorldCanvas(buffers));
    //$ }
    //#else
    @Override public void render(GameStationEntity station, float partialTick, PoseStack pose,
                                 net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {
        draw(station, partialTick, pose, new WorldCanvas(buffers));
    }
    //#endif

    private void draw(GameStationEntity station, float partialTick, PoseStack pose, WorldCanvas buffers) {
        long start = SmokeBench.ENABLED ? System.nanoTime() : 0;
        if (inView(station)) {
            drawStation(station, partialTick, pose, buffers);
            buffers.flush();
        }
        if (SmokeBench.ENABLED) SmokeBench.furniture(System.nanoTime() - start);
    }

    /** How far the furniture reaches from the middle of its block, in blocks, glows and confetti included. */
    private static final double REACH = 3.5;
    /** The view widens with sprinting and speed; this much more than the set field of view stays drawn. */
    private static final double FOV_MARGIN = 1.5;

    /**
     * Furniture is drawn off screen, so that a tall cabinet never vanishes with the section its base
     * stands in, but not once it is wholly behind the view. The cone's tip is set four blocks behind the
     * eyes, where the camera stands in third person; a camera looking back at the player culls nothing.
     * Also sets how much detail the piece is drawn with, from how far it stands.
     */
    private static boolean inView(GameStationEntity station) {
        var minecraft = net.minecraft.client.Minecraft.getInstance();
        var viewer = minecraft.getCameraEntity();
        WorldCanvas.focus(0);
        if (viewer == null) return true;
        var eye = viewer.getEyePosition();
        var pos = station.getBlockPos();
        // Measured from the eyes to the near side of the piece: what it shows is never drawn too coarse.
        WorldCanvas.focus(Math.sqrt(pos.getCenter().distanceToSqr(eye)) - 1.5);
        if (minecraft.options.getCameraType().isMirrored()) return true;
        var look = viewer.getViewVector(1);
        double dx = pos.getX() + .5 - (eye.x - look.x * 4), dy = pos.getY() + 1.5 - (eye.y - look.y * 4),
                dz = pos.getZ() + .5 - (eye.z - look.z * 4);
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (distance <= REACH) return true;
        double aspect = (double) minecraft.getWindow().getWidth() / Math.max(1, minecraft.getWindow().getHeight());
        double half = Math.toRadians(minecraft.options.fov().get() * FOV_MARGIN) / 2;
        double reach = Math.atan(Math.tan(Math.min(half, 1.5)) * Math.sqrt(1 + aspect * aspect)) + Math.asin(REACH / distance);
        return reach >= Math.PI || (dx * look.x + dy * look.y + dz * look.z) / distance >= Math.cos(reach);
    }

    private void drawStation(GameStationEntity station, float partialTick, PoseStack pose, WorldCanvas buffers) {
        if (station.getBlockState().getBlock() instanceof dev.gamblingitems.fabric.arcade.DecorBlock decor) {
            ArcadeDecor.render(station, decor, partialTick, pose, buffers, font);
            return;
        }
        if (SlotCabinet.isCabinet(station)) {
            // A cabinet has no monitor: its reels are drawn on its own front.
            SlotCabinet.render(station, partialTick, pose, buffers, font);
            return;
        }
        if (StationTables.hasTable(station)) {
            // A table has no monitor: everything it shows is on its felt.
            StationTables.render(station, partialTick, pose, buffers, font);
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-station.getBlockState().getValue(GameStationBlock.FACING).toYRot()));
        pose.translate(0, 0, 0.515);
        pose.scale(1f / 128, -1f / 128, 1f / 128);
        int accent = accent(station.mode());
        // The glass: night blue, darker towards the foot, under a header in the colour of the game.
        for (int band = 0; band < 8; band++) {
            int top = -190 + band * 31;
            rectangle(pose, buffers, -190, top, 190, Math.min(62, top + 31), shade(0xff111b2a, 1 - band * 0.06f), 0);
        }
        rectangle(pose, buffers, -190, -190, 190, -160, shade(accent, 0.45f), 1);
        rectangle(pose, buffers, -190, -190, 190, -186, accent, 1.5f);
        rectangle(pose, buffers, -190, -161, 190, -160, GOLD, 1.5f);
        line(pose, buffers, Component.translatable("gui.gamblingitems.station." + station.mode().id()).getString(), -181, GOLD, 1.7f);
        rectangle(pose, buffers, -182, -148, 182, -50, 0xff22344a, 1.6f);
        rectangle(pose, buffers, -181, -147, 181, -51, 0xff0c141f, 1.8f);
        boolean rolling = station.animating();
        String owner = station.idle() ? station.previewPlayer : station.playerName;
        line(pose, buffers, owner.isEmpty() ? WorldCanvas.translate("gui.gamblingitems.station_ready") : owner, -157, TEXT, 0.9f);
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
                case ROULETTE -> button >= StationPanel.FIRST_MOVE && button <= StationPanel.FIRST_MOVE + 2
                        && station.phase >= 2;
                case CRASH -> button == StationPanel.FIRST_MOVE + 1 && station.phase == 3;
                default -> button == StationPanel.FIRST_MOVE + 1 && station.animating();
            };
            if (locked) color = 0xff1c2631;
            if (hovered == button && !locked) {
                rectangle(pose, buffers, x - 1, y - 1, x + control.width() + 1, y + control.height() + 1, GOLD);
                color = shade(color, 1.35f);
            }
            // A raised key: lighter along the top, a shadow along the bottom.
            rectangle(pose, buffers, x, y, x + control.width(), y + control.height(), color, 4);
            rectangle(pose, buffers, x, y, x + control.width(), y + 3, shade(color, 1.4f), 4.2f);
            rectangle(pose, buffers, x, y + control.height() - 3, x + control.width(), y + control.height(), shade(color, 0.6f), 4.2f);
            String text = WorldCanvas.translate("gui.gamblingitems.panel." + key);
            pose.pushPose();
            pose.translate(x + control.width() / 2f, y + (control.height() - 8) / 2f, 0.02);
            float scale = Math.min(1f, (control.width() - 10f) / Math.max(1, WorldCanvas.width(font, text)));
            pose.scale(scale, scale, scale);
            line(pose, buffers, text, 0, TEXT);
            pose.popPose();
        }
        pose.popPose();
    }

    //#if MC >= 1.21.6
    //$ @Override public boolean shouldRenderOffScreen() { return true; }
    //#else
    @Override public boolean shouldRenderOffScreen(GameStationEntity station) { return true; }
    //#endif

    private void animation(GameStationEntity station, float partial, PoseStack pose, WorldCanvas buffers) {
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
                label(pose, buffers, WorldCanvas.translate("gui.gamblingitems.panel.bet"), 90, -137, TEXT);
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

    private void reel(GameStationEntity station, double progress, PoseStack pose, WorldCanvas buffers) {
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

    private void item(GameStationEntity station, String id, int x, int y, PoseStack pose, WorldCanvas buffers) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) return;
        pose.pushPose();
        pose.translate(x, y, 6);
        pose.scale(24, -24, 0.1f);
        buffers.item(pose, new ItemStack(dev.gamblingitems.fabric.Compat.item(key)), 0xf000f0, station.getLevel());
        pose.popPose();
    }

    private void dot(PoseStack pose, WorldCanvas buffers, double x, double y, int colour, int radius) {
        rectangle(pose, buffers, (int) x - radius, (int) y - radius, (int) x + radius, (int) y + radius, colour, 5);
    }

    /** Adjacent ring segments share edges instead of overlapping coloured squares. */
    private void arc(PoseStack pose, WorldCanvas buffers, double a, double b, float inner, float outer, int colour) {
        buffers.quads(pose, (matrix, vertices) -> {
        WorldCanvas.vertex(vertices, matrix, (float) Math.sin(a) * inner, -98 - (float) Math.cos(a) * inner, 4, colour);
        WorldCanvas.vertex(vertices, matrix, (float) Math.sin(b) * inner, -98 - (float) Math.cos(b) * inner, 4, colour);
        WorldCanvas.vertex(vertices, matrix, (float) Math.sin(b) * outer, -98 - (float) Math.cos(b) * outer, 4, colour);
        WorldCanvas.vertex(vertices, matrix, (float) Math.sin(a) * outer, -98 - (float) Math.cos(a) * outer, 4, colour);
        });
    }

    private void label(PoseStack pose, WorldCanvas buffers, String text, int x, int y, int colour) {
        pose.pushPose();
        pose.translate(0, 0, 8);
        buffers.text(pose, font, font.plainSubstrByWidth(text, 84), x, y, colour, 0xf000f0);
        pose.popPose();
    }

    private void marquee(PoseStack pose, WorldCanvas buffers, String text, int y, int color, long ticks) {
        if (WorldCanvas.width(font, text) > 348) {
            String padded = text + "   |   ";
            int offset = (int) (ticks / 5 % padded.length());
            text = padded.substring(offset) + padded.substring(0, offset);
        }
        line(pose, buffers, text, y, color);
    }

    private void rectangle(PoseStack pose, WorldCanvas buffers, int x1, int y1, int x2, int y2, int color) {
        rectangle(pose, buffers, x1, y1, x2, y2, color, 3);
    }

    // Separate depth planes prevent coplanar quads from fighting in world-space rendering.
    private void rectangle(PoseStack pose, WorldCanvas buffers, int x1, int y1, int x2, int y2, int color, float depth) {
        buffers.quads(pose, (matrix, vertices) -> {
        WorldCanvas.vertex(vertices, matrix, x1, y1, depth, color);
        WorldCanvas.vertex(vertices, matrix, x1, y2, depth, color);
        WorldCanvas.vertex(vertices, matrix, x2, y2, depth, color);
        WorldCanvas.vertex(vertices, matrix, x2, y1, depth, color);
        });
    }

    private String result(GameStationEntity station) {
        if (station.resultItem.isEmpty()) {
            return WorldCanvas.translate("gui.gamblingitems." + station.resultKey);
        }
        ResourceLocation id = ResourceLocation.tryParse(station.resultItem);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return station.resultItem;
        return font.plainSubstrByWidth(new ItemStack(dev.gamblingitems.fabric.Compat.item(id)).getHoverName().getString(), 94);
    }

    private void line(PoseStack pose, WorldCanvas buffers, String text, int y, int color) {
        text = font.plainSubstrByWidth(text, 348);
        pose.pushPose();
        pose.translate(0, 0, 8);
        buffers.text(pose, font, text, -WorldCanvas.width(font, text) / 2f, y, color, 0xF000F0);
        pose.popPose();
    }

    /** A centred line at a size, shrunk to fit the width of the screen. */
    private void line(PoseStack pose, WorldCanvas buffers, String text, int y, int color, float scale) {
        float fitted = Math.min(scale, 348f / Math.max(1, WorldCanvas.width(font, text)));
        pose.pushPose();
        pose.translate(0, y, 8);
        pose.scale(fitted, fitted, fitted);
        buffers.text(pose, font, text, -WorldCanvas.width(font, text) / 2f, -4, color, 0xF000F0);
        pose.popPose();
    }

    /** The colour a station wears in its header, one per game. */
    private static int accent(dev.gamblingitems.core.GameMode mode) {
        return switch (mode) {
            case UPGRADER -> 0xff2fbf7a;
            case TRADE_UP -> 0xff4cc3d9;
            case CASE_OPENING -> 0xffd9b25a;
            case CRASH -> 0xffd2434f;
            case ROULETTE -> 0xffb8323f;
            case BLACKJACK -> 0xff17784a;
            case CASE_BATTLE -> 0xff8a5cd0;
            case BINGO -> 0xff3a72d8;
            default -> GOLD;
        };
    }

    private static int shade(int colour, float factor) {
        return SlotCabinet.shade(colour, factor);
    }
}
