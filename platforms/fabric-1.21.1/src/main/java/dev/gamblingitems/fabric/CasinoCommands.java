package dev.gamblingitems.fabric;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.gamblingitems.core.prediction.PredictionMarket.Side;
import dev.gamblingitems.fabric.cheat.CasinoSecurity;
import dev.gamblingitems.fabric.prediction.MarketRecord;
import dev.gamblingitems.fabric.prediction.MarketView;
import dev.gamblingitems.fabric.prediction.PredictionMarkets;
import dev.gamblingitems.fabric.vault.CasinoLedger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * The commands of the casino's staff: a player's security file, a pardon, and the final word on a
 * disputed question. Every loader registers them under the same /gamblingitems root.
 */
public final class CasinoCommands {
    private CasinoCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gamblingitems")
                .then(Commands.literal("security")
                        .then(Commands.literal("status")
                                .executes(context -> status(context, context.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player()).requires(Compat::operator)
                                        .executes(context -> status(context, EntityArgument.getPlayer(context, "player")))))
                        .then(Commands.literal("pardon").requires(Compat::operator)
                                .then(Commands.argument("player", EntityArgument.player()).executes(CasinoCommands::pardon))))
                .then(Commands.literal("market")
                        .then(Commands.literal("list").executes(CasinoCommands::list))
                        .then(Commands.literal("resolve").requires(Compat::operator)
                                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                        .then(Commands.literal("yes").executes(context -> judge(context, Side.YES)))
                                        .then(Commands.literal("no").executes(context -> judge(context, Side.NO)))
                                        .then(Commands.literal("cancel").executes(context -> judge(context, null)))))));
    }

    private static int status(CommandContext<CommandSourceStack> context, ServerPlayer player) {
        var server = context.getSource().getServer();
        CasinoLedger.Record record = CasinoLedger.get(server).find(player.getUUID());
        int caught = record == null ? 0 : record.caught, tricks = record == null ? 0 : record.tricks;
        context.getSource().sendSuccess(() -> Component.translatable("gui.gamblingitems.security.status",
                player.getDisplayName(), CasinoSecurity.heat(server, player.getUUID()),
                CasinoSecurity.banSeconds(server, player.getUUID()), tricks, caught), false);
        return 1;
    }

    private static int pardon(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        CasinoSecurity.pardon(context.getSource().getServer(), player.getUUID());
        context.getSource().sendSuccess(() -> Component.translatable("gui.gamblingitems.security.pardoned",
                player.getDisplayName()), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        var server = context.getSource().getServer();
        int shown = 0;
        for (MarketRecord record : PredictionMarkets.listed(server)) {
            if (record.market().status().finished()) continue;
            var market = record.market();
            context.getSource().sendSuccess(() -> Component.literal("#" + record.id() + " ")
                    .append(MarketView.question(record)).append(" | ")
                    .append(Component.translatable("gui.gamblingitems.market.status." + market.status().id()))
                    .append(" | " + market.priceBasisPoints() / 100 + "%"), false);
            shown++;
        }
        if (shown == 0) context.getSource().sendSuccess(() -> Component.translatable("gui.gamblingitems.market.none"), false);
        return shown;
    }

    private static int judge(CommandContext<CommandSourceStack> context, Side answer) {
        var server = context.getSource().getServer();
        int id = IntegerArgumentType.getInteger(context, "id");
        MarketRecord record = PredictionMarkets.find(server, id);
        if (!PredictionMarkets.judge(server, record, answer)) {
            context.getSource().sendFailure(Component.translatable("gui.gamblingitems.market.not_found", id));
            return 0;
        }
        return 1;
    }
}
