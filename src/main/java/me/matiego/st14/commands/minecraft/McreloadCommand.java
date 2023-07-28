package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.objects.CommandHandler;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class McreloadCommand implements CommandHandler.Minecraft {
    public McreloadCommand(@NotNull Main plugin) {
        command = plugin.getCommand("mcreload");
        if (command == null) {
            Logs.warning("The command /mcreload does not exist in the plugin.yml file and cannot be registered.");
        }
    }
    private final PluginCommand command;

    @Override
    public @Nullable PluginCommand getMinecraftCommand() {
        return command;
    }

    @Override
    public int onCommand(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length != 0) return -1;
        sender.sendMessage(Utils.getComponentByString("&aPrzeładowywanie..."));
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "minecraft:reload");
        return 10;
    }
}
