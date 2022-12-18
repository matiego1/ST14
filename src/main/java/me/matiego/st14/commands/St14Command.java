package me.matiego.st14.commands;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class St14Command implements CommandHandler.Minecraft {
    private final org.bukkit.command.PluginCommand command;
    public St14Command() {
        command = Main.getInstance().getCommand("st14");
        if (command == null) {
            Logs.warning("The command /st14 does not exist in the plugin.yml file and cannot be registered.");
        }
    }

    @Override
    public @Nullable org.bukkit.command.PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 1) return false;
        if (!args[0].equalsIgnoreCase("reload")) return false;

        Main.getInstance().reloadConfig();
        sender.sendMessage(Utils.getComponentByString("&aSuccessfully reloaded config."));
        return true;
    }

    @Override
    public @NotNull List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return List.of("reload");
    }
}
