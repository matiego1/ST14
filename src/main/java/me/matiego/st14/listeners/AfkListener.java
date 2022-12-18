package me.matiego.st14.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.jetbrains.annotations.NotNull;

public class AfkListener implements Listener {
    private final Main plugin;
    public AfkListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        plugin.getAfkManager().move(event.getPlayer());
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        plugin.getAfkManager().move(event.getPlayer());

    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        plugin.getAfkManager().move(event.getPlayer());
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerRespawn(@NotNull PlayerRespawnEvent event) {
        plugin.getAfkManager().move(event.getPlayer());
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onAsyncChat(@NotNull AsyncChatEvent event) {
        plugin.getAfkManager().move(event.getPlayer());
    }
}
