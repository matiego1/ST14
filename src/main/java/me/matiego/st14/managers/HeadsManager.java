package me.matiego.st14.managers;

import lombok.Getter;
import lombok.Synchronized;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.heads.Head;
import me.matiego.st14.objects.heads.HeadsCategory;
import me.matiego.st14.objects.heads.HeadsGUI;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class HeadsManager {
    public HeadsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_heads\" table in the database.";
    @Getter(onMethod_ = {@Synchronized})
    private boolean available = true;

    public synchronized void setAvailable(boolean available) {
        this.available = available;
        if (!available) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getInventory().getHolder() instanceof HeadsGUI) {
                    player.closeInventory();
                    player.sendMessage(Utils.getComponentByString(Prefix.HEADS + "Pobieranie nowych główek... Otwórz sklep jeszcze raz."));
                }
            }
        }
    }

    public double getCost(@NotNull World world) {
        if (plugin.getConfig().getStringList("heads.free-worlds").contains(world.getName())) return 0;
        return Utils.round(Math.max(0, plugin.getConfig().getDouble("heads.cost", 0)), 2);
    }

    public @Nullable List<Head> findHeadsByName(@NotNull String name) {
        if (!isAvailable()) return null;
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, name, value, category, IFNULL((SELECT GROUP_CONCAT(tag SEPARATOR ',') FROM st14_heads_tags WHERE uuid = st14_heads.uuid), '') as tags FROM st14_heads WHERE name LIKE ? ESCAPE '!'")) {
            name = name.replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                    .replace("[", "![");
            stmt.setString(1, "%" + name + "%");

            ResultSet result = stmt.executeQuery();
            List<Head> heads = new ArrayList<>();
            while (result.next()) {
                try {
                    HeadsCategory category = null;
                    try {
                        category = HeadsCategory.valueOf(result.getString("category"));
                    } catch (IllegalArgumentException ignored) {}
                    if (category == null) return null;

                    heads.add(new Head(
                            UUID.fromString(result.getString("uuid")),
                            result.getString("name"),
                            result.getString("value"),
                            Arrays.asList(result.getString("tags").split(",")),
                            category
                    ));
                } catch (Exception ignored) {}
            }
            return heads;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public @Nullable List<Head> findHeadsByTag(@NotNull String tag) {
        if (!isAvailable()) return null;
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM st14_heads_tags WHERE tag LIKE ? ESCAPE '!'")) {
            tag = tag.replace("!", "!!")
                    .replace("%", "!%")
                    .replace("_", "!_")
                    .replace("[", "![");
            stmt.setString(1, "%" + tag + "%");

            ResultSet result = stmt.executeQuery();
            List<Head> heads = new ArrayList<>();
            while (result.next()) {
                try {
                    Head head = getHeadById(UUID.fromString(result.getString("uuid")));
                    if (head == null) continue;
                    heads.add(head);
                } catch (Exception ignored) {}
            }
            return heads;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    private @Nullable Head getHeadById(@NotNull UUID uuid) {
        if (!isAvailable()) return null;
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT name, value, category, IFNULL((SELECT GROUP_CONCAT(tag SEPARATOR ',') FROM st14_heads_tags WHERE uuid = ?), '') as tags FROM st14_heads WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, uuid.toString());

            ResultSet result = stmt.executeQuery();
            while (result.next()) {
                try {
                    HeadsCategory category = null;
                    try {
                        category = HeadsCategory.valueOf(result.getString("category"));
                    } catch (IllegalArgumentException ignored) {}
                    if (category == null) return null;

                    return new Head(
                            uuid,
                            result.getString("name"),
                            result.getString("value"),
                            Arrays.asList(result.getString("tags").split(",")),
                            category
                    );
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_heads(uuid VARCHAR(36) NOT NULL, name TEXT NOT NULL, value TEXT NOT NULL, category VARCHAR(20) NOT NULL, PRIMARY KEY (uuid));")) {
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_heads_tags(uuid VARCHAR(36) NOT NULL, tag VARCHAR(100) NOT NULL, category VARCHAR(20) NOT NULL, CONSTRAINT st14_heads_tags_const UNIQUE (uuid, tag));")) {
                stmt.execute();
            }
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_heads\"", e);
        }
        return false;
    }
}
