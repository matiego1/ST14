package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.jetbrains.annotations.NotNull;

public class ChunkLoadListener implements Listener {
    public ChunkLoadListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(@NotNull ChunkLoadEvent event) {
        if (event.isNewChunk()) return;
        plugin.getClaimsDynmap().refreshClaims(event.getChunk());
    }
}
