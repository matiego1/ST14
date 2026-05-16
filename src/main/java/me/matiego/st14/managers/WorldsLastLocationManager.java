package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class WorldsLastLocationManager {
    public WorldsLastLocationManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_worlds_cmd\" table in the database.";

    public @NotNull Location getLastLocation(@NotNull UUID uuid, @NotNull World world) {
        if (plugin.getConfig().getBoolean("worlds-command." + world.getName() + ".teleport-to-spawn")) {
            return world.getSpawnLocation();
        }
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT world_loc, x, y, z, yaw, pitch FROM st14_worlds_cmd WHERE uuid = ? AND world = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, world.getUID().toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return world.getSpawnLocation();

            World worldLoc = Bukkit.getWorld(result.getString("world_loc"));
            if (worldLoc == null) return world.getSpawnLocation();

            return new Location(
                    worldLoc,
                    result.getDouble("x"),
                    result.getDouble("y"),
                    result.getDouble("z"),
                    result.getFloat("yaw"),
                    result.getFloat("pitch")
            );
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return world.getSpawnLocation();
    }

    public void setLastLocation(@NotNull UUID uuid, @NotNull World world, @NotNull Location loc) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_worlds_cmd(uuid, world, world_loc, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world_loc = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, world.getUID().toString());

            stmt.setString(3, loc.getWorld().getUID().toString());
            stmt.setDouble(4, loc.getX());
            stmt.setDouble(5, loc.getY());
            stmt.setDouble(6, loc.getZ());
            stmt.setDouble(7, loc.getYaw());
            stmt.setDouble(8, loc.getPitch());

            stmt.setString(9, loc.getWorld().getUID().toString());
            stmt.setDouble(10, loc.getX());
            stmt.setDouble(11, loc.getY());
            stmt.setDouble(12, loc.getZ());
            stmt.setDouble(13, loc.getYaw());
            stmt.setDouble(14, loc.getPitch());

            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_worlds_cmd(uuid VARCHAR(36) NOT NULL, world VARCHAR(36) NOT NULL, world_loc VARCHAR(36) NOT NULL, x DECIMAL(15, 5) NOT NULL, y DECIMAL(15, 5) NOT NULL, z DECIMAL(15, 5) NOT NULL, yaw DECIMAL(15, 5) NOT NULL, pitch DECIMAL(15, 5) NOT NULL, CONSTRAINT st14_worlds_cmd_const UNIQUE (uuid, world))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_worlds_cmd\"", e);
        }
        return false;
    }
}
