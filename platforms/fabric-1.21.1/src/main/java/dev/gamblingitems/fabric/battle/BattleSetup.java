package dev.gamblingitems.fabric.battle;

import dev.gamblingitems.fabric.cases.CaseSetup;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Everything a battle needs: the cases it opens, their values, and the pace of the lobby. */
public record BattleSetup(CaseSetup cases, BattleSettings settings) {
    public static final StreamCodec<RegistryFriendlyByteBuf, BattleSetup> CODEC = new StreamCodec<>() {
        @Override public BattleSetup decode(RegistryFriendlyByteBuf buffer) {
            return new BattleSetup(CaseSetup.CODEC.decode(buffer), BattleSettings.CODEC.decode(buffer));
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, BattleSetup data) {
            CaseSetup.CODEC.encode(buffer, data.cases());
            BattleSettings.CODEC.encode(buffer, data.settings());
        }
    };
}
