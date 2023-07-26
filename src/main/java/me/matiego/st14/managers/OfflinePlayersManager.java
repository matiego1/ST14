package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.Logs;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class OfflinePlayersManager {
    private final Main plugin;
    public OfflinePlayersManager(@NotNull Main plugin) {
        this.plugin = plugin;
        refreshCache();
    }

    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_offline_players\" table in the database.";
    private List<String> cache;

    public @Nullable UUID getIdByName(@NotNull String name) {
        Player player = Bukkit.getPlayer(name);
        if (player != null) {
            return player.getUniqueId();
        }
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM st14_offline_players WHERE name = ?")) {
            stmt.setString(1, name);
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return null;
            return UUID.fromString(result.getString("uuid"));
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public @Nullable String getNameById(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT name FROM st14_offline_players WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return null;
            return result.getString("name");
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public @NotNull String getEffectiveNameById(@NotNull UUID uuid) {
        String name = getNameById(uuid);
        name = name == null ? Bukkit.getOfflinePlayer(uuid).getName() : name;
        return name == null ? uuid.toString() : name;
    }

    public synchronized @NotNull List<String> getNames() {
        return cache;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS test(uuid VARCHAR(36) NOT NULL, name VARCHAR(36) NOT NULL, PRIMARY KEY (uuid), UNIQUE KEY (name))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_offline_players\"", e);
        }
        return false;
    }

    public void refresh(@NotNull UUID uuid, @NotNull String name) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_offline_players(uuid, name) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = ?, name = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, name);
            stmt.setString(3, uuid.toString());
            stmt.setString(4, name);
            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        refreshCache();
    }
    private synchronized void refreshCache() {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT name FROM st14_offline_players")) {
            ResultSet result = stmt.executeQuery();
            List<String> cache = new ArrayList<>();
            while (result.next()) cache.add(result.getString("name"));
            this.cache = cache;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }
}