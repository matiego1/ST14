package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.jetbrains.annotations.NotNull;

public class VehicleExitListener implements Listener {
    public VehicleExitListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler (ignoreCancelled = true)
    public void onVehicleExit(@NotNull VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player player)) return;
        plugin.getVehicleMoveListener().removeBossBarForPlayer(player);
    }
}
