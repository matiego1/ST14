package me.matiego.st14.managers;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class HeadsManager {
    public HeadsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    @Getter (onMethod_ = {@Synchronized})
    @Setter (onMethod_ = {@Synchronized})
    private boolean isAvailable = true;

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_heads(uuid VARCHAR(36) NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, tags TEXT NOT NULL, category TEXT NOT NULL, PRIMARY KEY (uuid));")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_heads\"", e);

        }
        return false;
    }

    public double getCost() {
        return Utils.round(Math.max(0, plugin.getConfig().getDouble("heads.cost", 0)), 2);
    }
}
