package me.matiego.st14.objects.time;

import lombok.Getter;
import lombok.Synchronized;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class PlayerTime {
    private PlayerTime(@NotNull UUID uuid, @NotNull GameTime total, @NotNull GameTime daily, long lastOnline, long fakeLastOnline) {
        this.uuid = uuid;
        this.total = total;
        this.daily = daily;
        this.lastOnline = lastOnline;
        this.fakeLastOnline = fakeLastOnline;
    }

    private final UUID uuid;
    private final GameTime total;
    private final GameTime daily;
    private GameTime session = GameTime.empty();
    private GameTime fakeSession = GameTime.empty();
    private GameTime current = GameTime.empty();
    private long lastOnline;
    private long fakeLastOnline;

    private long startOfCurrentType = 0;
    @Getter(onMethod_ = {@Synchronized}) private GameTime.Type type = null;

    private long getTimeOfCurrentType() {
        return startOfCurrentType == 0 ? 0 : Utils.now() - startOfCurrentType;
    }
    private void updateCurrent() {
        if (type == null) return;
        switch (type) {
            case NORMAL -> current.setNormal(getTimeOfCurrentType());
            case AFK -> current.setAfk(getTimeOfCurrentType());
            case INCOGNITO -> current.setIncognito(getTimeOfCurrentType());
        }
    }

    public synchronized @NotNull GameTime getTotal() {
        return GameTime.add(total, getSession());
    }
    public synchronized @NotNull GameTime getDaily() {
        return GameTime.add(daily, getSession());
    }
    public synchronized @NotNull GameTime getSession() {
        updateCurrent();
        return GameTime.add(session, current);
    }
    public synchronized @NotNull GameTime getFakeSession() {
        if (getType() == GameTime.Type.INCOGNITO) return GameTime.empty();
        updateCurrent();
        GameTime time = GameTime.add(fakeSession, current);
        time.setIncognito(0);
        return time;
    }
    public synchronized long getLastOnline() {
        return type == null ? lastOnline : Utils.now();
    }
    public synchronized long getFakeLastOnline() {
        return type == null || type == GameTime.Type.INCOGNITO ? fakeLastOnline : Utils.now();
    }

    public synchronized void setType(@NotNull GameTime.Type newType) {
        if (type == newType) return;
        updateCurrent();
        session = GameTime.add(session, current);
        fakeSession = GameTime.add(fakeSession, current);
        current = GameTime.empty();
        if (type == GameTime.Type.INCOGNITO || newType == GameTime.Type.INCOGNITO) {
            fakeSession = GameTime.empty();
        }
        boolean shouldUpdateFakeLastOnline = type != null && newType == GameTime.Type.INCOGNITO;
        type = newType;
        startOfCurrentType = Utils.now();
        if (shouldUpdateFakeLastOnline) {
            fakeLastOnline = startOfCurrentType;
        }
    }

    public static @Nullable PlayerTime load(@NotNull UUID uuid) {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT t_normal, t_afk, t_incognito, normal, afk, incognito, last_online, fake_last_online, last_save FROM st14_time WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                GameTime daily = new GameTime(result.getLong("normal"), result.getLong("afk"), result.getLong("incognito"));
                if (Utils.isDifferentDay(result.getLong("last_save"), Utils.now())) {
                    daily = GameTime.empty();
                }

                return new PlayerTime(
                        uuid,
                        new GameTime(result.getLong("t_normal"), result.getLong("t_afk"), result.getLong("t_incognito")),
                        daily,
                        result.getLong("last_online"),
                        result.getLong("fake_last_online")
                );
            }
            return new PlayerTime(
                    uuid,
                    GameTime.empty(),
                    GameTime.empty(),
                    0,
                    0
            );
        } catch (SQLException e) {
            Logs.error("An error occurred while modifying values in \"st14_time\" table in the database.", e);
        }
        return null;
    }

    public synchronized void save() {
        if (type == null) return;

        updateCurrent();

        long now = Utils.now();

        lastOnline = now;
        if (type != GameTime.Type.INCOGNITO) {
            fakeLastOnline = now;
        }

        type = null;
        startOfCurrentType = 0;

        GameTime total = getTotal();
        GameTime daily = getDaily();
        fakeSession = GameTime.empty();
        session = GameTime.empty();
        current = GameTime.empty();

        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_time(uuid, t_normal, t_afk, t_incognito, normal, afk, incognito, last_online, fake_last_online, last_save) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE t_normal = ?, t_afk = ?, t_incognito = ?, normal = ?, afk = ?, incognito = ?, last_online = ?, fake_last_online = ?, last_save = ?")) {
            stmt.setString(1, uuid.toString());

            stmt.setLong(2, total.getNormal());
            stmt.setLong(3, total.getAfk());
            stmt.setLong(4, total.getIncognito());
            stmt.setLong(5, daily.getNormal());
            stmt.setLong(6, daily.getAfk());
            stmt.setLong(7, daily.getIncognito());
            stmt.setLong(8, lastOnline);
            stmt.setLong(9, fakeLastOnline);
            stmt.setLong(10, now);

            stmt.setLong(11, total.getNormal());
            stmt.setLong(12, total.getAfk());
            stmt.setLong(13, total.getIncognito());
            stmt.setLong(14, daily.getNormal());
            stmt.setLong(15, daily.getAfk());
            stmt.setLong(16, daily.getIncognito());
            stmt.setLong(17, lastOnline);
            stmt.setLong(18, fakeLastOnline);
            stmt.setLong(19, now);

            stmt.execute();
        } catch (SQLException e) {
            Logs.error("An error occurred while modifying values in \"st14_time\" table in the database.", e);
        }
    }
}