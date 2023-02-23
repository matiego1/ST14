package me.matiego.st14.commands.discord;

import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ListCommand implements CommandHandler.Discord {
    public ListCommand(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    
    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("list", "Wyświetla listę graczy online")
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
        IncognitoManager manager = plugin.getIncognitoManager();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!manager.isIncognito(player.getUniqueId())) {
                players.add("- " + player.getName());
            }
        }
        if (players.isEmpty()) {
            event.reply("Nikt nie jest online!").setEphemeral(ephemeral).queue();
            return 5;
        }
        if (players.size() > 50) {
            int more = players.size() - 50;
            players = players.subList(0, 50);
            players.add("... i " + more + " innych graczy");
        }
        event.reply("**__Gracze online:__**\n```\n" + String.join("\n", players) + "\n```").setEphemeral(ephemeral).queue();
        return 5;
    }
}
