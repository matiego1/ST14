package me.matiego.st14.utils;

import lombok.Getter;
import lombok.Synchronized;
import me.matiego.st14.Main;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.UUID;

public class PlayerTime {
    private PlayerTime(UUID uuid, GameTime total, GameTime daily) {
        this.uuid = uuid;
        this.total = total;
        this.daily = daily;
    }

    private final UUID uuid;
    private final GameTime total;
    private final GameTime daily;
    private GameTime current = new GameTime(0, 0, 0);
    private GameTime fake = new GameTime(0, 0, 0);

    private long startOfCurrentType = 0;
    @Getter(onMethod_ = {@Synchronized}) private GameTime.Type type = null;

    private int getTimeOfCurrentType() {
        return startOfCurrentType == 0 ? 0 : (int) (Utils.now() - startOfCurrentType) / 1000;
    }
    private void updateCurrent() {
        if (type == null) return;
        switch (type) {
            case NORMAL -> {
                current.setNormal(getTimeOfCurrentType());
                fake.setNormal(getTimeOfCurrentType());
            }
            case AFK -> {
                current.setAfk(getTimeOfCurrentType());
                fake.setAfk(getTimeOfCurrentType());
            }
            case INCOGNITO -> current.setIncognito(getTimeOfCurrentType());
        }
    }

    public synchronized @NotNull GameTime getTotal() {
        return GameTime.add(total, getCurrent());
    }
    public synchronized @NotNull GameTime getDaily() {
        return GameTime.add(daily, getCurrent());
    }
    public synchronized @NotNull GameTime getCurrent() {
        updateCurrent();
        return current;
    }
    public synchronized @NotNull GameTime getFakeCurrent() {
        updateCurrent();
        return fake;
    }

    public synchronized void setType(@NotNull GameTime.Type newType) {
        if (type == newType) return;
        if (type == GameTime.Type.INCOGNITO) {
            fake = new GameTime(0, 0, 0);
        }
        updateCurrent();
        type = newType;
        startOfCurrentType = Utils.now();
    }

    public static @Nullable PlayerTime load(@NotNull UUID uuid) {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT t_normal, t_afk, t_incognito, normal, afk, incognito, last_save FROM st14_time WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
                String lastSave = format.format(new Date(result.getLong("last_save")));
                String now = format.format(new Date(Utils.now()));

                GameTime daily = new GameTime(result.getInt("normal"), result.getInt("afk"), result.getInt("incognito"));
                if (!lastSave.equals(now)) {
                    daily = new GameTime(0, 0, 0);
                }

                return new PlayerTime(
                        uuid,
                        new GameTime(result.getInt("t_normal"), result.getInt("t_afk"), result.getInt("t_incognito")),
                        daily
                );
            }
            return new PlayerTime(
                    uuid,
                    new GameTime(0, 0, 0),
                    new GameTime(0, 0, 0)
            );
        } catch (SQLException e) {
            Logs.error("An error occurred while modifying values in \"st14_time\" table in the database.", e);
        }
        return null;
    }

    public synchronized void save() {
        updateCurrent();
        type = null;
        startOfCurrentType = 0;
        GameTime total = getTotal();
        GameTime daily = getDaily();
        fake = new GameTime(0, 0, 0);
        current = new GameTime(0, 0, 0);

        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_time(uuid, t_normal, t_afk, t_incognito, normal, afk, incognito, last_save) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE t_normal = ?, t_afk = ?, t_incognito = ?, normal = ?, afk = ?, incognito = ?, last_save = ?")) {
            stmt.setString(1, uuid.toString());

            stmt.setInt(2, total.getNormal());
            stmt.setInt(3, total.getAfk());
            stmt.setInt(4, total.getIncognito());
            stmt.setInt(5, daily.getNormal());
            stmt.setInt(6, daily.getAfk());
            stmt.setInt(7, daily.getIncognito());
            stmt.setLong(8, Utils.now());

            stmt.setInt(9, total.getNormal());
            stmt.setInt(10, total.getAfk());
            stmt.setInt(11, total.getIncognito());
            stmt.setInt(12, daily.getNormal());
            stmt.setInt(13, daily.getAfk());
            stmt.setInt(14, daily.getIncognito());
            stmt.setLong(15, Utils.now());

            stmt.execute();
        } catch (SQLException e) {
            Logs.error("An error occurred while modifying values in \"st14_time\" table in the database.", e);
        }
    }
}