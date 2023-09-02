package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.GameTime;
import me.matiego.st14.objects.PlayerTime;
import me.matiego.st14.utils.*;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class IncognitoManager {

    private final Main plugin;
    public IncognitoManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_inc\" table in the database.";
    private final Set<UUID> incognito = new HashSet<>();

    public @NotNull Set<UUID> getIncognitoPlayers() {
        return incognito;
    }

    public synchronized boolean isIncognito(@NotNull UUID uuid) {
        return incognito.contains(uuid);
    }

    public synchronized void setIncognito(@NotNull UUID uuid, boolean value) {
        if (isIncognito(uuid) == value) return;
        Player player = Bukkit.getPlayer(uuid);
        if (value) {
            if (player != null) {
                if (plugin.getAfkManager().isAfk(player)) {
                    plugin.getChatMinecraftManager().sendMessage("Gracz **" + player.getName() + "** już nie jest AFK!", Prefix.AFK.getDiscord());
                }
                plugin.getChatMinecraftManager().sendFakeQuitMessage(player);
                player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Jesteś incognito!"));

                PlayerTime time = plugin.getTimeManager().getTime(uuid);
                if (time != null) time.setType(GameTime.Type.INCOGNITO);
            }
            incognito.add(uuid);
            Logs.info("Gracz " + plugin.getOfflinePlayersManager().getEffectiveNameById(uuid) + " jest incognito.");
        } else {
            if (player != null) {
                plugin.getChatMinecraftManager().sendFakeJoinMessage(player);
                if (plugin.getAfkManager().isAfk(player)) {
                    plugin.getChatMinecraftManager().sendMessage("Gracz **" + player.getName() + "** jest AFK!", Prefix.AFK.getDiscord());
                }
                player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Już nie jesteś incognito!"));

                PlayerTime time = plugin.getTimeManager().getTime(uuid);
                if (time != null) time.setType(plugin.getAfkManager().isAfk(player) ? GameTime.Type.AFK : GameTime.Type.NORMAL);
            }
            incognito.remove(uuid);
            Logs.info("Gracz " + plugin.getOfflinePlayersManager().getEffectiveNameById(uuid) + " nie jest już incognito.");
        }
    }

    public @NotNull List<UUID> getTrustedPlayers(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT trusted FROM st14_inc_trusted WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            List<UUID> uuids = new ArrayList<>();
            ResultSet result = stmt.executeQuery();
            while (result.next()) {
                try {
                    uuids.add(UUID.fromString(result.getString("trusted")));
                } catch (IllegalFormatException ignored) {}
            }
            return uuids;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return new ArrayList<>();
    }

    @SuppressWarnings("UnusedReturnValue")
    public boolean addTrustedPlayer(@NotNull UUID uuid, @NotNull UUID trustedPlayer) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_inc_trusted(uuid, trusted) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = uuid, trusted = trusted")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, trustedPlayer.toString());
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean removeTrustedPlayer(@NotNull UUID uuid, @NotNull UUID trustedPlayer) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_inc_trusted WHERE uuid = ? AND trusted = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, trustedPlayer.toString());
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean isKickingEnabled(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT kicking FROM st14_inc_kicking WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            return result.next() && result.getBoolean("kicking");
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return true; //it's safer fallback value for this method than false
    }

    public boolean setKickingEnabled(@NotNull UUID uuid, boolean value) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_inc_kicking(uuid, kicking) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = uuid, kicking = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setBoolean(2, value);
            stmt.setBoolean(3, value);
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection()) {
            //trusted players
            PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_inc_trusted (uuid VARCHAR(36) NOT NULL, trusted VARCHAR(36) NOT NULL, CONSTRAINT st14_inc_trusted_const UNIQUE (uuid, trusted))");
            stmt.execute();
            stmt.close();
            //kicking mode
            stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_inc_kicking (uuid VARCHAR(36) NOT NULL, kicking BOOL NOT NULL, PRIMARY KEY (uuid))");
            stmt.execute();
            stmt.close();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_inc\"", e);
        }
        return false;
    }
}
