package dev.gamblingitems.fabric.vault;

import dev.gamblingitems.fabric.Nbt;
import dev.gamblingitems.fabric.prediction.MarketRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What the casino remembers about its players beyond their stakes: the suspicion weighing on each of
 * them, who is banned and until when, and every prediction market with what its creators are owed.
 * It holds no item, only numbers and identifiers, so it is saved without the registries.
 */
public final class CasinoLedger extends SavedData {
    public static final int SCHEMA_VERSION = 1;

    /** The security file of one player. Times are wall-clock milliseconds, so a ban survives a restart. */
    public static final class Record {
        public int heat;
        public long heatAt;
        public long bannedUntil;
        public int caught;
        public int tricks;

        boolean empty() { return heat == 0 && bannedUntil == 0 && caught == 0 && tricks == 0; }
    }

    private final Map<UUID, Record> records = new HashMap<>();
    private final List<MarketRecord> markets = new ArrayList<>();
    private final Map<UUID, Long> credits = new HashMap<>();
    private int nextMarket = 1;

    //#if MC >= 26.1
    //$ private static final net.minecraft.resources.ResourceLocation LEDGER_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gamblingitems", "gamblingitems_ledger");
    //#elif MC >= 1.21.5
    //$ private static final String LEDGER_ID = "gamblingitems_ledger";
    //#endif
    //#if MC >= 1.21.11
    //$ private static final com.mojang.serialization.Codec<CasinoLedger> CODEC =
    //$         CompoundTag.CODEC.xmap(CasinoLedger::read, ledger -> ledger.write(new CompoundTag()));
    //$ private static final net.minecraft.world.level.saveddata.SavedDataType<CasinoLedger> TYPE =
    //$         new net.minecraft.world.level.saveddata.SavedDataType<>(LEDGER_ID, CasinoLedger::new, CODEC, null);
    //#elif MC >= 1.21.5
    //$ private static final net.minecraft.world.level.saveddata.SavedDataType<CasinoLedger> TYPE =
    //$         new net.minecraft.world.level.saveddata.SavedDataType<>(LEDGER_ID, context -> new CasinoLedger(),
    //$                 context -> CompoundTag.CODEC.xmap(CasinoLedger::read, ledger -> ledger.write(new CompoundTag())), null);
    //#endif

    public static CasinoLedger get(MinecraftServer server) {
        //#if MC >= 26.1
        //$ return server.getDataStorage().computeIfAbsent(TYPE);
        //#elif MC >= 1.21.5
        //$ return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        //#elif MC >= 1.20.2
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(CasinoLedger::new, CasinoLedger::load, null), "gamblingitems_ledger");
        //#else
        //$ return server.overworld().getDataStorage().computeIfAbsent(CasinoLedger::load, CasinoLedger::new, "gamblingitems_ledger");
        //#endif
    }

    /** The file of a player, opened on first use. */
    public Record record(UUID player) {
        setDirty();
        return records.computeIfAbsent(player, key -> new Record());
    }

    /** The file of a player when there is one, without opening it. */
    public Record find(UUID player) { return records.get(player); }

    public List<MarketRecord> markets() { return markets; }

    public MarketRecord market(int id) {
        for (MarketRecord market : markets) if (market.id() == id) return market;
        return null;
    }

    public int nextMarketId() {
        setDirty();
        return nextMarket++;
    }

    public long credit(UUID player) { return credits.getOrDefault(player, 0L); }

    public void addCredit(UUID player, long amount) {
        if (player == null || amount <= 0) return;
        credits.merge(player, amount, Long::sum);
        setDirty();
    }

    public void takeCredit(UUID player, long amount) {
        long left = credit(player) - amount;
        if (left > 0) credits.put(player, left);
        else credits.remove(player);
        setDirty();
    }

    //#if MC >= 1.20.5
    public static CasinoLedger load(CompoundTag root, HolderLookup.Provider registries) { return read(root); }
    //#else
    //$ public static CasinoLedger load(CompoundTag root) { return read(root); }
    //#endif

    private static CasinoLedger read(CompoundTag root) {
        int schema = Nbt.integer(root, "schemaVersion", 0);
        if (schema < 1 || schema > SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported gamblingitems ledger schema: " + schema);
        }
        CasinoLedger ledger = new CasinoLedger();
        ListTag players = Nbt.compounds(root, "players");
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = Nbt.compound(players, index);
            Record record = new Record();
            record.heat = Nbt.integer(entry, "heat", 0);
            record.heatAt = Nbt.longValue(entry, "heatAt");
            record.bannedUntil = Nbt.longValue(entry, "bannedUntil");
            record.caught = Nbt.integer(entry, "caught", 0);
            record.tricks = Nbt.integer(entry, "tricks", 0);
            ledger.records.put(Nbt.uuid(entry, "player"), record);
        }
        ListTag markets = Nbt.compounds(root, "markets");
        for (int index = 0; index < markets.size(); index++) {
            ledger.markets.add(MarketRecord.load(Nbt.compound(markets, index)));
        }
        ListTag credits = Nbt.compounds(root, "credits");
        for (int index = 0; index < credits.size(); index++) {
            CompoundTag entry = Nbt.compound(credits, index);
            ledger.credits.put(Nbt.uuid(entry, "player"), Nbt.longValue(entry, "amount"));
        }
        ledger.nextMarket = Math.max(1, Nbt.integer(root, "nextMarket", 1));
        return ledger;
    }

    //#if MC < 1.21.5
    @Override
    //#endif
    //#if MC >= 1.20.5
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) { return write(root); }
    //#else
    //$ public CompoundTag save(CompoundTag root) { return write(root); }
    //#endif

    private CompoundTag write(CompoundTag root) {
        root.putInt("schemaVersion", SCHEMA_VERSION);
        ListTag players = new ListTag();
        records.forEach((player, record) -> {
            if (record.empty()) return;
            CompoundTag entry = new CompoundTag();
            Nbt.putUuid(entry, "player", player);
            entry.putInt("heat", record.heat);
            entry.putLong("heatAt", record.heatAt);
            entry.putLong("bannedUntil", record.bannedUntil);
            entry.putInt("caught", record.caught);
            entry.putInt("tricks", record.tricks);
            players.add(entry);
        });
        root.put("players", players);
        ListTag saved = new ListTag();
        for (MarketRecord market : markets) saved.add(market.save());
        root.put("markets", saved);
        ListTag owed = new ListTag();
        credits.forEach((player, amount) -> {
            CompoundTag entry = new CompoundTag();
            Nbt.putUuid(entry, "player", player);
            entry.putLong("amount", amount);
            owed.add(entry);
        });
        root.put("credits", owed);
        root.putInt("nextMarket", nextMarket);
        return root;
    }
}
