package me.matiego.st14.rewards;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.managers.RewardsManager;
import me.matiego.st14.times.GameTime;
import me.matiego.st14.times.PlayerTime;
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

public class RewardForPlaying {
    public RewardForPlaying(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final HashMap<UUID, RewardsManager.Data> cache = new HashMap<>();
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_rewards_rfp\" table in the database.";
    private final long INTERVAL_MS = 5 * 60 * 1000;
    private BukkitTask task;

    public synchronized void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            EconomyManager economy = plugin.getEconomyManager();
            for (Player player : Bukkit.getOnlinePlayers()) {
                Utils.async(() -> {
                    UUID uuid = player.getUniqueId();

                    PlayerTime playerTime = plugin.getTimeManager().getTime(uuid);
                    if (playerTime == null) return;
                    GameTime time = playerTime.getDaily();

                    RewardsManager.Data data = cache.get(uuid);
                    if (data == null) return;
                    if (data.getLast() <= 0) {
                        data.setLast(time.getNormal());
                    }

                    int difference = (int) ((time.getNormal() - data.getLast()) / INTERVAL_MS);
                    if (difference <= 0) return;
                    data.addLast(difference * INTERVAL_MS);
                    double amount = plugin.getConfig().getDouble("reward-for-playing.amount", 5) * difference;

                    double limit = data.getLimit();
                    final double max = getMaxRFP();
                    if (limit >= max) {
                        sendActionBar(player, "&cUzbierałeś dzienny limit pieniędzy za granie");
                        cache.put(uuid, data);
                        return;
                    }

                    limit += amount;
                    if (limit >= max) {
                        amount -= Math.max(0, limit - max);
                        limit = max;
                    }
                    data.setLimit(limit);

                    if (economy.depositPlayer(player, amount).transactionSuccess()) {
                        sendActionBar(player, "&eDostałeś " + economy.format(amount) + " za " + (difference * INTERVAL_MS / 1000 / 60) + " minut gry");
                        cache.put(uuid, data);
                    }
                });
            }
        }, 20, 100);
    }

    private double getMaxRFP() {
        double max = Utils.round(plugin.getConfig().getDouble("reward-for-playing.max"), 2);
        return max <= 0 ? 100 : max;
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

    public boolean loadToCache(@NotNull UUID uuid) {
        RewardsManager.Data rfp = get(uuid);
        if (rfp == null) return false;
        cache.put(uuid, rfp);
        return true;
    }

    public void unloadFromCache(@NotNull UUID uuid) {
        RewardsManager.Data rfp = cache.remove(uuid);
        if (rfp != null) set(uuid, rfp);
    }

    private @Nullable RewardsManager.Data get(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT amount, last FROM st14_rewards_rfp WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (!result.next()) return new RewardsManager.Data(0, 0);
            if (Utils.isDifferentDay(Utils.now(), result.getLong("last"))) return new RewardsManager.Data(0, 0);
            return new RewardsManager.Data(result.getDouble("amount"), 0);
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    private void set(@NotNull UUID uuid, @NotNull RewardsManager.Data data) {
        long now = Utils.now();
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_rewards_rfp(uuid, amount, last) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE amount = ?, last = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setDouble(2, data.getLimit());
            stmt.setLong(3, now);
            stmt.setDouble(4, data.getLimit());
            stmt.setLong(5, now);
            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }
}
