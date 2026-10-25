package dev.gamblingitems.fabric.platform;

import java.nio.file.Path;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuConstructor;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;

/**
 * NeoForge version of the loader calls the shared game code makes. It replaces the Fabric class
 * of the same name, which this build leaves out, and must keep the same signatures.
 */
public final class Platform {
    private Platform() {}

    public static Path configDir() { return FMLPaths.CONFIGDIR.get(); }

    /** A menu whose client side is built from data the server sends when it opens. */
    public static <M extends AbstractContainerMenu, D> MenuType<M> menuType(
            MenuFactory<M, D> factory, StreamCodec<? super RegistryFriendlyByteBuf, D> codec) {
        return IMenuTypeExtension.create((id, inventory, buffer) -> factory.create(id, inventory, codec.decode(buffer)));
    }

    /** Opens a menu of a {@link #menuType}, sending {@code data} for its client side. */
    public static <D> void openMenu(ServerPlayer player, Component title, D data,
            StreamCodec<? super RegistryFriendlyByteBuf, D> codec, MenuConstructor menu) {
        player.openMenu(new SimpleMenuProvider(menu, title), buffer -> codec.encode(buffer, data));
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
