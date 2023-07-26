package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.World;
import org.bukkit.entity.Player;
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
        Player player = event.getPlayer();
        World world = event.getBed().getWorld();
        plugin.getPlayerBedEnterListener().clearSleepingInWorld(world);
        if (world.getTime() != 0) return;

        Utils.broadcastMessage(
                player,
                Prefix.SLEEPING_THROUGH_NIGHT,
                "&6[" + Utils.getWorldName(world) + "] &eGracz &6" + player.getName() + "&e poszedł spać. Słodkich snów!",
                "&6[" + Utils.getWorldName(world) + "] &eGracz &6" + player.getName() + "&e poszedł spać. Słodkich snów!",
                "**[" + Utils.getWorldName(world) + "]** Gracz **" + player.getName() + "** poszedł spać. Słodkich snów!"
        );
    }
}
