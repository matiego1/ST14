package me.matiego.st14.listeners;

import me.matiego.st14.utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;

public class BlockFormListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(@NotNull BlockBreakEvent event) {
        if (Utils.doesBlockContactPortalBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }
}
