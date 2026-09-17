package dev.gamblingitems.fabric;

import dev.gamblingitems.fabric.block.UpgradeStationBlock;
import dev.gamblingitems.fabric.block.UpgradeStationEntity;
import dev.gamblingitems.fabric.item.TerminalItem;
import dev.gamblingitems.fabric.upgrade.UpgradeCatalog;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModContent {
    public static final Item TERMINAL = Registry.register(BuiltInRegistries.ITEM, id("terminal"),
            new TerminalItem(new Item.Properties().stacksTo(1)));
    public static final UpgradeStationBlock STATION = Registry.register(BuiltInRegistries.BLOCK, id("upgrade_station"),
            new UpgradeStationBlock(BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).noOcclusion()));
    public static final Item STATION_ITEM = Registry.register(BuiltInRegistries.ITEM, id("upgrade_station"),
            new BlockItem(STATION, new Item.Properties()));
    public static final BlockEntityType<UpgradeStationEntity> STATION_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("upgrade_station"),
            FabricBlockEntityTypeBuilder.create(UpgradeStationEntity::new, STATION).build());
    public static final ExtendedScreenHandlerType<UpgradeMenu, UpgradeCatalog> UPGRADER_MENU = Registry.register(
            BuiltInRegistries.MENU, id("upgrader"), new ExtendedScreenHandlerType<>(UpgradeMenu::new, UpgradeCatalog.CODEC));

    private ModContent() {}
    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gamblingitems", path); }
    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(TERMINAL);
            entries.accept(STATION_ITEM);
        });
    }
}

