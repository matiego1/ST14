package me.matiego.st14.commands;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.SlashCommandInteraction;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StopCommand implements CommandHandler.Minecraft, CommandHandler.Discord {
    public StopCommand(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("stop");
        if (command == null) {
            Logs.warning("The command /stop does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final Main plugin;
    private final PluginCommand command;
    private boolean isStoping = false;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (isStoping) {
            sender.sendMessage(Utils.getComponentByString("&cZatrzymanie serwera już trwa!"));
            return 0;
        }

        if (args.length == 1 && args[0].equalsIgnoreCase("now")) {
            shutdown();
            return 0;
        }
        if (args.length != 0) return -1;

        isStoping = true;

        if (Bukkit.getOnlinePlayers().isEmpty()) {
            shutdown();
            return 0;
        }

        Bukkit.broadcast(Utils.getComponentByString("&c&lSerwer zostanie wyłączony za 5 sekund!"));

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.broadcast(Utils.getComponentByString("&c&lWyłączanie serwera..."));

            shutdown();
        }, 100);
        return 0;
    }

    private void shutdown() {
        isStoping = true;
        plugin.getMiniGamesManager().setServerStopping(true);
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kick(Utils.getComponentByString("&cWyłączenie serwera. Zapraszamy później!"));
        }
        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 1);
    }

    @Override
    public @NotNull CommandData getDiscordCommand() {
        //noinspection SpellCheckingInspection
        return Commands.slash("stop", "wyłącz serwer minecraft")
                .setGuildOnly(true)
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                .addOptions(
                        new OptionData(OptionType.STRING, "pomin-odliczanie", "czy chcesz, żeby pominąć odliczanie", false)
                                .addChoice("Tak", "True")
                                .addChoice("Nie", "False")
                );
    }

    @Override
    public int onSlashCommandInteraction(@NotNull SlashCommandInteraction event) {
        boolean now = event.getOption("incognito", "False", OptionMapping::getAsString).equals("True");

        event.reply("Wyłączanie serwera minecraft...").setEphemeral(true).complete();

        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "st14:stop" + (now ? " now" : ""));
        return 0;
    }
}
