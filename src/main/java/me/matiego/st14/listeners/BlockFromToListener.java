package me.matiego.st14.listeners;

import me.matiego.st14.utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFromToEvent;

import javax.annotation.Nonnull;

public class BlockFromToListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockFromTo(@Nonnull BlockFromToEvent event) {
        if (Utils.doesBlockContactPortalBlock(event.getToBlock())) {
            event.setCancelled(true);
        }
    }
}
