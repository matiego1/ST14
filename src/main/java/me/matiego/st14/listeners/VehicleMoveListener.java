package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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

        List<Entity> passengers = minecart.getPassengers();
        boolean isNoneOfPassengersPlayer = true;
        for (Entity entity : passengers) {
            if (entity instanceof Player) {
                isNoneOfPassengersPlayer = false;
                break;
            }
        }
        if (isNoneOfPassengersPlayer) {
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
        return Math.max(MIN_MAX_SPEED, plugin.getConfig().getDouble("minecarts.blocks." + block, VANILLA_MAX_SPEED));
    }

    private double getSpeedDelta() {
        return Math.max(MIN_MAX_SPEED, plugin.getConfig().getDouble("minecarts.delta", 100));
    }
}
