package dev.gamblingitems.fabric.block;

import dev.gamblingitems.fabric.Nbt;

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
    /** The tickets the last play of an arcade cabinet won, shown once it is revealed. */
    public long tickets;
    /**
     * An administrator's settings for this one cabinet, over the server's: closed, and a smallest and
     * largest stake (0 keeps the server's). See /gamblingitems cabinet.
     */
    public boolean closed;
    public long minimumStake, maximumStake;
    /** Video poker: the stake of the hand being played, and who plays it. */
    public long handStake;
    public String handOwner = "";
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
    /** Client side only: the play a cabinet last celebrated as a big win, and the tick of its last sparks. */
    public long celebratedAt = -1, lastSpark;
    /** Client only, never saved: what the cabinet already sounded, and what its renderer keeps between frames. */
    public long soundedStart = -1, soundedStep = -1, busyCheckedAt;
    public boolean soundedReveal, busyNearby;
    public Object clientState;
    /** Server side only: what the seat fields were in the last packet built, see preview. */
    private String sentPreview = "";

    public boolean animating() { return level != null && level.getGameTime() < startedAt + durationTicks; }

    public void preview(String player, String text) {
        previewPlayer = player;
        previewText = text;
        // Seats call this every tick: only resend the block when what it shows has changed.
        if (previewKey().equals(sentPreview)) return;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** The fields a seat writes before calling preview. */
    private String previewKey() {
        return String.join("\n", previewPlayer, previewText, cards, String.valueOf(phase), stakeItems,
                reelItems, rollingText);
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

    /** Back to the resting screen, keeping what a round shows meanwhile: its bets and its countdown. */
    public void clearResult() {
        playerName = "";
        rollingText = "";
        resultKey = "";
        resultItem = "";
        highlight = false;
        durationTicks = 0;
        startedAt = 0;
        reels = "";
        tickets = 0;
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

    //#if MC >= 1.21.6
    //$ @Override protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput tag) {
    //$     super.saveAdditional(tag);
    //#elif MC < 1.20.5
    //$ @Override protected void saveAdditional(CompoundTag tag) {
    //$     super.saveAdditional(tag);
    //#else
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
    //#endif
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
        tag.putLong("tickets", tickets);
        tag.putBoolean("closed", closed);
        tag.putLong("minimumStake", minimumStake);
        tag.putLong("maximumStake", maximumStake);
        tag.putLong("handStake", handStake);
        tag.putString("handOwner", handOwner);
    }

    //#if MC >= 1.21.6
    //$ @Override protected void loadAdditional(net.minecraft.world.level.storage.ValueInput tag) {
    //$     super.loadAdditional(tag);
    //#elif MC < 1.20.5
    //$ @Override public void load(CompoundTag tag) {
    //$     super.load(tag);
    //#else
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
    //#endif
        playerName = Nbt.string(tag, "player");
        rollingText = Nbt.string(tag, "rolling");
        resultKey = Nbt.string(tag, "result");
        resultItem = Nbt.string(tag, "resultItem");
        highlight = Nbt.bool(tag, "highlight");
        startedAt = Nbt.longValue(tag, "startedAt");
        durationTicks = Nbt.integer(tag, "duration", 0);
        previewPlayer = Nbt.string(tag, "previewPlayer");
        previewText = Nbt.string(tag, "previewText");
        publicBets = Nbt.string(tag, "publicBets");
        cards = Nbt.string(tag, "cards");
        drawn = Nbt.string(tag, "drawn");
        reels = Nbt.string(tag, "reels");
        stakeItems = Nbt.string(tag, "stakeItems");
        reelItems = Nbt.string(tag, "reelItems");
        wheelColours = Nbt.string(tag, "wheelColours");
        phase = Nbt.integer(tag, "phase", -1);
        targetSlot = Nbt.integer(tag, "targetSlot", -1);
        animationTicks = Nbt.integer(tag, "animationTicks", 0);
        multiplier = Nbt.integer(tag, "multiplier", 0);
        phaseEnd = Nbt.longValue(tag, "phaseEnd");
        tickets = Nbt.longValue(tag, "tickets");
        closed = Nbt.bool(tag, "closed");
        minimumStake = Nbt.longValue(tag, "minimumStake");
        maximumStake = Nbt.longValue(tag, "maximumStake");
        handStake = Nbt.longValue(tag, "handStake");
        handOwner = Nbt.string(tag, "handOwner");
    }

    //#if MC >= 1.20.5
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { sentPreview = previewKey(); return saveWithoutMetadata(registries); }
    //#else
    //$ @Override public CompoundTag getUpdateTag() { sentPreview = previewKey(); return saveWithoutMetadata(); }
    //#endif
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}
