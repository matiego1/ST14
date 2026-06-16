package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Rail;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Minecart;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.vehicle.VehicleMoveEvent;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class VehicleMoveListener implements Listener {
    public VehicleMoveListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final double VANILLA_MAX_SPEED = 0.4;
    private final double MIN_MAX_SPEED = 0.01;
    private final HashMap<UUID, BossBar> bossBars = new HashMap<>();

    @EventHandler (ignoreCancelled = true)
    public void onVehicleMove(@NotNull VehicleMoveEvent event) {
        if (!(event.getVehicle() instanceof Minecart minecart)) return;

        List<Entity> passengers = minecart.getPassengers();
        if (passengers.isEmpty()) {
            minecart.setMaxSpeed(VANILLA_MAX_SPEED);
            return;
        }

        Block block = event.getTo().getBlock();
        BlockData data = block.getBlockData();
        if (!(data instanceof Rail rail)) return;

        Rail.Shape shape = rail.getShape();
        boolean isAscending = shape == Rail.Shape.ASCENDING_EAST ||
                shape == Rail.Shape.ASCENDING_WEST ||
                shape == Rail.Shape.ASCENDING_SOUTH ||
                shape == Rail.Shape.ASCENDING_NORTH;

        double newMaxSpeed;
        if (isAscending) {
            newMaxSpeed = VANILLA_MAX_SPEED;
        } else {
            newMaxSpeed = getMaxSpeed(block.getRelative(BlockFace.DOWN).getType());
        }

        double currentMaxSpeed = minecart.getMaxSpeed();
        double delta = getSpeedDelta();

        if (Math.abs(newMaxSpeed - currentMaxSpeed) <= delta) {
            minecart.setMaxSpeed(newMaxSpeed);
        } else if (newMaxSpeed > currentMaxSpeed) {
            minecart.setMaxSpeed(currentMaxSpeed + delta);
        } else {
            minecart.setMaxSpeed(Math.max(MIN_MAX_SPEED, currentMaxSpeed - delta));
        }

        double distance = event.getFrom().distance(event.getTo());
        double speed = distance * 20;

        Component title = Utils.getComponentByString("&6" + Utils.round(speed, 2) + " m/s");
        double maxSpeed = minecart.getMaxSpeed();
        float progress = (float) Math.min(1, Math.max(0, speed / maxSpeed));

        passengers.forEach(entity -> {
            if (!(entity instanceof Player player)) return;
            BossBar bossBar = bossBars.get(player.getUniqueId());

            if (bossBar == null) {
                bossBar = BossBar.bossBar(title, progress, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                bossBars.put(player.getUniqueId(), bossBar);
                player.showBossBar(bossBar);
            } else {
                bossBar.name(title);
                bossBar.progress(progress);
            }
        });
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

    public void removeBossBarForPlayer(@NotNull Player player) {
        BossBar bossBar = bossBars.remove(player.getUniqueId());
        if (bossBar == null) return;
        player.hideBossBar(bossBar);
    }
}
