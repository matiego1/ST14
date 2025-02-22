package me.matiego.st14.listeners;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.jetbrains.annotations.NotNull;

public class ServerLoadListener implements Listener {
    public ServerLoadListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onServerLoad(@NotNull ServerLoadEvent event) {
        if (event.getType() == ServerLoadEvent.LoadType.RELOAD) {
            Logs.error("Server reload detected! Shutting down server, restart it instead!");
            Bukkit.shutdown();
            return;
        }

        // Set spawnChunkRadius gamerule - MultiverseCore keeps resetting this
        Bukkit.getScheduler().runTaskLater(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "gamerule spawnChunkRadius 4"), 20);
    }
}
