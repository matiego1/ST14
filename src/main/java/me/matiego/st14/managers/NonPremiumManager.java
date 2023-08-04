package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.utils.NonPremiumUtils;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class NonPremiumManager {
    public NonPremiumManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_non_premium\" table in the database.";
    private final Set<UUID> loggedPlayers = new HashSet<>();
    private static final byte[] HEX_ARRAY = "0123456789ABCDEF".getBytes(StandardCharsets.US_ASCII);

    public synchronized boolean isLoggedIn(@NotNull Player player) {
        if (!NonPremiumUtils.isNonPremiumUuid(player.getUniqueId())) return true; //normal players do not require login
        return loggedPlayers.contains(player.getUniqueId());
    }

    public synchronized void logIn(@NotNull Player player) {
        if (!NonPremiumUtils.isNonPremiumUuid(player.getUniqueId())) return;
        loggedPlayers.add(player.getUniqueId());
    }

    //TODO: execute logOut method somewhere
    public synchronized void logOut(@NotNull Player player) {
        if (!NonPremiumUtils.isNonPremiumUuid(player.getUniqueId())) return;
        loggedPlayers.remove(player.getUniqueId());
    }

    public boolean set(@NotNull UUID uuid, @NotNull String playerName, @NotNull String password) {
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return false;
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_non_premium(uuid, name, password) VALUES (?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, password = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, password);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public @Nullable String getPlayerName(@NotNull UUID uuid) {
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return null;
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password FROM st14_non_premium WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return null;
            return result.getString("name");
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public boolean checkPassword(@NotNull UUID uuid, @NotNull String password) {
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return false;
        try {
            return hashPassword(password).equals(getPasswordHash(uuid));
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private @Nullable String getPasswordHash(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT password FROM st14_non_premium WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return null;
            return result.getString("password");
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    private @NotNull String hashPassword(@NotNull String password) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));

        //https://stackoverflow.com/a/9855338
        byte[] hexChars = new byte[hash.length * 2];
        for (int i = 0; i < hash.length; i++) {
            int v = hash[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars, StandardCharsets.UTF_8);
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_non_premium(uuid VARCHAR(36) NOT NULL, name VARCHAR(36) NOT NULL, password VARCHAR(64) NOT NULL, PRIMARY KEY (uuid), UNIQUE KEY (name))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_non_premium\"", e);
        }
        return false;
    }
}
