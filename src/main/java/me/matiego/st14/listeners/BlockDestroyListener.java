package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;

public class BlockDestroyListener implements Listener {
    public BlockDestroyListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDestroy(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        if (block.getType().toString().contains("SIGN")) {
            plugin.getDynmapManager().getSignsMarker().deleteMarker(block.getLocation());
        }
    }
}
