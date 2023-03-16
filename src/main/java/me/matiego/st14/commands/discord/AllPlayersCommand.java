package me.matiego.st14.commands.discord;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class AllPlayersCommand implements CommandHandler.Discord {
    public AllPlayersCommand(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("all-players", "Wyświetl listę wszystkich graczy")
                .addOptions(
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setGuildOnly(true);
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");

        List<String> players = new ArrayList<>();
        int index = 1;
        for (String name : plugin.getOfflinePlayers().getNames()) {
            players.add((index++) + ". " + name);
        }

        if (players.isEmpty()) {
            event.reply("Nikt nie grał na tym serwerze albo napotkano błąd.").setEphemeral(ephemeral).queue();
            return 5;
        }

        if (players.size() > 50) {
            int more = players.size() - 50;
            players = players.subList(0, 50);
            players.add("... i " + more + " innych graczy");
        }

        event.reply("**__Wszyscy gracze:__**\n```\n" + String.join("\n", players) + "\n```").setEphemeral(ephemeral).queue();
        return 5;
    }
}
