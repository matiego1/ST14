package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.bans.Ban;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class BansManager {
    public BansManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_bans\" table in the database.";

    public boolean isBanned(@NotNull UUID uuid) {
        Ban ban = getBan(uuid);
        return ban != null && ban.isActive();
    }

    public @Nullable Ban getBan(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT reason, expiration FROM st14_bans WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return new Ban(uuid);
            return new Ban(uuid, result.getString("reason"), result.getLong("expiration"));
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public boolean setBan(@NotNull Ban ban) {
        if (ban.isActive()) {
            Player player = Bukkit.getPlayer(ban.getUuid());
            if (player != null) {
                Utils.sync(() -> player.kick(Utils.getComponentByString(getKickMessage(ban))));
            }
        }
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_bans(uuid, reason, expiration) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE reason = ?, expiration = ?")) {
            stmt.setString(1, ban.getUuid().toString());
            stmt.setString(2, ban.getReason());
            stmt.setLong(3, ban.getExpiration());
            stmt.setString(4, ban.getReason());
            stmt.setLong(5, ban.getExpiration());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public @NotNull String getKickMessage(@Nullable Ban ban) {
        if (ban == null) {
            return "&c&lJesteś zbanowany!&r\n" +
                    "\n" +
                    "&7Niestety nie udało się wczytać więcej informacji o twoim banie.\n" +
                    "Przepraszamy.";
        }
        return "&c&lJesteś zbanowany!&r\n" +
                "\n" +
                "&cPozostały czas: &7" + Utils.parseMillisToString(ban.getExpiration() - Utils.now(), false) + "\n" +
                "&cPowód: &7" + ban.getReason() + "\n" +
                "\n" +
                "&8Odwołanie możesz złożyć na serwerze Discord.";
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_bans(uuid VARCHAR(36) NOT NULL, reason TEXT NOT NULL, expiration BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_bans\"", e);
        }
        return false;
    }
}
