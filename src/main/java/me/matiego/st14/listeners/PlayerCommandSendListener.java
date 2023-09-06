package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.List;

public class PlayerCommandSendListener implements Listener {
    public PlayerCommandSendListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onPlayerCommandSent(@NotNull PlayerCommandSendEvent event) {
        Player player = event.getPlayer();

        if (player.isOp()) return;
        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        if (allowedCommands.isEmpty()) return;
        if (plugin.getConfig().getStringList("allowed-commands-bypass").contains(player.getUniqueId().toString())) return;

        Iterator<String> iterator = event.getCommands().iterator();
        while (iterator.hasNext()) {
            String command = iterator.next();
            if (command.isBlank()) return;
            if (command.charAt(0) == '/') command = command.substring(1);
            if (!allowedCommands.contains(command)) iterator.remove();
        }
    }
}
