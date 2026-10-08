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
        //#if MC >= 1.20.5
        return new ExtendedScreenHandlerType<>(factory::create, codec);
        //#else
        //$ // Before 1.20.5 the opening data is read from the packet buffer by the type itself.
        //$ return new ExtendedScreenHandlerType<>((id, inventory, buffer) -> factory.create(id, inventory, codec.decode(buffer)));
        //#endif
    }

    /** Opens a menu of a {@link #menuType}, sending {@code data} for its client side. */
    public static <D> void openMenu(ServerPlayer player, Component title, D data,
            StreamCodec<? super RegistryFriendlyByteBuf, D> codec, MenuConstructor menu) {
        //#if MC >= 1.20.5
        player.openMenu(new ExtendedScreenHandlerFactory<D>() {
        //#else
        //$ player.openMenu(new ExtendedScreenHandlerFactory() {
        //#endif
            //#if MC >= 1.20.5
            @Override public D getScreenOpeningData(ServerPlayer ignored) { return data; }
            //#else
            //$ @Override public void writeScreenOpeningData(ServerPlayer ignored, RegistryFriendlyByteBuf buffer) { codec.encode(buffer, data); }
            //#endif
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
