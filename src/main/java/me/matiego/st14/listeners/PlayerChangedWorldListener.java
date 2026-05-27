package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.managers.MiniGamesManager;
import me.matiego.st14.utils.MiniGamesUtils;
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

        boolean worldFrom = MiniGamesUtils.isAnyMiniGameWorld(event.getFrom());
        boolean worldTo = MiniGamesUtils.isInAnyMiniGameWorld(player);

        if (worldFrom && worldTo) return;
        if (worldFrom) {
            manager.onPlayerQuit(player);
        } else if (worldTo) {
            manager.onPlayerJoin(player);
        }
    }
}
