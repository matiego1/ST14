package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class PlayerCommandPreprocessListener implements Listener {
    public PlayerCommandPreprocessListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1).toLowerCase();
        if (command.equalsIgnoreCase("minecraft:stop")) {
            event.setCancelled(true);
            player.performCommand("st14:stop");
        }
        if (command.isBlank()) return;
        if (command.charAt(0) == '/') command = command.substring(1);
        if (command.isBlank()) return;

        if (player.isOp()) {
            Logs.info("[" + player.getName() + "]: " + event.getMessage());
            return;
        }

        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        if (allowedCommands.isEmpty()) {
            Logs.info("[" + player.getName() + "]: " + event.getMessage());
            return;
        }

        if (plugin.getConfig().getStringList("allowed-commands-bypass").contains(event.getPlayer().getUniqueId().toString())) {
            Logs.info("[" + player.getName() + "]: " + event.getMessage());
            return;
        }

        for (String allowedCommand : allowedCommands) {
            if (command.startsWith(allowedCommand.toLowerCase())) {
                Logs.info("[" + player.getName() + "]: " + event.getMessage());
                return;
            }
        }
        event.setCancelled(true);
        player.sendMessage(Utils.getComponentByString("&cNie masz uprawnień, aby użyć tej komendy!"));
    }
}
