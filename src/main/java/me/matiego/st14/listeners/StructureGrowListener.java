package me.matiego.st14.listeners;

import me.matiego.st14.utils.Utils;
import org.bukkit.block.BlockState;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.StructureGrowEvent;
import org.jetbrains.annotations.NotNull;

public class StructureGrowListener implements Listener {
    @EventHandler (ignoreCancelled = true)
    public void onStructureGrow(@NotNull StructureGrowEvent event) {
        for (BlockState block : event.getBlocks()) {
            if (Utils.doesBlockContactPortalBlock(block.getBlock())) {
                event.setCancelled(true);
            }
        }
    }
}
