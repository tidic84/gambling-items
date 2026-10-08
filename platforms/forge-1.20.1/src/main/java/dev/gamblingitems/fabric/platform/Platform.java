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

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu, D> {
        M create(int syncId, Inventory inventory, D data);
    }
}
