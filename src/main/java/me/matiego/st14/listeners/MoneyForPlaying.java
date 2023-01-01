package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.GameTime;
import me.matiego.st14.utils.PlayerTime;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class MoneyForPlaying implements Listener {
    public MoneyForPlaying(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private BukkitTask task;
    private final HashMap<UUID, Long> lastTime = new HashMap<>();
    private final HashMap<UUID, Integer> limit = new HashMap<>();

    public synchronized void start() {
        if (task != null) {
            task.cancel();
        }
        limit.clear();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();

                PlayerTime playerTime = plugin.getTimeManager().getTime(uuid);
                if (playerTime == null) continue;
                if (playerTime.getType() != GameTime.Type.NORMAL) continue;

                long time = playerTime.getDaily().getNormal();
                if (lastTime.getOrDefault(uuid, time - 1) == time) continue;
                lastTime.put(uuid, time);

                if (time == 0) continue;
                if (time % 300_000 != 0) continue;

                int amount = limit.getOrDefault(uuid, 0);
                if (amount >= 10) {
                    player.sendActionBar(Utils.getComponentByString("&cUzbierałeś dzienny limit pieniędzy za granie."));
                    continue;
                }
                if (plugin.getEconomy().depositPlayer(player, 5d).transactionSuccess()) {
                    player.sendActionBar(Utils.getComponentByString("&eDostałeś 5$ za przegrane 5 minut."));
                    limit.put(uuid, amount + 1);
                }
            }
        }, 20, 60);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        lastTime.remove(event.getPlayer().getUniqueId());
    }
}
