package dev.gamblingitems.fabric.vault;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import dev.gamblingitems.fabric.Nbt;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;

/** Per-player stakes and unclaimed rewards; shared by portable and placed menus in every dimension. */
public final class PlayerVaults extends SavedData {
    public static final int SCHEMA_VERSION = 2;
    private final Map<UUID, Map<VaultSection, SimpleContainer>> vaults = new HashMap<>();

    //#if MC >= 26.1
    //$ private static final net.minecraft.resources.ResourceLocation VAULTS_ID = net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("gamblingitems", "gamblingitems_vaults");
    //#elif MC >= 1.21.5
    //$ private static final String VAULTS_ID = "gamblingitems_vaults";
    //#endif
    //#if MC >= 1.21.11
    //$ // Since 1.21.11 saved data is decoded with the registry-aware ops of the server; the NBT layout stays the same.
    //$ private static final com.mojang.serialization.Codec<PlayerVaults> CODEC = new com.mojang.serialization.Codec<>() {
    //$     @Override public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<PlayerVaults, T>> decode(
    //$             com.mojang.serialization.DynamicOps<T> ops, T input) {
    //$         var nbt = nbtOps(ops);
    //$         return com.mojang.serialization.DataResult.success(com.mojang.datafixers.util.Pair.of(
    //$                 read((CompoundTag) ops.convertTo(net.minecraft.nbt.NbtOps.INSTANCE, input), nbt), ops.empty()));
    //$     }
    //$     @Override public <T> com.mojang.serialization.DataResult<T> encode(PlayerVaults vaults,
    //$             com.mojang.serialization.DynamicOps<T> ops, T prefix) {
    //$         return com.mojang.serialization.DataResult.success(
    //$                 net.minecraft.nbt.NbtOps.INSTANCE.convertTo(ops, vaults.write(new CompoundTag(), nbtOps(ops))));
    //$     }
    //$ };
    //$ private static <T> com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> nbtOps(com.mojang.serialization.DynamicOps<T> ops) {
    //$     return ops instanceof net.minecraft.resources.RegistryOps<T> registry ? registry.withParent(net.minecraft.nbt.NbtOps.INSTANCE)
    //$             : net.minecraft.nbt.NbtOps.INSTANCE;
    //$ }
    //$ private static final net.minecraft.world.level.saveddata.SavedDataType<PlayerVaults> TYPE =
    //$         new net.minecraft.world.level.saveddata.SavedDataType<>(VAULTS_ID, PlayerVaults::new, CODEC, null);
    //#elif MC >= 1.21.5
    //$ // Saved data is read through a codec since 1.21.5; this one wraps the same NBT layout, so worlds keep their vaults.
    //$ private static final net.minecraft.world.level.saveddata.SavedDataType<PlayerVaults> TYPE =
    //$         new net.minecraft.world.level.saveddata.SavedDataType<>(VAULTS_ID, context -> new PlayerVaults(),
    //$                 context -> CompoundTag.CODEC.xmap(tag -> load(tag, context.levelOrThrow().registryAccess()),
    //$                         vaults -> vaults.save(new CompoundTag(), context.levelOrThrow().registryAccess())), null);
    //#endif

    public static PlayerVaults get(MinecraftServer server) {
        //#if MC >= 26.1
        //$ return server.getDataStorage().computeIfAbsent(TYPE);
        //#elif MC >= 1.21.5
        //$ return server.overworld().getDataStorage().computeIfAbsent(TYPE);
        //#elif MC >= 1.20.5
        return server.overworld().getDataStorage().computeIfAbsent(
                new Factory<>(PlayerVaults::new, PlayerVaults::load, null), "gamblingitems_vaults");
        //#elif MC >= 1.20.2
        //$ return server.overworld().getDataStorage().computeIfAbsent(
        //$         new Factory<>(PlayerVaults::new, PlayerVaults::load, null), "gamblingitems_vaults");
        //#else
        //$ return server.overworld().getDataStorage().computeIfAbsent(PlayerVaults::load, PlayerVaults::new, "gamblingitems_vaults");
        //#endif
    }

    public SimpleContainer forPlayer(UUID player, VaultSection section) {
        return vaults.computeIfAbsent(player, key -> new EnumMap<>(VaultSection.class))
                .computeIfAbsent(section, key -> {
                    // Any change to a vault marks the world data for saving.
                    return new SimpleContainer(key.size()) {
                        @Override public void setChanged() {
                            super.setChanged();
                            setDirty();
                        }
                    };
                });
    }

    //#if MC >= 1.20.5
    public static PlayerVaults load(CompoundTag root, HolderLookup.Provider registries) {
        return read(root, Nbt.ops(registries));
    }
    //#else
    //$ // Items carry no registry-bound components before 1.20.5: plain NBT ops read them.
    //$ public static PlayerVaults load(CompoundTag root) { return read(root, net.minecraft.nbt.NbtOps.INSTANCE); }
    //#endif

    private static PlayerVaults read(CompoundTag root, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> registries) {
        int schema = Nbt.integer(root, "schemaVersion", 0);
        if (schema < 1 || schema > SCHEMA_VERSION) {
            throw new IllegalStateException("Unsupported gamblingitems vault schema: " + schema);
        }
        PlayerVaults storage = new PlayerVaults();
        ListTag players = Nbt.compounds(root, "players");
        for (int index = 0; index < players.size(); index++) {
            CompoundTag entry = Nbt.compound(players, index);
            UUID player = Nbt.uuid(entry, "player");
            if (schema == 1) {
                // The first format only stored the upgrader stake and its reward.
                readItems(entry, storage.forPlayer(player, VaultSection.UPGRADER), registries);
                continue;
            }
            ListTag sections = Nbt.compounds(entry, "sections");
            for (int position = 0; position < sections.size(); position++) {
                CompoundTag section = Nbt.compound(sections, position);
                readItems(section, storage.forPlayer(player,
                        VaultSection.fromId(Nbt.string(section, "section"))), registries);
            }
        }
        storage.setDirty(schema != SCHEMA_VERSION);
        return storage;
    }

    private static void readItems(CompoundTag tag, SimpleContainer container, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> registries) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            String key = "slot" + slot;
            if (!tag.contains(key)) continue;
            container.setItem(slot, Nbt.readItem(tag.get(key), registries)
                    .orElseThrow(() -> new IllegalStateException("Unreadable item in gamblingitems vault")));
        }
    }

    //#if MC < 1.21.5
    @Override
    //#endif
    //#if MC >= 1.20.5
    public CompoundTag save(CompoundTag root, HolderLookup.Provider registries) {
        return write(root, Nbt.ops(registries));
    }
    //#else
    //$ public CompoundTag save(CompoundTag root) { return write(root, net.minecraft.nbt.NbtOps.INSTANCE); }
    //#endif

    private CompoundTag write(CompoundTag root, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> registries) {
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
                        tag.put("slot" + slot, Nbt.saveItem(container.getItem(slot), registries));
                    }
                }
                saved.add(tag);
            });
            if (saved.isEmpty()) return;
            CompoundTag entry = new CompoundTag();
            Nbt.putUuid(entry, "player", player);
            entry.put("sections", saved);
            players.add(entry);
        });
        root.put("players", players);
        return root;
    }
}
