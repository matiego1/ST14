package me.matiego.st14;

import lombok.Getter;
import lombok.Setter;
import me.matiego.st14.utils.GameTime;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.PlayerTime;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

public class RewardsManager {
    public RewardsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private BukkitTask task;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_rewards\" table in the database.";
    private final HashMap<UUID, Data> cache = new HashMap<>();
    private final int MAX_RFP = 100; //RFP = Reward for playing
    private final long RFP_INTERVAL_MS = 300_000;

    public synchronized void start() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Economy economy = plugin.getEconomy();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Utils.async(() -> {
                    UUID uuid = player.getUniqueId();

                    PlayerTime playerTime = plugin.getTimeManager().getTime(uuid);
                    if (playerTime == null) return;
                    GameTime time = playerTime.getDaily();

                    Data data = cache.get(uuid);
                    if (data == null) return;
                    if (data.getLast() <= 0) {
                        data.setLast(time.getNormal());
                    }

                    int difference = (int) ((time.getNormal() - data.getLast()) / RFP_INTERVAL_MS);
                    if (difference <= 0) return;
                    data.addLast(difference * RFP_INTERVAL_MS);
                    int amount = 5 * difference;

                    int limit = data.getLimit();
                    if (limit >= MAX_RFP) {
                        sendActionBar(player, "&cUzbierałeś dzienny limit pieniędzy za granie");
                        cache.put(uuid, data);
                        return;
                    }

                    limit += amount;
                    limit -= Math.max(0, limit - MAX_RFP);
                    data.setLimit(limit);

                    if (economy.depositPlayer(player, amount).transactionSuccess()) {
                        System.out.println("[DEBUG] Gracz " + player.getName() + " dostał " + amount + " za minuty gry");
                        sendActionBar(player, "&eDostałeś " + economy.format(amount) + " za " + amount + " minut gry");
                        cache.put(uuid, data);
                    }
                });
            }
        }, 20, 100);
    }

    private void sendActionBar(@NotNull Player player, @NotNull String msg) {
        player.sendActionBar(Utils.getComponentByString(msg));
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private @Nullable Data get(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT amount, last FROM st14_rewards WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return new Data(0, 0);
            if (Utils.isDifferentDay(Utils.now(), result.getLong("last"))) return new Data(0, 0);
            return new Data(result.getInt("amount"), 0);
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    private void set(@NotNull UUID uuid, @NotNull Data data) {
        long now = Utils.now();
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_rewards(uuid, amount, last) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE amount = ?, last = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setInt(2, data.getLimit());
            stmt.setLong(3, now);
            stmt.setInt(4, data.getLimit());
            stmt.setLong(5, now);
            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }

    public boolean load(@NotNull UUID uuid) {
        Data data = get(uuid);
        if (data == null) return false;
        cache.put(uuid, data);
        return true;
    }

    public void unload(@NotNull UUID uuid) {
        Data data = cache.remove(uuid);
        if (data == null) return;
        set(uuid, data);
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_rewards(uuid VARCHAR(36) NOT NULL, amount INT NOT NULL, last BIGINT NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_rewards\"", e);
        }
        return false;
    }

    private static class Data {
        public Data(int limit, long last) {
            this.limit = limit;
            this.last = last;
        }
        @Setter @Getter private int limit;
        @Setter @Getter private long last;
        public void addLast(long value) {
            last += value;
        }
    }
}
