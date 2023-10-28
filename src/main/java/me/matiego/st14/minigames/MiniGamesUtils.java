package me.matiego.st14.minigames;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MiniGamesUtils {
    public static @Nullable World getMiniGamesWorld() {
        return Bukkit.getWorld(Main.getInstance().getConfig().getString("minigames.world", ""));
    }

    public static @Nullable World getMiniGamesSurvivalWorld() {
        return Bukkit.getWorld(Main.getInstance().getConfig().getString("minigames.survival-world", ""));
    }

    public static boolean isInAnyMiniGameWorld(@NotNull Player player) {
        return isAnyMiniGameWorld(player.getWorld());
    }

    public static boolean isAnyMiniGameWorld(@NotNull World world) {
        return world.equals(getMiniGamesWorld()) || world.equals(getMiniGamesSurvivalWorld());
    }

    public static void teleportToLobby(@NotNull Player player) {
        World world = getMiniGamesWorld();
        if (world == null) return;

        if (!isInAnyMiniGameWorld(player)) return;

        player.setBedSpawnLocation(world.getSpawnLocation(), true);
        player.setWorldBorder(null);

        player.teleportAsync(world.getSpawnLocation()).thenAcceptAsync(result -> Utils.sync(() -> {
            if (!result) return;
            healPlayer(player, GameMode.ADVENTURE);
        }));
    }

    public static void healPlayer(@NotNull Player player, @NotNull GameMode gamemode) {
        if (!isInAnyMiniGameWorld(player)) return;

        player.setHealth(20);
        player.setFoodLevel(20);
        player.setSaturation(20);
        player.setExhaustion(0);
        player.setFireTicks(0);
        player.setGameMode(gamemode);
        player.setExp(0);
        player.setLevel(0);
        player.getInventory().clear();
        for (PotionEffect potionEffect : player.getActivePotionEffects()) {
            player.removePotionEffect(potionEffect.getType());
        }
    }

    public static @NotNull CompletableFuture<Boolean> teleportPlayers(@NotNull List<Player> players, @NotNull Location location) {
        List<CompletableFuture<Boolean>> results = new ArrayList<>();
        for (Player player : players) {
            results.add(player.teleportAsync(location));
        }
        CompletableFuture<Boolean> success = new CompletableFuture<>();
        Utils.async(() -> success.complete(results.stream().allMatch(CompletableFuture::join)));
        return success;
    }

    public static void setLobbyRules() {
        World world = getMiniGamesWorld();
        if (world == null) return;

        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
        world.setPVP(false);
    }

    public static @Nullable Location getRelativeLocationFromConfig(@NotNull Location baseLocation, @NotNull String path) {
        Location loc = getLocationFromConfig(baseLocation.getWorld(), path);
        if (loc == null) return null;
        return loc.add(baseLocation);
    }

    public static @Nullable Location getLocationFromConfig(@NotNull World world, @NotNull String path) {
        String value = Main.getInstance().getConfig().getString(path);
        if (value == null) return null;

        return getLocationFromString(world, value);
    }

    @SuppressWarnings("unused")
    public static @Nullable Location getRelativeLocationFromString(@NotNull Location baseLocation, @NotNull String string) {
        Location loc = getLocationFromString(baseLocation.getWorld(), string);
        if (loc == null) return null;
        return loc.add(baseLocation);
    }

    public static @Nullable Location getLocationFromString(@NotNull World world, @NotNull String string) {
        String[] values = string.split(";");
        if (values.length != 3) return null;

        double x, y, z;
        try {
            x = Double.parseDouble(values[0]);
            y = Double.parseDouble(values[1]);
            z = Double.parseDouble(values[2]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new Location(world, x, y, z, 0, 0);
    }

    public static @Nullable ItemStack getItemStackFromString(@NotNull String string) {
        String[] values = string.split(";");
        if (values.length < 3) {
            Logs.warning("invalid item: `" + string + "` (#1)");
            return null;
        }

        Material material = null;
        try {
            material = Material.valueOf(values[0]);
        } catch (Exception ignored) {}
        if (material == null) {
            Logs.warning("invalid item: `" + string + "` (#2)");
            return null;
        }

        int amount = -1;
        try {
            amount = Utils.getRandomNumber(Integer.parseInt(values[1]), Integer.parseInt(values[2]));
        } catch (Exception ignored) {}
        if (amount <= 0) {
            Logs.warning("invalid item: `" + string + "` (#3)");
            return null;
        }

        ItemStack item = new ItemStack(material, amount);

        for (int j = 3; j < values.length; j++) {
            String[] enchantmentValues = values[j].split(",");
            if (enchantmentValues.length != 2) {
                Logs.warning("invalid item: `" + string + "` (#4)");
                return null;
            }
            Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentValues[0]));
            if (enchantment == null) {
                Logs.warning("invalid item: `" + string + "` (#5)");
                return null;
            }
            int level = -1;
            try {
                level = Integer.parseInt(enchantmentValues[1]);
            } catch (Exception ignored) {}
            if (level <= 0) {
                Logs.warning("invalid item: `" + string + "` (#6)");
                return null;
            }
            item.addUnsafeEnchantment(enchantment, level);
        }

        return item;
    }
}
