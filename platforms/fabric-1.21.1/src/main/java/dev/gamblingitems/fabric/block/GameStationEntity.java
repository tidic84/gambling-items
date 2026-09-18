package dev.gamblingitems.fabric.block;

import dev.gamblingitems.core.GameMode;
import dev.gamblingitems.fabric.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Public display only. Player inventories and payouts never belong to the block. */
public final class GameStationEntity extends BlockEntity {
    /** Locale-neutral text shown while the draw plays, such as a percentage. */
    public String rollingText = "";
    /** Translation key suffix under gui.gamblingitems, shown once the draw ends. */
    public String resultKey = "";
    /** Identifier of the item that was won, when the result is an item rather than a message. */
    public String resultItem = "";
    public String playerName = "";
    public boolean highlight;
    public long startedAt;
    public int durationTicks;

    public GameStationEntity(BlockPos pos, BlockState state) { super(ModContent.STATION_ENTITY, pos, state); }

    public GameMode mode() {
        return getBlockState().getBlock() instanceof GameStationBlock station ? station.mode() : GameMode.UPGRADER;
    }

    public boolean idle() { return resultKey.isEmpty(); }

    public void show(String playerName, String rollingText, String resultKey, String resultItem,
                     boolean highlight, int durationTicks) {
        this.playerName = playerName;
        this.rollingText = rollingText;
        this.resultKey = resultKey;
        this.resultItem = resultItem;
        this.highlight = highlight;
        this.durationTicks = durationTicks;
        this.startedAt = level.getGameTime();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Returns the screen to its resting state, for a game whose round simply ended. */
    public void clear() {
        playerName = "";
        rollingText = "";
        resultKey = "";
        resultItem = "";
        highlight = false;
        durationTicks = 0;
        startedAt = 0;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("player", playerName);
        tag.putString("rolling", rollingText);
        tag.putString("result", resultKey);
        tag.putString("resultItem", resultItem);
        tag.putBoolean("highlight", highlight);
        tag.putLong("startedAt", startedAt);
        tag.putInt("duration", durationTicks);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        playerName = tag.getString("player");
        rollingText = tag.getString("rolling");
        resultKey = tag.getString("result");
        resultItem = tag.getString("resultItem");
        highlight = tag.getBoolean("highlight");
        startedAt = tag.getLong("startedAt");
        durationTicks = tag.getInt("duration");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
