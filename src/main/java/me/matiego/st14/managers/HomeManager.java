package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class HomeManager {
    public HomeManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_homes\" table in the database.";

    public @Nullable Location getHomeLocation(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT world, x, y, z, yaw, pitch FROM st14_homes WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return null;

            World world = Bukkit.getWorld(UUID.fromString(result.getString("world")));
            if (world == null) return null;

            return new Location(
                    world,
                    result.getDouble("x"),
                    result.getDouble("y"),
                    result.getDouble("z"),
                    result.getFloat("yaw"),
                    result.getFloat("pitch")
            );
        } catch (SQLException|IllegalArgumentException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public boolean setHomeLocation(@NotNull UUID uuid, @NotNull Location loc) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_homes(uuid, world, x, y, z, yaw, pitch) VALUES (?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE world = ?, x = ?, y = ?, z = ?, yaw = ?, pitch = ?")) {
            stmt.setString(1, uuid.toString());

            stmt.setString(2, loc.getWorld().getUID().toString());
            stmt.setDouble(3, loc.getX());
            stmt.setDouble(4, loc.getY());
            stmt.setDouble(5, loc.getZ());
            stmt.setDouble(6, loc.getYaw());
            stmt.setDouble(7, loc.getPitch());

            stmt.setString(8, loc.getWorld().getUID().toString());
            stmt.setDouble(9, loc.getX());
            stmt.setDouble(10, loc.getY());
            stmt.setDouble(11, loc.getZ());
            stmt.setDouble(12, loc.getYaw());
            stmt.setDouble(13, loc.getPitch());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean removeHome(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_homes WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean isHomeLocationSet(@NotNull UUID uuid) {
        return getHomeLocation(uuid) != null;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_homes(uuid VARCHAR(36) NOT NULL, world VARCHAR(36) NOT NULL, x DECIMAL(15, 5) NOT NULL, y DECIMAL(15, 5) NOT NULL, z DECIMAL(15, 5) NOT NULL, yaw DECIMAL(15, 5) NOT NULL, pitch DECIMAL(15, 5) NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_homes\"", e);
        }
        return false;
    }
}
