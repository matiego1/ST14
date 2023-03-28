package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.jetbrains.annotations.NotNull;

import java.util.regex.PatternSyntaxException;

public class BlockBreakListener implements Listener {
    public BlockBreakListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        Block block = event.getBlock();
        String material = block.getType().name();
        Player player = event.getPlayer();
        try {
            if (material.matches(plugin.getConfig().getString("block-break-warn-regex", "[^\\s\\S]*"))) {
                Logs.info("Gracz " + player.getName() + " wykopał " + material + " w " + Utils.getWorldName(player.getWorld()));
            }
        } catch (PatternSyntaxException e) {
            Logs.warning("block-break-warn regex's syntax is invalid", e);
        }

        if (material.contains("SIGN")) {
            plugin.getDynmap().deleteSignMarker(block.getLocation());
        }
    }
}
