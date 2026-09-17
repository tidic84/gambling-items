package dev.gamblingitems.fabric.upgrade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player input and unclaimed reward; shared by portable and block menus in every dimension. */
public final class PlayerVaults extends SavedData {
    private final Map<UUID, SimpleContainer> vaults = new HashMap<>();

    public static PlayerVaults get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerVaults::new, PlayerVaults::load, null), "gamblingitems_vaults");
    }

    public SimpleContainer forPlayer(UUID player) {
        return vaults.computeIfAbsent(player, key -> {
            SimpleContainer container = new SimpleContainer(2);
            container.addListener(ignored -> setDirty());
            return container;
        });
    }

    public static PlayerVaults load(CompoundTag root, HolderLookup.Provider registries) {
        int schema = root.getInt("schemaVersion");
        if (schema != 1) throw new IllegalStateException("Unsupported gamblingitems vault schema: " + schema);
        PlayerVaults storage = new PlayerVaults();
        ListTag players = root.getList("players", Tag.TAG_COMPOUND);
        for (int i = 0; i < players.size(); i++) {
            CompoundTag entry = players.getCompound(i);
            SimpleContainer vault = storage.forPlayer(entry.getUUID("player"));
            for (int slot = 0; slot < 2; slot++) {
                String key = "slot" + slot;
                if (entry.contains(key)) {
                    ItemStack stack = ItemStack.parse(registries, entry.get(key))
                            .orElseThrow(() -> new IllegalStateException("Unreadable item in gamblingitems vault"));
                    vault.setItem(slot, stack);
                }
            }
        }
        storage.setDirty(false);
        return storage;
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("schemaVersion", 1);
        ListTag players = new ListTag();
        vaults.forEach((player, vault) -> {
            if (vault.isEmpty()) return;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            for (int slot = 0; slot < 2; slot++) {
                if (!vault.getItem(slot).isEmpty()) entry.put("slot" + slot, vault.getItem(slot).save(registries));
            }
            players.add(entry);
        });
        root.put("players", players);
        return root;
    }
}

