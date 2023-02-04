package me.matiego.st14.utils;

import me.matiego.st14.Main;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
            Logs.warning("An error occurred while running an async task. The task will be run in the same thread.");
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
            Logs.warning("An error occurred while running an sync task. The task will be run in the same thread.");
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

        //months
        int msc = (int) (time / (30L * 3600 * 24 * x));
        time -= (long) msc * 30 * 3600 * 24 * x;
        if (msc != 0) result += msc + "msc ";
        //days
        int d = (int) (time / (3600 * 24 * x));
        time -= (long) d * 3600 * 24 * x;
        if (d != 0) result += d + "d ";
        //hours
        int h = (int) (time / (3600 * x));
        time -= (long) h * 3600 * x;
        if (h != 0) result += h + "h ";
        //minutes
        int m = (int) (time / (60 * x));
        time -= (long) m * 60 * x;
        if (m != 0) result += m + "m ";
        //seconds
        int s = (int) (time / x);
        time -= (long) s * x;
        if (s != 0) result += s + "s ";
        //milliseconds
        int ms = (int) (time);
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

    public static void registerRecipes() {
        //Name tag
        NamespacedKey key = new NamespacedKey(Main.getInstance(), "name_tag");
        ShapedRecipe recipe = new ShapedRecipe(key, new ItemStack(Material.NAME_TAG));
        recipe.shape("  S", " P ", "P  ");
        recipe.setIngredient('S', Material.STRING);
        recipe.setIngredient('P', Material.PAPER);
        Bukkit.addRecipe(recipe);
    }

    public static boolean isDifferentDay(long date1, long date2) {
        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd");
        return !format.format(new Date(date1)).equals(format.format(new Date(date2)));
    }

    public static void kickPlayersAtMidnightTask() {
        LocalTime midnight = LocalTime.of(23, 59, 55);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime task = LocalDateTime.of(now.toLocalDate(), midnight);
        if (!task.isAfter(now)) {
            task = task.plusDays(1);
        }
        long seconds = Duration.between(now.atZone(ZoneId.systemDefault()).toInstant(), task.atZone(ZoneId.systemDefault()).toInstant()).getSeconds();
        Executors.newScheduledThreadPool(1).schedule(() -> Utils.sync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.kick(Utils.getComponentByString("&cNa serwer możesz ponownie dołączyć 3 sekundy po północy. Przepraszamy."));
            }
        }), seconds, TimeUnit.SECONDS);
    }

    public static void broadcastMessage(@NotNull Player player, @NotNull Prefix prefix, @NotNull String self, @NotNull String others, @NotNull String discord) {
        Utils.async(() -> {
            player.sendMessage(Utils.getComponentByString(prefix + self));

            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .forEach(p -> p.sendMessage(Utils.getComponentByString(prefix + others)));
            Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(prefix + others));

            if (Main.getInstance().getIncognitoManager().isIncognito(player.getUniqueId())) return;
            Main.getInstance().getChatMinecraft().sendMessage(discord, prefix.getDiscord());
        });
    }

    public static @NotNull String formatDouble(double number) {
        return (number % 1) == 0 ? String.valueOf((int) number) : String.valueOf(number);
    }
}
