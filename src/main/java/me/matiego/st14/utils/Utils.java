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
}
