package me.matiego.st14.listeners;

import me.matiego.st14.utils.Utils;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.jetbrains.annotations.NotNull;

public class BlockPistonExtendListener implements Listener {
    @EventHandler(ignoreCancelled = true)
    public void onBlockPistonExtend(@NotNull BlockPistonExtendEvent event) {
        BlockFace direction = event.getDirection();
        for (Block block : event.getBlocks()) {
            if (Utils.doesBlockContactPortalBlock(block.getRelative(direction))) {
                event.setCancelled(true);
            }
        }
    }
}
