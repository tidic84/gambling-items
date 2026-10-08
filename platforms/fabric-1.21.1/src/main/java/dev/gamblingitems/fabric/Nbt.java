package dev.gamblingitems.fabric;

import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

/** Reading saved values: plain getters until 1.21.4, defaults spelled out since 1.21.5. */
public final class Nbt {
    private Nbt() {}

    public static String string(CompoundTag tag, String key) {
        //#if MC >= 1.21.5
        //$ return tag.getStringOr(key, "");
        //#else
        return tag.getString(key);
        //#endif
    }

    public static int integer(CompoundTag tag, String key, int absent) {
        //#if MC >= 1.21.5
        //$ return tag.getIntOr(key, absent);
        //#else
        return tag.contains(key) ? tag.getInt(key) : absent;
        //#endif
    }

    public static long longValue(CompoundTag tag, String key) {
        //#if MC >= 1.21.5
        //$ return tag.getLongOr(key, 0);
        //#else
        return tag.getLong(key);
        //#endif
    }

    public static boolean bool(CompoundTag tag, String key) {
        //#if MC >= 1.21.5
        //$ return tag.getBooleanOr(key, false);
        //#else
        return tag.getBoolean(key);
        //#endif
    }

    public static ListTag compounds(CompoundTag tag, String key) {
        //#if MC >= 1.21.5
        //$ return tag.getListOrEmpty(key);
        //#else
        return tag.getList(key, net.minecraft.nbt.Tag.TAG_COMPOUND);
        //#endif
    }

    public static CompoundTag compound(ListTag list, int index) {
        //#if MC >= 1.21.5
        //$ return list.getCompoundOrEmpty(index);
        //#else
        return list.getCompound(index);
        //#endif
    }

    //#if MC >= 1.21.6
    //$ // Block entities read through ValueInput since 1.21.6; the same keys and defaults apply.
    //$ public static String string(net.minecraft.world.level.storage.ValueInput in, String key) { return in.getStringOr(key, ""); }
    //$ public static int integer(net.minecraft.world.level.storage.ValueInput in, String key, int absent) { return in.getIntOr(key, absent); }
    //$ public static long longValue(net.minecraft.world.level.storage.ValueInput in, String key) { return in.getLongOr(key, 0); }
    //$ public static boolean bool(net.minecraft.world.level.storage.ValueInput in, String key) { return in.getBooleanOr(key, false); }
    //#endif

    /** The NBT ops that know the registries, needed to save items with their components. */
    //#if MC >= 1.20.5
    public static com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops(net.minecraft.core.HolderLookup.Provider registries) {
        return registries.createSerializationContext(net.minecraft.nbt.NbtOps.INSTANCE);
    }
    //#endif

    /** An item as saved data, through the same codec as vanilla containers. */
    public static net.minecraft.nbt.Tag saveItem(net.minecraft.world.item.ItemStack stack, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops) {
        return net.minecraft.world.item.ItemStack.CODEC.encodeStart(ops, stack).result().orElseThrow();
    }

    public static java.util.Optional<net.minecraft.world.item.ItemStack> readItem(net.minecraft.nbt.Tag tag, com.mojang.serialization.DynamicOps<net.minecraft.nbt.Tag> ops) {
        return net.minecraft.world.item.ItemStack.CODEC.parse(ops, tag).result();
    }

    /** UUIDs stay stored as four integers, the layout every version reads. */
    public static UUID uuid(CompoundTag tag, String key) {
        //#if MC >= 1.21.5
        //$ return net.minecraft.core.UUIDUtil.uuidFromIntArray(tag.getIntArray(key).orElseThrow());
        //#else
        return tag.getUUID(key);
        //#endif
    }

    public static void putUuid(CompoundTag tag, String key, UUID uuid) {
        tag.putIntArray(key, net.minecraft.core.UUIDUtil.uuidToIntArray(uuid));
    }
}
