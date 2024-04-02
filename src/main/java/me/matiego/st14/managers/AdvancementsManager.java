package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Objective;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class AdvancementsManager {
    public AdvancementsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_advancements\" table in the database.";

    public void updateAmount(@NotNull Player player) {
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " run function bc_rewards:st14_score");

        Objective objective = player.getScoreboard().getObjective("st14_advancements");
        if (objective == null) {
            Logs.error("Scoreboard st14_advancements is not created! Advancements ranking will not work.");
            return;
        }
        int amount = objective.getScore(player).getScore();

        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_advancements(uuid, amount) VALUES (?, ?) ON DUPLICATE KEY UPDATE amount = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setInt(2, amount);
            stmt.setInt(3, amount);
            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_advancements(uuid VARCHAR(36) NOT NULL, amount INT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_advancements\"", e);
        }
        return false;
    }
}
