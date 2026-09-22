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
    public String reelItems = "", wheelColours = "";
    public int phase = -1, targetSlot = -1, animationTicks, multiplier = 100;
    public long phaseEnd;
    public String previewPlayer = "", previewText = "", publicBets = "";
    /** Cards on the felt: the hand, then the dealer, "12,25|7?" with ? for the hole card. */
    public String cards = "";
    /** Numbers already called by a bingo drum, oldest first. */
    public String drawn = "";
    /** The line a slot machine has just drawn, as "0,4,7". */
    public String reels = "";
    /** The items staked on a table, as "minecraft:diamond*3", laid out on its felt. */
    public String stakeItems = "";
    /** Client side only: what the felt last drew, and when, so a new card can be dealt in. */
    public String seenCards = "";
    public long cardsChangedAt;

    public boolean animating() { return level != null && level.getGameTime() < startedAt + durationTicks; }

    public void preview(String player, String text) {
        previewPlayer = player;
        previewText = text;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public GameStationEntity(BlockPos pos, BlockState state) { super(ModContent.STATION_ENTITY, pos, state); }

    public GameMode mode() {
        return getBlockState().getBlock() instanceof GameSurface surface ? surface.mode() : GameMode.UPGRADER;
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
        publicBets = "";
        cards = "";
        drawn = "";
        reels = "";
        stakeItems = "";
        phase = -1;
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
        tag.putString("previewPlayer", previewPlayer);
        tag.putString("previewText", previewText);
        tag.putString("publicBets", publicBets);
        tag.putString("cards", cards);
        tag.putString("drawn", drawn);
        tag.putString("reels", reels);
        tag.putString("stakeItems", stakeItems);
        tag.putString("reelItems", reelItems);
        tag.putString("wheelColours", wheelColours);
        tag.putInt("phase", phase);
        tag.putInt("targetSlot", targetSlot);
        tag.putInt("animationTicks", animationTicks);
        tag.putInt("multiplier", multiplier);
        tag.putLong("phaseEnd", phaseEnd);
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
        previewPlayer = tag.getString("previewPlayer");
        previewText = tag.getString("previewText");
        publicBets = tag.getString("publicBets");
        cards = tag.getString("cards");
        drawn = tag.getString("drawn");
        reels = tag.getString("reels");
        stakeItems = tag.getString("stakeItems");
        reelItems = tag.getString("reelItems");
        wheelColours = tag.getString("wheelColours");
        phase = tag.contains("phase") ? tag.getInt("phase") : -1;
        targetSlot = tag.contains("targetSlot") ? tag.getInt("targetSlot") : -1;
        animationTicks = tag.getInt("animationTicks");
        multiplier = tag.getInt("multiplier");
        phaseEnd = tag.getLong("phaseEnd");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
