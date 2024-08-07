package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.objects.time.GameTime;
import me.matiego.st14.Logs;
import me.matiego.st14.objects.time.PlayerTime;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

public class TimeManager {
    private final Main plugin;
    public TimeManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final HashMap<UUID, PlayerTime> cache = new HashMap<>();

    public boolean join(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        PlayerTime time = PlayerTime.load(uuid);
        if (time == null) return false;
        time.setType(plugin.getIncognitoManager().isIncognito(uuid) ? GameTime.Type.INCOGNITO : GameTime.Type.NORMAL);
        cache.put(uuid, time);
        return true;
    }

    public void quit(@NotNull Player player) {
        PlayerTime time = cache.remove(player.getUniqueId());
        if (time == null) return;
        time.save();
    }

    public @Nullable PlayerTime retrieveTime(@NotNull UUID uuid) {
        PlayerTime time = cache.get(uuid);
        if (time != null) return time;
        return PlayerTime.load(uuid);
    }

    public @Nullable PlayerTime getTime(@NotNull UUID uuid) {
        return cache.get(uuid);
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_time(uuid VARCHAR(36) NOT NULL, t_normal BIGINT NOT NULL, t_afk BIGINT NOT NULL, t_incognito BIGINT NOT NULL, normal BIGINT NOT NULL, afk BIGINT NOT NULL, incognito BIGINT NOT NULL, last_online BIGINT NOT NULL, fake_last_online BIGINT NOT NULL, last_save BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_time\"", e);
        }
        return false;
    }
}
