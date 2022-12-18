package me.matiego.st14.utils;

import me.matiego.st14.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class Utils {

    /**
     * Runs the given task async.
     * @param task the task
     */
    public static void async(@NotNull Runnable task) {
        try {
            Bukkit.getScheduler().runTaskAsynchronously(Main.getInstance(), task);
        } catch (IllegalPluginAccessException e) {
            Logs.warning("An error occurred while running an async task. The task will be run synchronously.");
            task.run();
        }
    }

    /**
     * Runs the given task sync.
     * @param task the task
     */
    public static void sync(@NotNull Runnable task) {
        try {
            Bukkit.getScheduler().runTask(Main.getInstance(), task);
        } catch (IllegalPluginAccessException e) {
            Logs.warning("An error occurred while running an sync task. The task will be run asynchronously.");
            task.run();
        }
    }

    public static @NotNull Component getComponentByString(@NotNull String string) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(string).asComponent();
    }

    public static double round(double number, int decimalPlaces) {
        return (double) Math.round(number * Math.pow(10, decimalPlaces)) / Math.pow(10, decimalPlaces);
    }

    public static @NotNull String getWorldName(@NotNull World world) {
        return Main.getInstance().getConfig().getString("world-name."+ world.getName(), world.getName());
    }

    public static @NotNull String getWorldPrefix(@NotNull World world) {
        return Main.getInstance().getConfig().getString("world-prefix."+ world.getName(), world.getName());
    }

    public static @NotNull String getSkinUrl(@NotNull UUID uuid) {
        return "https://mc-heads.net/avatar/" + uuid + ".png";
    }

    public static long now() {
        return System.currentTimeMillis();
    }

    public static @NotNull String parseMillisToString(long time, boolean useMilliseconds) {
        time = Math.round((double) time / (useMilliseconds ? 1 : 1000));
        int x = useMilliseconds ? 1000 : 1;
        String result = "";

        //days
        int d = (int) time / (3600 * 24 * x);
        time -= (long) d * 3600 * 24 * x;
        if (d != 0) result += d + "d ";
        //hours
        int h = (int) time / (3600 * x);
        time -= (long) h * 3600 * x;
        if (h != 0) result += h + "h ";
        //minutes
        int m = (int) time / (60 * x);
        time -= (long) m * 3600 * x;
        if (m != 0) result += m + "m ";
        //seconds
        int s = (int) time / x;
        time -= (long) s * x;
        if (s != 0) result += s + "s ";
        //milliseconds
        int ms = (int) time;
        if (ms != 0) result += ms + "ms ";

        if (result.isEmpty()) return useMilliseconds ? "0ms" : "0s";
        return result.substring(0, result.length() - 1);
    }
}
