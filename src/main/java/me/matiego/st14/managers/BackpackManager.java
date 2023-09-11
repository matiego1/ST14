package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.FixedSizeMap;
import me.matiego.st14.objects.GUI;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BackpackManager {
    public BackpackManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final FixedSizeMap<UUID, String> cache = new FixedSizeMap<>(50);
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_backpack\" table in the database.";

    public @Nullable List<ItemStack> loadBackpack(@NotNull UUID uuid) {
        String cached = cache.get(uuid);
        if (cached != null) {
            return GUI.stringToItems(cached);
        }

        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT inv FROM st14_backpack WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return new ArrayList<>();
            String string = result.getString("inv");
            return GUI.stringToItems(string == null ? "" : string);
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public boolean saveBackpack(@NotNull UUID uuid, @NotNull List<ItemStack> itemStacks) {
        String string = GUI.itemsToString(itemStacks);
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_backpack(uuid, inv) VALUES (?, ?) ON DUPLICATE KEY UPDATE inv = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, string);
            stmt.setString(3, string);
            if (stmt.executeUpdate() > 0) return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public void clearCache(@NotNull UUID uuid) {
        cache.remove(uuid);
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_backpack(uuid VARCHAR(36) NOT NULL, inv TEXT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_backpack\"", e);
        }
        return false;
    }
}
