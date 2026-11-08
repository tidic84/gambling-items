package dev.gamblingitems.fabric.platform;

import java.nio.file.Path;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkHooks;

/**
 * Forge 47 (Minecraft 1.20.1) version of the loader calls the shared game code makes. It replaces
 * the Fabric class of the same name and keeps the same signatures.
 */
public final class Platform {
    private Platform() {}

    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    private static final net.minecraftforge.network.simple.SimpleChannel MENU_CHANNEL =
            net.minecraftforge.network.NetworkRegistry.newSimpleChannel(
                    new net.minecraft.resources.ResourceLocation("gamblingitems", "menu_button"),
                    () -> "1", "1"::equals, "1"::equals);

    public static void initializeMenuNetworking() {
        MENU_CHANNEL.messageBuilder(dev.gamblingitems.fabric.menu.MenuButton.class, 0,
                        net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER)
                .encoder(dev.gamblingitems.fabric.menu.MenuButton::write)
                .decoder(dev.gamblingitems.fabric.menu.MenuButton::read)
                .consumerMainThread((action, context) -> {
                    ServerPlayer player = context.get().getSender();
                    if (player != null) dev.gamblingitems.fabric.menu.MenuButton.handle(player, action);
                }).add();
    }

    public static net.minecraft.network.protocol.Packet<?> menuButtonPacket(int containerId, int button) {
        return MENU_CHANNEL.toVanillaPacket(new dev.gamblingitems.fabric.menu.MenuButton(containerId, button),
                net.minecraftforge.network.NetworkDirection.PLAY_TO_SERVER);
    }

    /** A menu whose client side is built from data the server sends when it opens. */
    public static <M extends AbstractContainerMenu, D> MenuType<M> menuType(
            MenuFactory<M, D> factory, dev.gamblingitems.fabric.compat.StreamCodec<? super FriendlyByteBuf, D> codec) {
        return IForgeMenuType.create((id, inventory, buffer) -> factory.create(id, inventory, codec.decode(buffer)));
    }

    /** Opens a menu of a {@link #menuType}, sending {@code data} for its client side. */
    public static <D> void openMenu(ServerPlayer player, Component title, D data,
            dev.gamblingitems.fabric.compat.StreamCodec<? super FriendlyByteBuf, D> codec, MenuConstructor menu) {
        NetworkHooks.openScreen(player, new SimpleMenuProvider(menu, title), buffer -> codec.encode(buffer, data));
    }

    /** The mod's own creative tab, listing {@code entries} in order. */
    public static net.minecraft.world.item.CreativeModeTab creativeTab(Component title,
            java.util.function.Supplier<net.minecraft.world.item.ItemStack> icon,
            java.util.function.Supplier<java.util.List<net.minecraft.world.item.Item>> entries) {
        return net.minecraft.world.item.CreativeModeTab.builder().title(title).icon(icon)
                .displayItems((parameters, output) -> entries.get().forEach(output::accept)).build();
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu, D> {
        M create(int syncId, Inventory inventory, D data);
    }
}
