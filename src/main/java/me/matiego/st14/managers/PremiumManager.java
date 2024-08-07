package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.time.GameTime;
import me.matiego.st14.objects.time.PlayerTime;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class PremiumManager {
    private final Main plugin;
    public PremiumManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_premium\" table in the database.";
    private final Set<UUID> playersToKick = new HashSet<>();

    public boolean isSuperPremium(@NotNull UUID uuid) {
        return plugin.getConfig().getStringList("premium.super-premium-players").contains(uuid.toString());
    }
    public boolean isPremium(@NotNull UUID uuid) {
        return isSuperPremium(uuid) || getRemainingTime(uuid) > 0;
    }

    public long getRemainingTime(@NotNull UUID uuid) {
        return Math.max(0, getEnd(uuid) - Utils.now());
    }
    public long getEnd(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT time FROM st14_premium WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                long time = result.getLong("time");
                return time <= Utils.now() ? 0 : time;
            }
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return 0;
    }

    public boolean extend(@NotNull UUID uuid, @Range(from = 1, to = Long.MAX_VALUE) long time) {
        if (isSuperPremium(uuid)) return false;
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_premium(uuid, time) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = uuid, time = time + ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, Utils.now() + time);
            stmt.setLong(3, time);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean reduce(@NotNull UUID uuid, @Range(from = 1, to = Long.MAX_VALUE) long time) {
        if (isSuperPremium(uuid)) return false;
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE st14_premium SET time = time - ? WHERE uuid = ?")) {
            stmt.setLong(1, time);
            stmt.setString(2, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean set(@NotNull UUID uuid, @Range(from = 1, to = Long.MAX_VALUE) long time) {
        time += Utils.now();
        if (isSuperPremium(uuid)) return false;
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_premium(uuid, time) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = uuid, time = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setLong(2, time);
            stmt.setLong(3, time);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean remove(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_premium WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean makeSpaceForPlayer(@NotNull UUID uuid) {
        int priority = getPriority(uuid);
        List<Player> players = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !playersToKick.contains(p.getUniqueId()))
                .filter(p -> !p.getUniqueId().equals(uuid))
                .filter(p -> getPriority(p.getUniqueId()) < priority)
                .collect(Collectors.toList());
        Player result = null;
        long max = -1;
        for (Player player : players) {
            PlayerTime playerTime = plugin.getTimeManager().getTime(player.getUniqueId());
            if (playerTime == null) continue;
            GameTime gameTime = playerTime.getDaily();
            long time = gameTime.getNormal() + gameTime.getAfk() + gameTime.getIncognito();
            if (time > max) {
                max = time;
                result = player;
            }
        }
        if (result == null) return false;
        kickPlayer(result);
        return true;
    }

    private void kickPlayer(@NotNull Player player) {
        playersToKick.add(player.getUniqueId());

        player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Za 10 sekund zostaniesz wyrzucony z serwera, żeby zrobić miejsce innemu graczowi."));
        player.showTitle(Title.title(Utils.getComponentByString("&6UWAGA!"), Utils.getComponentByString("&ePRZECZYTAJ CZAT")));
        player.playSound(player, Sound.ENTITY_CREEPER_PRIMED, SoundCategory.NEUTRAL, 5, 1);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                playersToKick.remove(player.getUniqueId());
                return;
            }

            player.kick(Utils.getComponentByString(Prefix.PREMIUM + "Zostałeś wyrzucony z serwera, żeby zrobić miejsce graczowi z wyższym priorytetem. Wybór padł na ciebie, ponieważ grałeś dzisiaj najdłużej."));
            playersToKick.remove(player.getUniqueId());

            Utils.broadcastMessage(
                    player,
                    Prefix.PREMIUM,
                    "Za chwilę zostaniesz wyrzucony...",
                    "Gracz " + player.getName() + " został wyrzucony z serwera, żeby zrobić miejsce graczowi z wyższym priorytetem.",
                    "Gracz **" + player.getName() + "** został wyrzucony z serwera, żeby zrobić miejsce graczowi z wyższym priorytetem."
            );
        }, 200);
    }

    private int getPriority(@NotNull UUID uuid) {
        if (isSuperPremium(uuid)) return 2;
        if (isPremium(uuid)) return 1;
        return 0;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_premium(uuid VARCHAR(36) NOT NULL, time BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_premium\"", e);
        }
        return false;
    }
}
