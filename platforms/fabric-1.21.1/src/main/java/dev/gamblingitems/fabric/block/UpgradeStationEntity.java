package dev.gamblingitems.fabric.block;

import dev.gamblingitems.fabric.ModContent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** Public display only. Player inventories and payouts never belong to the block. */
public final class UpgradeStationEntity extends BlockEntity {
    public String target = "";
    public String playerName = "";
    public int chanceBasisPoints;
    public boolean won;
    public long startedAt;

    public UpgradeStationEntity(BlockPos pos, BlockState state) { super(ModContent.STATION_ENTITY, pos, state); }
    public void showResult(ResourceLocation target, int chance, boolean won, String playerName) {
        this.target = target.toString();
        this.chanceBasisPoints = chance;
        this.won = won;
        this.playerName = playerName;
        this.startedAt = level.getGameTime();
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("target", target);
        tag.putString("player", playerName);
        tag.putInt("chance", chanceBasisPoints);
        tag.putBoolean("won", won);
        tag.putLong("startedAt", startedAt);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        target = tag.getString("target");
        playerName = tag.getString("player");
        chanceBasisPoints = tag.getInt("chance");
        won = tag.getBoolean("won");
        startedAt = tag.getLong("startedAt");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }
}

