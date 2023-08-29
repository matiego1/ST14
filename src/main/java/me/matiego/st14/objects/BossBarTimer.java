package me.matiego.st14.objects;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class BossBarTimer {
    public BossBarTimer(@NotNull Main plugin, int timeInSeconds, @NotNull String name) {
        this.plugin = plugin;
        this.name = name;
        this.timeInSeconds = timeInSeconds;

        bossBar = BossBar.bossBar(Utils.getComponentByString(""), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
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

    public void setColor(BossBar.Color color) {
        Utils.async(() -> bossBar.color(color));
    }

    private @NotNull String getMessage() {
        return name + " za " + Utils.parseMillisToString(Math.max(0, end - Utils.now()), false);
    }

    private float getProgress() {
        long now = Utils.now();
        float x = (end - now) / (float) (end - begin);
        if (x < 0 || x > 1) Logs.warning("[DEGUB]" + x + " (end=" + end + ", begin=" + begin + ", now=" + now + ")");
        return x;
    }
}
