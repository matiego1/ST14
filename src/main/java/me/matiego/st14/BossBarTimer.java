package me.matiego.st14;

import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class BossBarTimer {
    public BossBarTimer(@NotNull Main plugin, int timeInSeconds, @NotNull String name, @NotNull BossBar.Color color) {
        this.plugin = plugin;
        this.name = name;
        this.timeInSeconds = timeInSeconds;

        bossBar = BossBar.bossBar(Utils.getComponentByString(""), 1, color, BossBar.Overlay.PROGRESS);
    }
    private final Main plugin;
    private final String name;
    private final BossBar bossBar;
    private final int timeInSeconds;

    private BukkitTask task;
    private long begin;
    private long end;

    public void startTimer() {
        begin = Utils.now();
        end = begin + timeInSeconds * 1000L;

        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (bossBar == null) return;

            String message = getMessage();
            float progress = getProgress();

            Utils.async(() -> {
                bossBar.name(Utils.getComponentByString(message));
                bossBar.progress(progress);
            });
        }, 0, 20);
    }

    public void stopTimerAndHideBossBar() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        Bukkit.getServer().hideBossBar(bossBar);
    }

    public void showBossBarToPlayer(@NotNull Player player) {
        if (bossBar == null) return;
        player.showBossBar(bossBar);
    }

    public void hideBossBarFromPlayer(@NotNull Player player) {
        if (bossBar == null) return;
        player.hideBossBar(bossBar);
    }

    private @NotNull String getMessage() {
        return name + " za " + Utils.parseMillisToString(Math.max(0, end - Utils.now()), false);
    }

    private float getProgress() {
        return (end - Utils.now()) / (float) (end - begin);
    }
}
