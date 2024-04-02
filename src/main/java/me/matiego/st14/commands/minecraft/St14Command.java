package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.objects.command.CommandHandler;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class St14Command implements CommandHandler.Minecraft {
    public St14Command(@NotNull Main plugin) {
        this.plugin = plugin;
        command = plugin.getCommand("st14");
        if (command == null) {
            Logs.warning("The command /st14 does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final Main plugin;
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return -1;
        if (!args[0].equalsIgnoreCase("reload")) return -1;

        plugin.reloadConfig();
        sender.sendMessage(Utils.getComponentByString("&aSuccessfully reloaded config."));
        return 3;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of("reload");
    }
}
