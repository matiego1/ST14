package me.matiego.st14.listeners;

import me.matiego.st14.MiniGamesManager;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGamesUtils;
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
        MiniGamesManager manager = plugin.getMiniGamesManager();
        if (MiniGamesUtils.isAnyMiniGameWorld(event.getFrom())) {
            manager.onPlayerQuit(player);
        } else if (MiniGamesUtils.isInAnyMiniGameWorld(player)) {
            manager.onPlayerJoin(player);
        }
    }
}
