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
import dev.gamblingitems.fabric.menu.Opening;
import dev.gamblingitems.fabric.platform.Platform;
import dev.gamblingitems.fabric.tradeup.TradeUpMenu;
import dev.gamblingitems.fabric.tradeup.TradeUpSetup;
import dev.gamblingitems.fabric.upgrade.UpgradeMenu;
import dev.gamblingitems.fabric.upgrade.UpgradeSetup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModContent {
    public static final Item TERMINAL = Registry.register(BuiltInRegistries.ITEM, id("terminal"),
            new TerminalItem(itemProperties("terminal").stacksTo(1)));
    public static final Item POCKET_CASINO = Registry.register(BuiltInRegistries.ITEM, id("pocket_casino"),
            new dev.gamblingitems.fabric.item.CasinoItem(itemProperties("pocket_casino").stacksTo(1)));
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
                    blockProperties("slot_machine").strength(3.5F).sound(SoundType.METAL).noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    public static final Item SLOT_MACHINE_ITEM = Registry.register(BuiltInRegistries.ITEM,
            id("slot_machine"), new BlockItem(SLOT_MACHINE, blockItemProperties("slot_machine")));
    // The identifier of the first station is kept so existing worlds still read their block entities.
    public static final BlockEntityType<GameStationEntity> STATION_ENTITY = Registry.register(
            BuiltInRegistries.BLOCK_ENTITY_TYPE, id("upgrade_station"),
            //#if MC >= 1.21.2 && LOADER == fabric
            //$ net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder.create(GameStationEntity::new,
            //#elif MC >= 1.21.2
            //$ new BlockEntityType<>(GameStationEntity::new, java.util.Set.of(
            //#else
            BlockEntityType.Builder.of(GameStationEntity::new,
            //#endif
                    UPGRADE_STATION, TRADE_UP_STATION,
                    CASE_STATION, CRASH_STATION, ROULETTE_STATION, BLACKJACK_STATION,
                    BATTLE_STATION, BINGO_STATION, BLACKJACK_TABLE, ROULETTE_TABLE,
                    BINGO_TABLE, SLOT_MACHINE)
            //#if MC >= 1.21.2 && LOADER == fabric
            //$ .build());
            //#elif MC >= 1.21.2
            //$ ));
            //#else
            .build(null));
            //#endif
    public static final MenuType<UpgradeMenu> UPGRADER_MENU = Registry.register(
            BuiltInRegistries.MENU, id("upgrader"), Platform.menuType(
                    (id, inventory, opening) -> new UpgradeMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(UpgradeSetup.CODEC)));
    public static final MenuType<TradeUpMenu> TRADE_UP_MENU = Registry.register(
            BuiltInRegistries.MENU, id("trade_up"), Platform.menuType(
                    (id, inventory, opening) -> new TradeUpMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(TradeUpSetup.CODEC)));
    public static final MenuType<CaseMenu> CASE_MENU = Registry.register(
            BuiltInRegistries.MENU, id("case_opening"), Platform.menuType(
                    (id, inventory, opening) -> new CaseMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(CaseSetup.CODEC)));
    public static final MenuType<CrashMenu> CRASH_MENU = Registry.register(
            BuiltInRegistries.MENU, id("crash"), Platform.menuType(
                    (id, inventory, opening) -> new CrashMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(CrashSetup.CODEC)));
    public static final MenuType<RouletteMenu> ROULETTE_MENU =
            Registry.register(BuiltInRegistries.MENU, id("roulette"),
                    Platform.menuType(
                    (id, inventory, opening) -> new RouletteMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(RouletteSetup.CODEC)));
    public static final MenuType<BlackjackMenu> BLACKJACK_MENU =
            Registry.register(BuiltInRegistries.MENU, id("blackjack"),
                    Platform.menuType(
                    (id, inventory, opening) -> new BlackjackMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(BlackjackSetup.CODEC)));
    public static final MenuType<BattleMenu> BATTLE_MENU =
            Registry.register(BuiltInRegistries.MENU, id("case_battle"),
                    Platform.menuType(
                    (id, inventory, opening) -> new BattleMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(BattleSetup.CODEC)));
    public static final MenuType<BingoMenu> BINGO_MENU =
            Registry.register(BuiltInRegistries.MENU, id("bingo"),
                    Platform.menuType(
                    (id, inventory, opening) -> new BingoMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(BingoSetup.CODEC)));
    public static final MenuType<SlotMenu> SLOT_MENU =
            Registry.register(BuiltInRegistries.MENU, id("slot_machine"),
                    Platform.menuType(
                    (id, inventory, opening) -> new SlotMenu(id, inventory, opening.setup(), opening.portable()),
                    Opening.codec(SlotSetup.CODEC)));

    private ModContent() {}

    public static Item key(CaseRarity rarity) { return KEYS.get(rarity); }

    private static java.util.Map<CaseRarity, Item> keys() {
        var keys = new java.util.EnumMap<CaseRarity, Item>(CaseRarity.class);
        for (CaseRarity rarity : CaseRarity.values()) {
            keys.put(rarity, Registry.register(BuiltInRegistries.ITEM, id(rarity.keyPath()),
                    new KeyItem(rarity, itemProperties(rarity.keyPath()).stacksTo(16))));
        }
        return keys;
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("gamblingitems", path); }

    private static GameStationBlock station(String path, GameMode mode) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), new GameStationBlock(mode,
                blockProperties(path).strength(3.5F).sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    }

    private static GameTableBlock table(String path, GameMode mode) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), new GameTableBlock(mode,
                blockProperties(path).strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    }

    private static Item tableItem(String path, GameTableBlock block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, blockItemProperties(path)));
    }

    private static Item stationItem(String path, GameStationBlock block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, blockItemProperties(path)));
    }

    /** Since 1.21.2 an item knows its own id from the moment it is built. */
    private static Item.Properties itemProperties(String path) {
        //#if MC >= 1.21.2
        //$ return new Item.Properties().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.ITEM, id(path)));
        //#else
        return new Item.Properties();
        //#endif
    }

    /** A block's item keeps the name of its block. */
    private static Item.Properties blockItemProperties(String path) {
        //#if MC >= 1.21.2
        //$ return itemProperties(path).useBlockDescriptionPrefix();
        //#else
        return itemProperties(path);
        //#endif
    }

    private static BlockBehaviour.Properties blockProperties(String path) {
        //#if MC >= 1.21.2
        //$ return BlockBehaviour.Properties.of().setId(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.BLOCK, id(path)));
        //#else
        return BlockBehaviour.Properties.of();
        //#endif
    }

    /** Loading this class registers everything above; each loader calls this at its registration time. */
    public static void initialize() {}

    /** What the functional blocks tab lists, in order. */
    public static java.util.List<Item> creativeEntries() {
        var entries = new java.util.ArrayList<Item>();
        entries.add(TERMINAL);
        entries.add(POCKET_CASINO);
        for (CaseRarity rarity : CaseRarity.values()) entries.add(key(rarity));
        entries.addAll(java.util.List.of(UPGRADE_STATION_ITEM, TRADE_UP_STATION_ITEM, CASE_STATION_ITEM,
                CRASH_STATION_ITEM, ROULETTE_STATION_ITEM, BLACKJACK_STATION_ITEM, BATTLE_STATION_ITEM,
                BLACKJACK_TABLE_ITEM, ROULETTE_TABLE_ITEM, BINGO_STATION_ITEM, BINGO_TABLE_ITEM, SLOT_MACHINE_ITEM));
        return entries;
    }
}
