//#if MC < 1.20.5
//$ package dev.gamblingitems.fabric.compat;
//$
//$ import java.util.function.BiFunction;
//$ import java.util.function.Function;
//$
//$ /**
//$  * The part of Minecraft's StreamCodec (1.20.5) the mod uses, for the versions before it.
//$  * tools/port.py points the imports here on those versions; the code using it is unchanged.
//$  */
//$ public interface StreamCodec<B, V> {
//$     V decode(B buffer);
//$     void encode(B buffer, V value);
//$
//$     static <B, C, T1, T2> StreamCodec<B, C> composite(StreamCodec<? super B, T1> first, Function<C, T1> firstGetter,
//$             StreamCodec<? super B, T2> second, Function<C, T2> secondGetter, BiFunction<T1, T2, C> factory) {
//$         return new StreamCodec<>() {
//$             @Override public C decode(B buffer) { return factory.apply(first.decode(buffer), second.decode(buffer)); }
//$             @Override public void encode(B buffer, C value) {
//$                 first.encode(buffer, firstGetter.apply(value));
//$                 second.encode(buffer, secondGetter.apply(value));
//$             }
//$         };
//$     }
//$ }
//#endif
