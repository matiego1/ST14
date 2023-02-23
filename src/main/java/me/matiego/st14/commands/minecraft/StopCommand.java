package me.matiego.st14.commands.minecraft;

import me.matiego.st14.Main;
import me.matiego.st14.utils.CommandHandler;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StopCommand implements CommandHandler.Minecraft {
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kick(Utils.getComponentByString("&cWyłączenie serwera. Zapraszamy później!"));
        }
        Bukkit.getScheduler().runTaskLater(plugin, Bukkit::shutdown, 1);
    }
}
