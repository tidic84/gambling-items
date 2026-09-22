package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.block.GameStationBlock;
import dev.gamblingitems.fabric.block.GameTableBlock;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.battle.BattleMenu;
import dev.gamblingitems.fabric.battle.BattleSetup;
import dev.gamblingitems.fabric.bingo.BingoMenu;
import dev.gamblingitems.fabric.bingo.BingoSetup;
import dev.gamblingitems.fabric.blackjack.BlackjackMenu;
import dev.gamblingitems.fabric.blackjack.BlackjackSetup;
import dev.gamblingitems.fabric.cases.CaseMenu;
import dev.gamblingitems.fabric.cases.CaseSetup;
import dev.gamblingitems.fabric.crash.CrashMenu;
import dev.gamblingitems.fabric.crash.CrashSetup;
import dev.gamblingitems.fabric.roulette.RouletteMenu;
import dev.gamblingitems.fabric.roulette.RouletteSetup;
import dev.gamblingitems.fabric.slots.SlotMenu;
import dev.gamblingitems.fabric.slots.SlotSetup;
import dev.gamblingitems.core.cases.CaseRarity;
import dev.gamblingitems.fabric.item.KeyItem;
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
    /** One key per rarity: the only price a case ever asks for. */
    private static final java.util.Map<CaseRarity, Item> KEYS = keys();
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
    public static final GameStationBlock BLACKJACK_STATION = station("blackjack_station", GameMode.BLACKJACK);
    public static final Item BLACKJACK_STATION_ITEM = stationItem("blackjack_station", BLACKJACK_STATION);
    public static final GameStationBlock BATTLE_STATION = station("battle_station", GameMode.CASE_BATTLE);
    public static final Item BATTLE_STATION_ITEM = stationItem("battle_station", BATTLE_STATION);
    public static final GameStationBlock BINGO_STATION = station("bingo_station", GameMode.BINGO);
    public static final Item BINGO_STATION_ITEM = stationItem("bingo_station", BINGO_STATION);
    /** Tables: the same games, played on a felt you stand at instead of a monitor on a wall. */
    public static final GameTableBlock BLACKJACK_TABLE = table("blackjack_table", GameMode.BLACKJACK);
    public static final Item BLACKJACK_TABLE_ITEM = tableItem("blackjack_table", BLACKJACK_TABLE);
    public static final GameTableBlock ROULETTE_TABLE = table("roulette_table", GameMode.ROULETTE);
    public static final Item ROULETTE_TABLE_ITEM = tableItem("roulette_table", ROULETTE_TABLE);
    public static final GameTableBlock BINGO_TABLE = table("bingo_table", GameMode.BINGO);
    public static final Item BINGO_TABLE_ITEM = tableItem("bingo_table", BINGO_TABLE);
    /** A cabinet: one block wide, two high, played standing in front of it. */
    public static final SlotMachineBlock SLOT_MACHINE = Registry.register(BuiltInRegistries.BLOCK,
            id("slot_machine"), new SlotMachineBlock(GameMode.SLOT_MACHINE,
                    BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final Item SLOT_MACHINE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            id("slot_machine"), new BlockItem(SLOT_MACHINE, new Item.Properties()));
    // The identifier of the first station is kept so existing worlds still read their block entities.
    public static final BlockEntityType<GameStationEntity> STATION_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("upgrade_station"),
            FabricBlockEntityTypeBuilder.create(GameStationEntity::new, UPGRADE_STATION, TRADE_UP_STATION,
                    CASE_STATION, CRASH_STATION, ROULETTE_STATION, BLACKJACK_STATION,
                    BATTLE_STATION, BINGO_STATION, BLACKJACK_TABLE, ROULETTE_TABLE,
                    BINGO_TABLE, SLOT_MACHINE).build());
    public static final ExtendedScreenHandlerType<UpgradeMenu, UpgradeSetup> UPGRADER_MENU = Registry.register(
            BuiltInRegistries.MENU, id("upgrader"), new ExtendedScreenHandlerType<>(UpgradeMenu::new, UpgradeSetup.CODEC));
    public static final ExtendedScreenHandlerType<TradeUpMenu, TradeUpSetup> TRADE_UP_MENU = Registry.register(
            BuiltInRegistries.MENU, id("trade_up"), new ExtendedScreenHandlerType<>(TradeUpMenu::new, TradeUpSetup.CODEC));
    public static final ExtendedScreenHandlerType<CaseMenu, CaseSetup> CASE_MENU = Registry.register(
            BuiltInRegistries.MENU, id("case_opening"), new ExtendedScreenHandlerType<>(CaseMenu::new, CaseSetup.CODEC));
    public static final ExtendedScreenHandlerType<CrashMenu, CrashSetup> CRASH_MENU = Registry.register(
            BuiltInRegistries.MENU, id("crash"), new ExtendedScreenHandlerType<>(CrashMenu::new, CrashSetup.CODEC));
    public static final ExtendedScreenHandlerType<RouletteMenu, RouletteSetup> ROULETTE_MENU =
            Registry.register(BuiltInRegistries.MENU, id("roulette"),
                    new ExtendedScreenHandlerType<>(RouletteMenu::new, RouletteSetup.CODEC));
    public static final ExtendedScreenHandlerType<BlackjackMenu, BlackjackSetup> BLACKJACK_MENU =
            Registry.register(BuiltInRegistries.MENU, id("blackjack"),
                    new ExtendedScreenHandlerType<>(BlackjackMenu::new, BlackjackSetup.CODEC));
    public static final ExtendedScreenHandlerType<BattleMenu, BattleSetup> BATTLE_MENU =
            Registry.register(BuiltInRegistries.MENU, id("case_battle"),
                    new ExtendedScreenHandlerType<>(BattleMenu::new, BattleSetup.CODEC));
    public static final ExtendedScreenHandlerType<BingoMenu, BingoSetup> BINGO_MENU =
            Registry.register(BuiltInRegistries.MENU, id("bingo"),
                    new ExtendedScreenHandlerType<>(BingoMenu::new, BingoSetup.CODEC));
    public static final ExtendedScreenHandlerType<SlotMenu, SlotSetup> SLOT_MENU =
            Registry.register(BuiltInRegistries.MENU, id("slot_machine"),
                    new ExtendedScreenHandlerType<>(SlotMenu::new, SlotSetup.CODEC));
    public static final MenuType<HubMenu> HUB_MENU = Registry.register(BuiltInRegistries.MENU, id("hub"),
            new MenuType<>(HubMenu::new, FeatureFlags.VANILLA_SET));

    private ModContent() {}

    public static Item key(CaseRarity rarity) { return KEYS.get(rarity); }

    private static java.util.Map<CaseRarity, Item> keys() {
        var keys = new java.util.EnumMap<CaseRarity, Item>(CaseRarity.class);
        for (CaseRarity rarity : CaseRarity.values()) {
            keys.put(rarity, Registry.register(BuiltInRegistries.ITEM, id(rarity.keyPath()),
                    new KeyItem(rarity, new Item.Properties().stacksTo(16))));
        }
        return keys;
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gamblingitems", path); }

    private static GameStationBlock station(String path, GameMode mode) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), new GameStationBlock(mode,
                BlockBehaviour.Properties.of().strength(3.5F).sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    }

    private static GameTableBlock table(String path, GameMode mode) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), new GameTableBlock(mode,
                BlockBehaviour.Properties.of().strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    }

    private static Item tableItem(String path, GameTableBlock block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, new Item.Properties()));
    }

    private static Item stationItem(String path, GameStationBlock block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, new Item.Properties()));
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(entries -> {
            entries.accept(TERMINAL);
            for (CaseRarity rarity : CaseRarity.values()) entries.accept(key(rarity));
            entries.accept(UPGRADE_STATION_ITEM);
            entries.accept(TRADE_UP_STATION_ITEM);
            entries.accept(CASE_STATION_ITEM);
            entries.accept(CRASH_STATION_ITEM);
            entries.accept(ROULETTE_STATION_ITEM);
            entries.accept(BLACKJACK_STATION_ITEM);
            entries.accept(BATTLE_STATION_ITEM);
            entries.accept(BLACKJACK_TABLE_ITEM);
            entries.accept(ROULETTE_TABLE_ITEM);
            entries.accept(BINGO_STATION_ITEM);
            entries.accept(BINGO_TABLE_ITEM);
            entries.accept(SLOT_MACHINE_ITEM);
        });
    }
}
