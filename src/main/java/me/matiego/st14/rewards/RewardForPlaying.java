package me.matiego.st14.rewards;

import me.matiego.st14.Main;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.managers.RankingsManager;
import me.matiego.st14.managers.RewardsManager;
import me.matiego.st14.objects.time.GameTime;
import me.matiego.st14.objects.time.PlayerTime;
import me.matiego.st14.objects.rewards.Reward;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class RewardForPlaying extends Reward {
    public RewardForPlaying(@NotNull Main plugin) {
        super(plugin);
    }

    private final HashMap<UUID, RewardsManager.Data> cache = new HashMap<>();
    private final long INTERVAL_MS = 5 * 60 * 1000;
    private BukkitTask task;

    @Override
    protected @NotNull String getTableSuffix() {
        return "rfp";
    }

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
                    final double max = Math.max(0, Utils.round(plugin.getConfig().getDouble("reward-for-playing.max", 100), 2));
                    if (limit >= max) {
                        sendActionBar(player, "&cUzbierałeś dzienny limit pieniędzy za granie");
                        cache.put(uuid, data);
                        return;
                    }

                    RankingsManager.Data rankingData = RankingsManager.Type.ECONOMY.get(uuid);
                    if (rankingData != null && rankingData.getRank() == 1) {
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
        RewardsManager.Data data = get(uuid);
        if (data == null) return false;
        data.setLast(0);
        cache.put(uuid, data);
        return true;
    }

    public void unloadFromCache(@NotNull UUID uuid) {
        RewardsManager.Data data = cache.remove(uuid);
        if (data != null) set(uuid, data);
    }
}
