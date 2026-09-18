package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.cases.CaseSetup;
import dev.gamblingitems.fabric.crash.CrashMenu;
import dev.gamblingitems.fabric.crash.CrashSetup;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import dev.gamblingitems.fabric.roulette.RouletteSettings;
import dev.gamblingitems.fabric.item.TerminalItem;
import dev.gamblingitems.fabric.menu.HubMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpSetup;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import dev.gamblingitems.fabric.upgrade.UpgradeSetup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModContent {
    public static final Item TERMINAL = Registry.register(BuiltInRegistries.ITEM, id("terminal"),
            new TerminalItem(new Item.Properties().stacksTo(1)));
    public static final GameStationBlock UPGRADE_STATION = station("upgrade_station", GameMode.UPGRADER);
    public static final Item UPGRADE_STATION_ITEM = stationItem("upgrade_station", UPGRADE_STATION);
    public static final GameStationBlock TRADE_UP_STATION = station("trade_up_station", GameMode.TRADE_UP);
    public static final Item TRADE_UP_STATION_ITEM = stationItem("trade_up_station", TRADE_UP_STATION);
    public static final GameStationBlock CASE_STATION = station("case_station", GameMode.CASE_OPENING);
    public static final Item CASE_STATION_ITEM = stationItem("case_station", CASE_STATION);
    public static final GameStationBlock CRASH_STATION = station("crash_station", GameMode.CRASH);
    public static final Item CRASH_STATION_ITEM = stationItem("crash_station", CRASH_STATION);
    public static final GameStationBlock ROULETTE_STATION = station("roulette_station", GameMode.ROULETTE);
    public static final Item ROULETTE_STATION_ITEM = stationItem("roulette_station", ROULETTE_STATION);
    // The identifier of the first station is kept so existing worlds still read their block entities.
    public static final BlockEntityType<GameStationEntity> STATION_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("upgrade_station"),
            FabricBlockEntityTypeBuilder.create(GameStationEntity::new, UPGRADE_STATION, TRADE_UP_STATION,
                    CASE_STATION, CRASH_STATION, ROULETTE_STATION).build());
    public static final ExtendedScreenHandlerType<UpgradeMenu, UpgradeSetup> UPGRADER_MENU = Registry.register(
            BuiltInRegistries.MENU, id("upgrader"), new ExtendedScreenHandlerType<>(UpgradeMenu::new, UpgradeSetup.CODEC));
    public static final ExtendedScreenHandlerType<TradeUpMenu, TradeUpSetup> TRADE_UP_MENU = Registry.register(
            BuiltInRegistries.MENU, id("trade_up"), new ExtendedScreenHandlerType<>(TradeUpMenu::new, TradeUpSetup.CODEC));
    public static final ExtendedScreenHandlerType<CaseMenu, CaseSetup> CASE_MENU = Registry.register(
            BuiltInRegistries.MENU, id("case_opening"), new ExtendedScreenHandlerType<>(CaseMenu::new, CaseSetup.CODEC));
    public static final ExtendedScreenHandlerType<CrashMenu, CrashSetup> CRASH_MENU = Registry.register(
            BuiltInRegistries.MENU, id("crash"), new ExtendedScreenHandlerType<>(CrashMenu::new, CrashSetup.CODEC));
    public static final ExtendedScreenHandlerType<RouletteMenu, RouletteSettings> ROULETTE_MENU =
            Registry.register(BuiltInRegistries.MENU, id("roulette"),
                    new ExtendedScreenHandlerType<>(RouletteMenu::new, RouletteSettings.CODEC));
    public static final MenuType<HubMenu> HUB_MENU = Registry.register(BuiltInRegistries.MENU, id("hub"),
            new MenuType<>(HubMenu::new, FeatureFlags.VANILLA_SET));

    private ModContent() {}

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gamblingitems", path); }

    private static GameStationBlock station(String path, GameMode mode) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), new GameStationBlock(mode,
                BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    }

    private static Item stationItem(String path, GameStationBlock block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, new Item.Properties()));
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(TERMINAL);
            entries.accept(UPGRADE_STATION_ITEM);
            entries.accept(TRADE_UP_STATION_ITEM);
            entries.accept(CASE_STATION_ITEM);
            entries.accept(CRASH_STATION_ITEM);
            entries.accept(ROULETTE_STATION_ITEM);
        });
    }
}
