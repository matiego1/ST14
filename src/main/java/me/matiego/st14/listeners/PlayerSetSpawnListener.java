package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.player.PlayerSetSpawnEvent;
import me.matiego.st14.utils.MiniGamesUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerSetSpawnListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onPlayerSetSpawn(@NotNull PlayerSetSpawnEvent event) {
        if (!MiniGamesUtils.isInAnyMiniGameWorld(event.getPlayer())) return;
        PlayerSetSpawnEvent.Cause cause = event.getCause();
        if (cause != PlayerSetSpawnEvent.Cause.BED) return;
        event.setCancelled(true);
    }
}
