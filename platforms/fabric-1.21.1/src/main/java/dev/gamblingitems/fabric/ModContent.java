package dev.gamblingitems.fabric;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.arcade.ArcadeSettings;
import dev.gamblingitems.fabric.arcade.CashierBlock;
import dev.gamblingitems.fabric.arcade.CashierMenu;
import dev.gamblingitems.fabric.arcade.CashierSetup;
import dev.gamblingitems.fabric.arcade.DecorBlock;
import dev.gamblingitems.fabric.arcade.NeonBlock;
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
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
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
    /** The prize machines stand in the same cabinet as a slot machine, each with its own front. */
    public static final SlotMachineBlock LUCKY_WHEEL = machine("lucky_wheel", GameMode.LUCKY_WHEEL);
    public static final Item LUCKY_WHEEL_ITEM = machineItem("lucky_wheel", LUCKY_WHEEL);
    public static final SlotMachineBlock COIN_PUSHER = machine("coin_pusher", GameMode.COIN_PUSHER);
    public static final Item COIN_PUSHER_ITEM = machineItem("coin_pusher", COIN_PUSHER);
    public static final SlotMachineBlock PRIZE_CLAW = machine("prize_claw", GameMode.PRIZE_CLAW);
    public static final Item PRIZE_CLAW_ITEM = machineItem("prize_claw", PRIZE_CLAW);
    public static final SlotMachineBlock PACHINKO = machine("pachinko", GameMode.PACHINKO);
    public static final Item PACHINKO_ITEM = machineItem("pachinko", PACHINKO);
    public static final SlotMachineBlock CAPSULE_GACHA = machine("capsule_gacha", GameMode.CAPSULE_GACHA);
    public static final Item CAPSULE_GACHA_ITEM = machineItem("capsule_gacha", CAPSULE_GACHA);
    public static final SlotMachineBlock HIGH_STAKES = machine("high_stakes", GameMode.HIGH_STAKES);
    public static final Item HIGH_STAKES_ITEM = machineItem("high_stakes", HIGH_STAKES);
    /** The wide machines take two columns: Plinko, the Big Six wheel and the horse race. */
    public static final SlotMachineBlock PLINKO = machine("plinko", GameMode.PLINKO);
    public static final Item PLINKO_ITEM = machineItem("plinko", PLINKO);
    public static final SlotMachineBlock BIG_SIX = machine("big_six", GameMode.BIG_SIX);
    public static final Item BIG_SIX_ITEM = machineItem("big_six", BIG_SIX);
    public static final SlotMachineBlock VIDEO_POKER = machine("video_poker", GameMode.VIDEO_POKER);
    public static final Item VIDEO_POKER_ITEM = machineItem("video_poker", VIDEO_POKER);
    public static final SlotMachineBlock HORSE_RACE = machine("horse_race", GameMode.HORSE_RACE);
    public static final Item HORSE_RACE_ITEM = machineItem("horse_race", HORSE_RACE);
    /** Casino chips: items of value like any other, worth the configured chip value times their face. */
    private static final java.util.Map<Integer, Item> CHIPS = chips();
    public static final CashierBlock CASHIER = Registry.register(BuiltInRegistries.BLOCK, id("cashier"),
            new CashierBlock(blockProperties("cashier").strength(2.5F).sound(SoundType.WOOD).noOcclusion()));
    public static final Item CASHIER_ITEM = blockItem("cashier", CASHIER);
    public static final DecorBlock LEADERBOARD = Registry.register(BuiltInRegistries.BLOCK, id("leaderboard"),
            new DecorBlock(DecorBlock.Kind.LEADERBOARD, blockProperties("leaderboard").strength(1.5F)
                    .sound(SoundType.METAL).noOcclusion().lightLevel(state -> 10)));
    public static final Item LEADERBOARD_ITEM = blockItem("leaderboard", LEADERBOARD);
    public static final DecorBlock CROUPIER = Registry.register(BuiltInRegistries.BLOCK, id("croupier"),
            new DecorBlock(DecorBlock.Kind.CROUPIER, blockProperties("croupier").strength(1.5F)
                    .sound(SoundType.WOOL).noOcclusion()));
    public static final Item CROUPIER_ITEM = blockItem("croupier", CROUPIER);
    public static final NeonBlock NEON_TUBE = Registry.register(BuiltInRegistries.BLOCK, id("neon_tube"),
            new NeonBlock(blockProperties("neon_tube").strength(0.3F).sound(SoundType.GLASS).noOcclusion()
                    .lightLevel(state -> 15)));
    public static final Item NEON_TUBE_ITEM = blockItem("neon_tube", NEON_TUBE);
    public static final Block CASINO_CARPET = carpet("casino_carpet");
    public static final Item CASINO_CARPET_ITEM = blockItem("casino_carpet", CASINO_CARPET);
    public static final Block CASINO_CARPET_BLUE = carpet("casino_carpet_blue");
    public static final Item CASINO_CARPET_BLUE_ITEM = blockItem("casino_carpet_blue", CASINO_CARPET_BLUE);
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
                    BINGO_TABLE, SLOT_MACHINE, LUCKY_WHEEL, COIN_PUSHER, PRIZE_CLAW, PACHINKO,
                    CAPSULE_GACHA, HIGH_STAKES, PLINKO, BIG_SIX, VIDEO_POKER, HORSE_RACE, LEADERBOARD, CROUPIER)
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
    /** One window for every prize machine; the machine it plays travels in its setup. */
    public static final MenuType<dev.gamblingitems.fabric.machines.MachineMenu> MACHINE_MENU =
            Registry.register(BuiltInRegistries.MENU, id("prize_machine"),
                    Platform.menuType(
                    (id, inventory, opening) -> new dev.gamblingitems.fabric.machines.MachineMenu(id, inventory, opening.setup()),
                    Opening.codec(dev.gamblingitems.fabric.machines.MachineSetup.CODEC)));
    public static final MenuType<CashierMenu> CASHIER_MENU =
            Registry.register(BuiltInRegistries.MENU, id("cashier"),
                    Platform.menuType(
                    (id, inventory, opening) -> new CashierMenu(id, inventory, opening.setup()),
                    Opening.codec(CashierSetup.CODEC)));
    /** Everything of the mod in one tab of its own. */
    public static final CreativeModeTab TAB = Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, id("casino"),
            Platform.creativeTab(Component.translatable("itemGroup.gamblingitems.casino"),
                    () -> new ItemStack(SLOT_MACHINE_ITEM), ModContent::creativeEntries));

    private ModContent() {}

    public static Item key(CaseRarity rarity) { return KEYS.get(rarity); }

    /** The chip of a face value of {@link ArcadeSettings#CHIPS}. */
    public static Item chip(int face) { return CHIPS.get(face); }

    private static java.util.Map<Integer, Item> chips() {
        var chips = new java.util.LinkedHashMap<Integer, Item>();
        for (int face : ArcadeSettings.CHIPS) {
            chips.put(face, Registry.register(BuiltInRegistries.ITEM, id("chip_" + face),
                    new Item(itemProperties("chip_" + face))));
        }
        return chips;
    }

    private static Block carpet(String path) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path),
                new CarpetBlock(blockProperties(path).strength(0.1F).sound(SoundType.WOOL)));
    }

    private static Item blockItem(String path, Block block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, blockItemProperties(path)));
    }

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

    private static SlotMachineBlock machine(String path, GameMode mode) {
        return Registry.register(BuiltInRegistries.BLOCK, id(path), new SlotMachineBlock(mode,
                blockProperties(path).strength(3.5F).sound(SoundType.METAL).noOcclusion()
                        .pushReaction(net.minecraft.world.level.material.PushReaction.BLOCK)));
    }

    private static Item machineItem(String path, SlotMachineBlock block) {
        return Registry.register(BuiltInRegistries.ITEM, id(path), new BlockItem(block, blockItemProperties(path)));
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

    /** What the mod's tab lists, in order. */
    public static java.util.List<Item> creativeEntries() {
        var entries = new java.util.ArrayList<Item>();
        entries.add(TERMINAL);
        entries.add(POCKET_CASINO);
        for (CaseRarity rarity : CaseRarity.values()) entries.add(key(rarity));
        entries.addAll(CHIPS.values());
        entries.addAll(java.util.List.of(UPGRADE_STATION_ITEM, TRADE_UP_STATION_ITEM, CASE_STATION_ITEM,
                CRASH_STATION_ITEM, ROULETTE_STATION_ITEM, BLACKJACK_STATION_ITEM, BATTLE_STATION_ITEM,
                BLACKJACK_TABLE_ITEM, ROULETTE_TABLE_ITEM, BINGO_STATION_ITEM, BINGO_TABLE_ITEM, SLOT_MACHINE_ITEM,
                LUCKY_WHEEL_ITEM, COIN_PUSHER_ITEM, PRIZE_CLAW_ITEM, PACHINKO_ITEM, CAPSULE_GACHA_ITEM, HIGH_STAKES_ITEM,
                VIDEO_POKER_ITEM, PLINKO_ITEM, BIG_SIX_ITEM, HORSE_RACE_ITEM, CASHIER_ITEM, LEADERBOARD_ITEM,
                CROUPIER_ITEM, NEON_TUBE_ITEM, CASINO_CARPET_ITEM, CASINO_CARPET_BLUE_ITEM));
        return entries;
    }
}
