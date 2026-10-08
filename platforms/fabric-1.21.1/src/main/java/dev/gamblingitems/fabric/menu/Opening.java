package dev.gamblingitems.fabric.menu;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** What a game window is built from, and whether it was opened from a portable item, which shows its tabs. */
public record Opening<D>(D setup, boolean portable) {
    public static <D> StreamCodec<RegistryFriendlyByteBuf, Opening<D>> codec(StreamCodec<? super RegistryFriendlyByteBuf, D> setup) {
        return StreamCodec.composite(setup, Opening::setup, ByteBufCodecs.BOOL, Opening::portable, Opening::new);
    }
}
