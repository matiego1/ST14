package me.matiego.st14.objects.rewards;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.RewardsManager;
import me.matiego.st14.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public abstract class Reward {
    public Reward(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    protected final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_rewards_" + getTableSuffix() + "\" table in the database.";

    protected abstract @NotNull String getTableSuffix();

    public @Nullable RewardsManager.Data get(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT amount, last FROM st14_rewards_" + getTableSuffix() + " WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return new RewardsManager.Data(0, 0);
            if (Utils.isDifferentDay(Utils.now(), result.getLong("last"))) return new RewardsManager.Data(0, 0);
            return new RewardsManager.Data(result.getDouble("amount"), result.getLong("last"));
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public boolean set(@NotNull UUID uuid, @NotNull RewardsManager.Data data) {
        long now = Utils.now();
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_rewards_" + getTableSuffix() + "(uuid, amount, last) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE amount = ?, last = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, data.getLimit());
            stmt.setLong(3, now);

            stmt.setDouble(4, data.getLimit());
            stmt.setLong(5, now);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }
}
