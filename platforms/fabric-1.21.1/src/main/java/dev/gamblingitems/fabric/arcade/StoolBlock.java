package dev.gamblingitems.fabric.arcade;

import com.mojang.serialization.MapCodec;
import dev.gamblingitems.fabric.Compat;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A casino stool: a chrome pedestal under a round leather seat. A click sits the player on it, in front
 * of a table or a cabinet; sneaking gets them up, as from any seat.
 */
public final class StoolBlock extends Block {
    //#if MC >= 1.20.3 && MC < 26.3
    public static final MapCodec<StoolBlock> CODEC = simpleCodec(StoolBlock::new);
    //#endif
    private static final VoxelShape SHAPE = Block.box(3, 0, 3, 13, 10, 13);
    /** Where a seated player's hips rest: on the leather, ten pixels up. */
    private static final double SEAT = 10 / 16.0;

    public StoolBlock(Properties properties) { super(properties); }

    //#if MC >= 1.20.3 && MC < 26.3
    @Override protected MapCodec<? extends Block> codec() { return CODEC; }
    //#endif

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) { return SHAPE; }

    //#if MC >= 1.20.5
    @Override protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        sit(level, pos, player);
        return Compat.handled(level);
    }
    //#else
    //$ @Override public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
    //$         net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
    //$     sit(level, pos, player);
    //$     return InteractionResult.sidedSuccess(level.isClientSide);
    //$ }
    //#endif

    /** Seats the player on an invisible seat that is never saved and goes as soon as it is left. */
    static void sit(Level level, BlockPos pos, Player player) {
        if (level.isClientSide || player.isPassenger() || player.isShiftKeyDown()) return;
        // One player per stool.
        if (!level.getEntitiesOfClass(Display.BlockDisplay.class, new net.minecraft.world.phys.AABB(pos),
                seat -> seat.isVehicle()).isEmpty()) return;
        Display.BlockDisplay seat = new Display.BlockDisplay(EntityType.BLOCK_DISPLAY, level) {
            @Override public void tick() {
                super.tick();
                if (!level().isClientSide && (!isVehicle() || !(level().getBlockState(blockPosition()).getBlock() instanceof StoolBlock))) {
                    discard();
                }
            }

            @Override public boolean shouldBeSaved() { return false; }
        };
        //#if MC >= 1.20.5
        double hips = SEAT;
        //#else
        //$ // Before 1.20.5 a player rides 0.35 below its vehicle instead of resting its hips on it.
        //$ double hips = SEAT + 0.25;
        //#endif
        seat.setPos(pos.getX() + 0.5, pos.getY() + hips, pos.getZ() + 0.5);
        seat.setYRot(player.getYRot());
        level.addFreshEntity(seat);
        player.startRiding(seat);
    }
}
