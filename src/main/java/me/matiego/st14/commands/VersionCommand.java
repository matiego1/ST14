package me.matiego.st14.commands;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class VersionCommand implements CommandHandler.Discord, CommandHandler.Minecraft {
    private final PluginCommand command;
    public VersionCommand() {
        command = Main.getInstance().getCommand("version");
        if (command == null) {
            Logs.warning("The command /version does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 0) return -1;
        sender.sendMessage(Utils.getComponentByString("&aWersja pluginu ST14: " + getVersion()));
        return 0;
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("version", "Wyświetl aktualną wersję pluginu ST14")
                .addOptions(
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        event.reply("**Wersja pluginu ST14:** `" + getVersion() + "`")
                .setEphemeral(event.getOption("incognito", "False", OptionMapping::getAsString).equals("True"))
                .queue();
        return 0;
    }

    private @NotNull String getVersion() {
        return Main.getInstance().getDescription().getVersion();
    }
}