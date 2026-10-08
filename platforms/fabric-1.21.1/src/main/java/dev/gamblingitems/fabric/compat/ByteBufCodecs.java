//#if MC < 1.20.5
//$ package dev.gamblingitems.fabric.compat;
//$
//$ import net.minecraft.network.FriendlyByteBuf;
//$
//$ /** The stream codecs of Minecraft 1.20.5 the mod uses, for the versions before it. */
//$ public final class ByteBufCodecs {
//$     private ByteBufCodecs() {}
//$
//$     public static final StreamCodec<FriendlyByteBuf, Boolean> BOOL = new StreamCodec<>() {
//$         @Override public Boolean decode(FriendlyByteBuf buffer) { return buffer.readBoolean(); }
//$         @Override public void encode(FriendlyByteBuf buffer, Boolean value) { buffer.writeBoolean(value); }
//$     };
//$ }
//#endif
