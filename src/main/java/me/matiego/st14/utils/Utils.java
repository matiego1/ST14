package me.matiego.st14.utils;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.json.JSONComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.recipe.CraftingBookCategory;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.text.SimpleDateFormat;
import java.time.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Utils {
    public static Pattern STRING_TO_MILLIS = Pattern.compile("([1-9][0-9]{0,3}d)?(([1-9]|1[0-9]|2[0-3])h)?(([1-9]|[1-5][0-9])m)?(([1-9]|[1-5][0-9])s)?");

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

    public static @NotNull String getJsonByLegacyString(@NotNull String string) {
        return JSONComponentSerializer.json().serialize(Utils.getComponentByString(string));
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

    public static long parseStringToMillis(@NotNull String time) throws IllegalArgumentException {
        Matcher matcher = STRING_TO_MILLIS.matcher(time);
        if (!matcher.matches()) throw new IllegalArgumentException("incorrect time");
        long days = getGroup(matcher, 1, "d");
        long hours = getGroup(matcher, 2, "h");
        long minutes = getGroup(matcher, 4, "m");
        long seconds = getGroup(matcher, 6, "s");

        return (((days * 24 + hours) * 60 + minutes) * 60 + seconds) * 1000;
    }

    private static int getGroup(@NotNull Matcher matcher, int group, @NotNull String c) {
        try {
            return Integer.parseInt(matcher.group(group).replace(c, ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static double getTps() {
        return Math.min(20, Bukkit.getTPS()[0]);
    }

    public static void deleteOldLogFiles() {
        Utils.async(() -> {
            List<File> files = new ArrayList<>();

            try {
                File logsDir = new File(Bukkit.getPluginsFolder().getAbsolutePath().replaceAll("plugins" + Pattern.quote(File.separator) + "*$", "") + "logs");
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
            if (deletedFiles > 0) {
                Logs.info("Successfully deleted " + deletedFiles + " log file(s)!");
            }
        });
    }

    public static void registerRecipes() {
        //Name tag
        ShapedRecipe nameTag = new ShapedRecipe(new NamespacedKey(Main.getInstance(), "name_tag"), new ItemStack(Material.NAME_TAG));
        nameTag.shape("  S", " P ", "P  ");
        nameTag.setIngredient('S', Material.STRING);
        nameTag.setIngredient('P', Material.PAPER);
        nameTag.setCategory(CraftingBookCategory.MISC);
        Bukkit.addRecipe(nameTag);
        //Wool to strings
        ShapelessRecipe woolToStrings = new ShapelessRecipe(new NamespacedKey(Main.getInstance(), "wool_to_strings"), new ItemStack(Material.STRING, 4));
        woolToStrings.addIngredient(Material.WHITE_WOOL);
        woolToStrings.setCategory(CraftingBookCategory.MISC);
        Bukkit.addRecipe(woolToStrings);
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
        player.sendMessage(Utils.getComponentByString(prefix + self));

        Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(player))
                .forEach(p -> p.sendMessage(Utils.getComponentByString(prefix + others)));
        Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(prefix + others));

        if (Main.getInstance().getIncognitoManager().isIncognito(player.getUniqueId())) {
            Logs.discord(discord);
            return;
        }
        Main.getInstance().getChatMinecraftManager().sendMessage(discord, prefix.getDiscord());
    }

    public static @NotNull String formatDouble(double number) {
        return (number % 1) == 0 ? String.valueOf((int) number) : String.valueOf(number);
    }


    public static boolean doesBlockContactPortalBlock(@NotNull Block block) {
        World world = block.getWorld();
        int x = block.getX(), y = block.getY(), z = block.getZ();
        if (world.getBlockAt(x + 1, y, z).getType() == Material.NETHER_PORTAL) return true;
        if (world.getBlockAt(x - 1, y, z).getType() == Material.NETHER_PORTAL) return true;
        if (world.getBlockAt(x, y, z + 1).getType() == Material.NETHER_PORTAL) return true;
        return world.getBlockAt(x, y, z - 1).getType() == Material.NETHER_PORTAL;
    }

    public static boolean checkIfCanNotExecuteCommandInWorld(@NotNull Player player, @NotNull String command) {
        return checkIfCanNotExecuteCommandInWorld(player, command, '-');
    }

    public static boolean checkIfCanNotExecuteCommandInWorld(@NotNull Player player, @NotNull String command, char configPathSeparator) {
        if (player.isOp()) return false;
        if (player.hasPermission("st14." + command + "." + player.getWorld().getName())) return false;
        return !Main.getInstance().getConfig().getStringList(command + configPathSeparator + "worlds").contains(player.getWorld().getName());
    }

    public static int getRandomNumber(int minInclusive, int maxInclusive) {
        return ThreadLocalRandom.current().nextInt(minInclusive, maxInclusive + 1);
    }
}
