package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.jetbrains.annotations.NotNull;

public class ChunkUnloadListener implements Listener {
    public ChunkUnloadListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkUnload(@NotNull ChunkUnloadEvent event) {
        plugin.getClaimsDynmap().refreshClaims(event.getChunk());
    }
}