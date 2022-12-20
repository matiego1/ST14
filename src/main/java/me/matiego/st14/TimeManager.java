package me.matiego.st14;

import me.matiego.st14.utils.GameTime;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.PlayerTime;
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

    public void join(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        PlayerTime time = PlayerTime.load(uuid);
        if (time == null) return;
        time.setType(plugin.getIncognitoManager().isIncognito(uuid) ? GameTime.Type.INCOGNITO : GameTime.Type.NORMAL);
        cache.put(uuid, time);
    }

    public void quit(@NotNull Player player) {
        PlayerTime time = cache.remove(player.getUniqueId());
        if (time == null) return;
        time.save();
    }

    public @Nullable PlayerTime retrieveTime(@NotNull UUID uuid) {
        return cache.getOrDefault(uuid, PlayerTime.load(uuid));
    }

    public @Nullable PlayerTime getTime(@NotNull UUID uuid) {
        return cache.get(uuid);
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_time(uuid VARCHAR(36) NOT NULL, t_normal INT NOT NULL, t_afk INT NOT NULL, t_incognito INT NOT NULL, normal INT NOT NULL, afk INT NOT NULL, incognito INT NOT NULL, last_save BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_time\"", e);
        }
        return false;
    }
}
