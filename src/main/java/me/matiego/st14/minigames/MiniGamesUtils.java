package me.matiego.st14.minigames;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
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

        player.teleportAsync(world.getSpawnLocation()).thenAcceptAsync(result -> Utils.sync(() -> {
            if (!result) return;
            healPlayer(player, GameMode.ADVENTURE);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 255, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 255, false, false, false));
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

    public static @Nullable Location getLocationFromConfig(@NotNull World world, @NotNull String path) {
        String value = Main.getInstance().getConfig().getString(path);
        if (value == null) return null;

        String[] values = value.split(";");
        if (values.length != 3) return null;

        int x, y, z;
        try {
            x = Integer.parseInt(values[0]);
            y = Integer.parseInt(values[1]);
            z = Integer.parseInt(values[2]);
        } catch (NumberFormatException e) {
            return null;
        }

        return new Location(world, x, y, z, 0, 0);
    }

    public static void pasteSchematic(@NotNull World world, @NotNull BlockVector3 vector, @NotNull File file) throws Exception{
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalStateException("cannot find clipboard format by file");
        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();
            //noinspection deprecation
            try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(world), -1)) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(vector)
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
            }
        }
    }
}
