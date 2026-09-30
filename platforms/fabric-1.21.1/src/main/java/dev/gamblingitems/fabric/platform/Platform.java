package dev.gamblingitems.fabric.platform;

import java.nio.file.Path;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;

/**
 * The only loader calls the shared game code makes. The NeoForge build compiles this same source
 * tree with its own copy of this class, so nothing else may import a loader API.
 */
public final class Platform {
    private Platform() {}

    public static Path configDir() { return FabricLoader.getInstance().getConfigDir(); }

    /** A menu whose client side is built from data the server sends when it opens. */
    public static <M extends AbstractContainerMenu, D> MenuType<M> menuType(
            MenuFactory<M, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return new ExtendedScreenHandlerType<>(factory::create, codec);
    }

    /** Opens a menu of a {@link #menuType}, sending {@code data} for its client side. */
    public static <D> void openMenu(ServerPlayer player, Component title, D data,
            StreamCodec<? super RegistryFriendlyByteBuf, D> codec, MenuConstructor menu) {
        player.openMenu(new ExtendedScreenHandlerFactory<D>() {
            @Override public D getScreenOpeningData(ServerPlayer ignored) { return data; }
            @Override public Component getDisplayName() { return title; }
            @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player opener) {
                return menu.createMenu(id, inventory, opener);
            }
        });
    }

    @FunctionalInterface
    public interface MenuFactory<M extends AbstractContainerMenu, D> {
        M create(int syncId, Inventory inventory, D data);
    }
}
