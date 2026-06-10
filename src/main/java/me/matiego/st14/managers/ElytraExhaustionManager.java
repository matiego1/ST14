package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public class ElytraExhaustionManager {
    public ElytraExhaustionManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final HashMap<UUID, Long> startTimes = new HashMap<>();
    private BukkitTask task;

    public void start() {
        stop();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = Utils.now();

            Iterator<Map.Entry<UUID, Long>> it = startTimes.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Long> entry = it.next();
                Player player = Bukkit.getPlayer(entry.getKey());

                if (player == null) {
                    it.remove();
                    continue;
                }

                applyExhaustion(player, entry.getValue(), now);

                if (player.isGliding()) entry.setValue(now);
                else it.remove();
            }
        }, 20, 20);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public void flightBeginning(@NotNull Player player) {
        startTimes.put(player.getUniqueId(), Utils.now());
    }

    public void flightEnd(@NotNull Player player) {
        Long start = startTimes.remove(player.getUniqueId());
        if (start == null) return;
        applyExhaustion(player, start, Utils.now());
    }

    private void applyExhaustion(@NotNull Player player, long start, long now) {
        double duration = (now - start) / 1000d;
        float exhaustion = (float) (plugin.getConfig().getDouble("elytra.glide-exhaustion", 0.5d) * duration);

        player.setExhaustion(player.getExhaustion() + exhaustion);
    }
}
