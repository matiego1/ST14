package me.matiego.st14.listeners;

import me.matiego.st14.GameManager;
import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerChangedWorldListener implements Listener {
    public PlayerChangedWorldListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerChangedWorld(@NotNull PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        GameManager manager = plugin.getGameManager();
        if (event.getFrom().equals(manager.getActiveGameWorld())) {
            manager.onPlayerQuit(player);
        } else if (player.getWorld().equals(manager.getActiveGameWorld())) {
            manager.onPlayerJoin(player);
        }
    }
}
