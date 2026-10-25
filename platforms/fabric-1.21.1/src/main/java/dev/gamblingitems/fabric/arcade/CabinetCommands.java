package dev.gamblingitems.fabric.arcade;

import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.gamblingitems.fabric.block.GameStationEntity;
import dev.gamblingitems.fabric.block.SlotMachineBlock;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;

/**
 * {@code /gamblingitems cabinet <pos> info|open|close|minimum <v>|maximum <v>|reset}: an operator's
 * settings for one cabinet, over the configuration of its machine. A stake of 0 means "as configured".
 */
public final class CabinetCommands {
    private static final SimpleCommandExceptionType NOT_A_CABINET = new SimpleCommandExceptionType(
            Component.translatable("commands.gamblingitems.cabinet.not_found"));

    private CabinetCommands() {}

    public static LiteralArgumentBuilder<CommandSourceStack> cabinet() {
        //#if MC >= 1.21.11
        //$ return Commands.literal("cabinet").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
        //#else
        return Commands.literal("cabinet").requires(source -> source.hasPermission(2))
        //#endif
                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                        .then(Commands.literal("info").executes(context -> info(context)))
                        .then(Commands.literal("open").executes(context -> change(context, cabinet -> cabinet.closed = false)))
                        .then(Commands.literal("close").executes(context -> change(context, cabinet -> cabinet.closed = true)))
                        .then(Commands.literal("reset").executes(context -> change(context, cabinet -> {
                            cabinet.closed = false;
                            cabinet.minimumStake = 0;
                            cabinet.maximumStake = 0;
                        })))
                        .then(Commands.literal("minimum").then(Commands.argument("value", LongArgumentType.longArg(0))
                                .executes(context -> change(context, cabinet ->
                                        cabinet.minimumStake = LongArgumentType.getLong(context, "value")))))
                        .then(Commands.literal("maximum").then(Commands.argument("value", LongArgumentType.longArg(0))
                                .executes(context -> change(context, cabinet ->
                                        cabinet.maximumStake = LongArgumentType.getLong(context, "value"))))));
    }

    private static GameStationEntity find(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        BlockPos pos = BlockPosArgument.getLoadedBlockPos(context, "pos");
        var level = context.getSource().getLevel();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof SlotMachineBlock)) throw NOT_A_CABINET.create();
        if (!(level.getBlockEntity(SlotMachineBlock.anchor(pos, state)) instanceof GameStationEntity cabinet)) {
            throw NOT_A_CABINET.create();
        }
        return cabinet;
    }

    private static int change(CommandContext<CommandSourceStack> context, Consumer<GameStationEntity> edit)
            throws CommandSyntaxException {
        GameStationEntity cabinet = find(context);
        edit.accept(cabinet);
        if (cabinet.maximumStake > 0 && cabinet.minimumStake > cabinet.maximumStake) cabinet.maximumStake = cabinet.minimumStake;
        cabinet.setChanged();
        cabinet.getLevel().sendBlockUpdated(cabinet.getBlockPos(), cabinet.getBlockState(), cabinet.getBlockState(), 3);
        return info(context);
    }

    private static int info(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        GameStationEntity cabinet = find(context);
        context.getSource().sendSuccess(() -> Component.translatable("commands.gamblingitems.cabinet.info",
                Component.translatable("gui.gamblingitems.mode." + cabinet.mode().id()),
                Component.translatable(cabinet.closed ? "commands.gamblingitems.cabinet.closed" : "commands.gamblingitems.cabinet.open"),
                cabinet.minimumStake, cabinet.maximumStake), false);
        return 1;
    }
}
