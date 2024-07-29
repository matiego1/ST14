package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Minecart;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;

public class VehicleMoveListener implements Listener {
    public VehicleMoveListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    final Main plugin;
    final double VANILLA_MAX_SPEED = 0.4;
    final double MIN_MAX_SPEED = 0.01;

    @EventHandler (ignoreCancelled = true)
    public void onVehicleMove(@NotNull VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;

        if (minecart.getPassengers().isEmpty()) {
            minecart.setMaxSpeed(VANILLA_MAX_SPEED);
            return;
        }

        Block block = minecart.getLocation().getBlock();
        if (!block.getType().toString().contains("RAIL")) return;

        double newMaxSpeed = getMaxSpeed(block.getRelative(BlockFace.DOWN).getType());
        double currentMaxSpeed = minecart.getMaxSpeed();
        double delta = getSpeedDelta();

        if (Math.abs(newMaxSpeed - currentMaxSpeed) <= delta) {
            minecart.setMaxSpeed(newMaxSpeed);
            return;
        }

        if (newMaxSpeed > currentMaxSpeed) {
            minecart.setMaxSpeed(currentMaxSpeed + delta);
        } else {
            minecart.setMaxSpeed(Math.max(MIN_MAX_SPEED, currentMaxSpeed - delta));
        }
    }

    private double getMaxSpeed(@NotNull Material block) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("minecarts.blocks");
        if (section == null) return VANILLA_MAX_SPEED;
        for (String key : section.getKeys(false)) {
            if (block.toString().matches(key)) {
                return Math.max(MIN_MAX_SPEED, plugin.getConfig().getDouble("minecarts.blocks." + key, VANILLA_MAX_SPEED));
            }
        }
        return VANILLA_MAX_SPEED;
    }

    private double getSpeedDelta() {
        return Math.max(MIN_MAX_SPEED, plugin.getConfig().getDouble("minecarts.delta", 100));
    }
}
