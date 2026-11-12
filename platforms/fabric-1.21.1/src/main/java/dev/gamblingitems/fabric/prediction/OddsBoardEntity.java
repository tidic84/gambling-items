package dev.gamblingitems.fabric.prediction;

import dev.gamblingitems.fabric.ModContent;
import dev.gamblingitems.fabric.Nbt;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Public display only: the busiest open questions, refreshed by the server every two seconds. */
public final class OddsBoardEntity extends BlockEntity {
    public static final int SHOWN = 3;
    private static final int REFRESH_TICKS = 40;
    private ListTag shown = new ListTag();
    private int refresh;

    public OddsBoardEntity(BlockPos pos, BlockState state) { super(ModContent.ODDS_BOARD_ENTITY, pos, state); }

    /** The questions on the board, as the server last described them. */
    public List<MarketView> views() {
        List<MarketView> views = new ArrayList<>();
        for (int index = 0; index < shown.size(); index++) {
            MarketView view = MarketView.load(Nbt.compound(shown, index));
            if (view != null) views.add(view);
        }
        return views;
    }

    void serverTick() {
        if (level == null || --refresh > 0) return;
        refresh = REFRESH_TICKS;
        MinecraftServer server = level.getServer();
        if (server == null) return;
        ListTag next = new ListTag();
        long now = CasinoSecurity.now();
        for (MarketRecord record : PredictionMarkets.listed(server)) {
            if (next.size() >= SHOWN || !record.market().trading()) break;
            next.add(MarketView.of(record, null, now).save());
        }
        if (next.equals(shown)) return;
        shown = next;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    //#if MC >= 1.21.6
    //$ @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
    //$     super.saveAdditional(tag);
    //$     tag.store("shown", CompoundTag.CODEC, wrap());
    //$ }
    //$ @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
    //$     super.loadAdditional(tag);
    //$     unwrap(tag.read("shown", CompoundTag.CODEC).orElseGet(CompoundTag::new));
    //$ }
    //#elif MC < 1.20.5
    //$ @Override protected void saveAdditional(CompoundTag tag) {
    //$     super.saveAdditional(tag);
    //$     tag.put("shown", wrap());
    //$ }
    //$ @Override public void load(CompoundTag tag) {
    //$     super.load(tag);
    //$     unwrap(tag.getCompound("shown"));
    //$ }
    //#else
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("shown", wrap());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        unwrap(Nbt.child(tag, "shown"));
    }
    //#endif

    private CompoundTag wrap() {
        CompoundTag tag = new CompoundTag();
        tag.put("markets", shown.copy());
        return tag;
    }

    private void unwrap(CompoundTag tag) { shown = Nbt.compounds(tag, "markets"); }

    //#if MC >= 1.20.5
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    //#else
    //$ @Override public CompoundTag getUpdateTag() { return saveWithoutMetadata(); }
    //#endif
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
