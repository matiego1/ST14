package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerBedLeaveListener implements Listener {
    public PlayerBedLeaveListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedLeave(@NotNull PlayerBedLeaveEvent event) {
        World world = event.getBed().getWorld();
        plugin.getPlayerDeepSleepListener().clearPlayerDataAndBroadcastMessage(event.getPlayer(), world, world.getTime() < 3);
    }
}
