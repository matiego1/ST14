package me.matiego.st14.commands;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.*;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CoordinatesCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public CoordinatesCommand(@NotNull Main plugin) {
        command = plugin.getCommand("coordinates");
        if (command == null) {
            Logs.warning("The command /coordinates does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        Player player = Bukkit.getPlayer(args[0]);
        if (player == null) {
            sender.sendMessage("&cTen gracz nie jest online!");
            return 0;
        }

        sender.sendMessage("&aKoordynaty gracza &2" + player.getName() + "&a: &2" + getCoordinates(player));
        return 0;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).collect(Collectors.toList());
        }
        return new ArrayList<>();
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        return Commands.slash("coordinates", "Pokazuje koordynaty gracza")
                .addOptions(
                        new OptionData(OptionType.STRING, "gracz", "Gracz, którego koordynaty mają być pokazane", true)
                                .setAutoComplete(true),
                        new OptionData(OptionType.STRING, "incognito", "czy wiadomość ma być widoczna tylko dla ciebie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                )
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR));
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean ephemeral = event.getOption("incognito", "True", OptionMapping::getAsString).equals("True");

        String name = event.getOption("gracz", "", OptionMapping::getAsString);

        Player player = Bukkit.getPlayer(name);
        if (player == null) {
            event.reply("Ten gracz nie jest online!").setEphemeral(ephemeral).queue();
            return 0;
        }

        event.reply("Koordynaty gracza **" + player.getName() + "**: `" + getCoordinates(player) + "`").setEphemeral(ephemeral).queue();
        return 0;
    }

    @Override
    public void onCommandAutoCompleteInteraction(@NotNull CommandAutoCompleteInteraction event) {
        if (!event.getName().equals(getDiscordCommand().getName())) return;
        event.replyChoices(Bukkit.getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> name.toLowerCase().startsWith(event.getFocusedOption().getValue().toLowerCase()))
                .map(name -> new Command.Choice(name, name))
                .collect(Collectors.toList())
        ).queue();
    }

    private @NotNull String getCoordinates(@NotNull Player player) {
        Block block = player.getLocation().getBlock();
        return Utils.getWorldName(player.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ();
    }
}
