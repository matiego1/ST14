package me.matiego.st14.listeners;

import me.matiego.st14.utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jetbrains.annotations.NotNull;

public class BlockPlaceListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        if (Utils.doesBlockContactPortalBlock(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar(Utils.getComponentByString("&cNie blokuj portali do netheru."));
        }
    }
}
