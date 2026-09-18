package dev.gamblingitems.fabric.vault;

import java.util.EnumMap;
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

/** Per-player stakes and unclaimed rewards; shared by portable and placed menus in every dimension. */
public final class PlayerVaults extends SavedData {
    public static final int SCHEMA_VERSION = 2;
    private final Map<UUID, Map<VaultSection, SimpleContainer>> vaults = new HashMap<>();

    public static PlayerVaults get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerVaults::new, PlayerVaults::load, null), "gamblingitems_vaults");
    }

    public SimpleContainer forPlayer(UUID player, VaultSection section) {
        return vaults.computeIfAbsent(player, key -> new EnumMap<>(VaultSection.class))
                .computeIfAbsent(section, key -> {
                    SimpleContainer container = new SimpleContainer(key.size());
                    container.addListener(ignored -> setDirty());
                    return container;
                });
    }

    public static PlayerVaults load(CompoundTag root, HolderLookup.Provider registries) {
        int schema = root.getInt("schemaVersion");
        if (schema < 1 || schema > SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported gamblingitems vault schema: " + schema);
        }
        PlayerVaults storage = new PlayerVaults();
        ListTag players = root.getList("players", Tag.TAG_COMPOUND);
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = players.getCompound(index);
            UUID player = entry.getUUID("player");
            if (schema == 1) {
                // The first format only stored the upgrader stake and its reward.
                readItems(entry, storage.forPlayer(player, VaultSection.UPGRADER), registries);
                continue;
            }
            ListTag sections = entry.getList("sections", Tag.TAG_COMPOUND);
            for (int position = 0; position < sections.size(); position++) {
                CompoundTag section = sections.getCompound(position);
                readItems(section, storage.forPlayer(player,
                        VaultSection.fromId(section.getString("section"))), registries);
            }
        }
        storage.setDirty(schema != SCHEMA_VERSION);
        return storage;
    }

    private static void readItems(CompoundTag tag, SimpleContainer container, HolderLookup.Provider registries) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            String key = "slot" + slot;
            if (!tag.contains(key)) continue;
            container.setItem(slot, ItemStack.parse(registries, tag.get(key))
                    .orElseThrow(() -> new IllegalStateException("Unreadable item in gamblingitems vault")));
        }
    }

    @Override public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        root.putInt("schemaVersion", SCHEMA_VERSION);
        ListTag players = new ListTag();
        vaults.forEach((player, sections) -> {
            ListTag saved = new ListTag();
            sections.forEach((section, container) -> {
                if (container.isEmpty()) return;
                CompoundTag tag = new CompoundTag();
                tag.putString("section", section.id());
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (!container.getItem(slot).isEmpty()) {
                        tag.put("slot" + slot, container.getItem(slot).save(registries));
                    }
                }
                saved.add(tag);
            });
            if (saved.isEmpty()) return;
            CompoundTag entry = new CompoundTag();
            entry.putUUID("player", player);
            entry.put("sections", saved);
            players.add(entry);
        });
        root.put("players", players);
        return root;
    }
}
