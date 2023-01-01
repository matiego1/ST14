package me.matiego.st14.utils;

import me.matiego.st14.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

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
        time -= (long) m * 60 * x;
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

    public static double getTps() {
        return Math.min(20, Bukkit.getTPS()[0]);
    }

    public static void deleteOldLogFiles() {
        Utils.async(() -> {
            Logs.info("Starting cleaning up old server logs...");
            List<File> files = new ArrayList<>();

            try {
                File logsDir = new File(Bukkit.getPluginsFolder().getAbsolutePath().replaceAll("plugins" + Pattern.quote(File.pathSeparator) + "*$", "") + "logs");
                if (!logsDir.isDirectory()) return;
                for (File file : Objects.requireNonNull(logsDir.listFiles())) {
                    if (!file.isDirectory()) files.add(file);
                }
            } catch (Exception e) {
                Logs.error("An error occurred while cleaning up old server logs", e);
                return;
            }

            int deletedFiles = 0;
            for (File file : files) {
                try {
                    //adds 21 days to date from file's name.
                    Instant date = LocalDateTime.ofInstant(new SimpleDateFormat("yyyy-MM-dd").parse(file.getName().substring(0, 10)).toInstant(), ZoneId.systemDefault()).plusDays(21).atZone(ZoneId.systemDefault()).toInstant();
                    if (date.isBefore(Instant.now())) {
                        if (file.delete()) {
                            deletedFiles++;
                        }
                    }
                } catch (Exception ignored) {}
            }
            if (deletedFiles == 0) {
                Logs.info("No log file has been deleted.");
            } else {
                Logs.info("Successfully deleted " + deletedFiles + " log file(s)!");
            }
        });
    }


}
