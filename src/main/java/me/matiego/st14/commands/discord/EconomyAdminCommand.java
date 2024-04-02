package me.matiego.st14.commands.discord;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.commands.EconomyCommand;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;
import java.util.stream.Collectors;

public class EconomyAdminCommand implements CommandHandler.Discord {
    public EconomyAdminCommand(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("economy-admin", "zarządzaj kontami graczy")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .setGuildOnly(true)
                .addSubcommands(
                        new SubcommandData("get", "wyświetl bilans konta gracza")
                                .addOptions(new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true)),
                        new SubcommandData("set", "ustaw bilans konta gracza")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true),
                                        new OptionData(OptionType.NUMBER, "wartosc", "nowy bilans konta", true)
                                                .setRequiredRange(0, 1000d * 1000d * 1000d)
                                ),
                        new SubcommandData("add", "daj pieniądze graczowi")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true),
                                        new OptionData(OptionType.NUMBER, "wartosc", "ilość pieniędzy", true)
                                                .setRequiredRange(0, 1000d * 1000d * 1000d)
                                ),
                        new SubcommandData("remove", "zabierz pieniądze graczowi")
                                .addOptions(
                                        new OptionData(OptionType.STRING, "gracz", "nick gracza", true, true),
                                        new OptionData(OptionType.NUMBER, "wartosc", "ilość pieniędzy", true)
                                                .setRequiredRange(0, 1000d * 1000d * 1000d)
                                )
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.deferReply(true).queue();
        InteractionHook hook = event.getHook();

        String playerName = event.getOption("gracz", OptionMapping::getAsString);
        if (playerName == null) return 0;

        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
        if (uuid == null) {
            hook.sendMessage("Zły nick.").queue();
            return 0;
        }

        double amount = Math.max(0, Utils.round(event.getOption("wartosc", 0d, OptionMapping::getAsDouble), 2));

        EconomyManager economy = plugin.getEconomyManager();

        Utils.async(() -> {
            String subcommandName = String.valueOf(event.getSubcommandName());
            if (subcommandName.equals("get")) {
                hook.sendMessage("Gracz **" + playerName + "** ma **" + economy.format(economy.getBalance(Bukkit.getOfflinePlayer(uuid))) + "**.").queue();
                return;
            }

            EconomyResponse response = switch (subcommandName) {
                case "add" -> economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                case "remove" -> economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
                case "set" -> economy.setBalance(Bukkit.getOfflinePlayer(uuid), amount);
                default -> new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
            };

            if (!response.transactionSuccess()) {
                hook.sendMessage("Napotkano niespodziewany błąd. Spróbuj ponownie.").queue();
                return;
            }

            Logs.info(
                    "Administrator " +
                            switch (subcommandName) {
                                case "add" -> "zwiększył";
                                case "remove" -> "zmniejszył";
                                case "set" -> "ustawił";
                                default -> null;
                            } +
                            " stan konta gracza " + playerName + " o/na " + economy.format(amount) + "."
            );

            hook.sendMessage("Pomyślnie zmieniono saldo konta gracza **" + playerName + "** (`" + uuid +"`) na **" + economy.format(response.balance) + "**.").queue();
            plugin.getEconomyCommand().informPlayer(
                    uuid,
                    "[Administrator]",
                    amount,
                    switch (subcommandName) {
                        case "add" -> EconomyCommand.Type.ADD;
                        case "remove" -> EconomyCommand.Type.REMOVE;
                        case "set" -> EconomyCommand.Type.SET;
                        default -> EconomyCommand.Type.UNKNOWN;
                    }
            );
        });
        return 0;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        event.replyChoices(plugin.getOfflinePlayersManager().getNames().stream()
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }
}
