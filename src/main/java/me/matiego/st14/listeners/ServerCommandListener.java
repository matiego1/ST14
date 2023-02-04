package me.matiego.st14.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerCommandEvent;
import org.jetbrains.annotations.NotNull;

public class ServerCommandListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onServerCommand(@NotNull ServerCommandEvent event) {
        String command = event.getCommand();
        if (command.equalsIgnoreCase("minecraft:stop")) {
            event.setCommand("st14:stop");
        } else if (command.equalsIgnoreCase("/minecraft:stop")) {
            event.setCommand("/st14:stop");
        }
    }
}
