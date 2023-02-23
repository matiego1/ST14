package me.matiego.st14.minigames;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class MiniGamesUtils {
    public static @Nullable World getLobbyWorld() {
        return Bukkit.getWorld(Main.getInstance().getConfig().getString("games.lobby-world", ""));
    }

    public static boolean isInMinigameWorldOrLobby(@NotNull Player player) {
        return player.getWorld().equals(getLobbyWorld()) || player.getWorld().equals(Main.getInstance().getMiniGameManager().getActiveGameWorld());
    }

    public static void teleportToLobby(@NotNull Player player) {
        World world = getLobbyWorld();
        if (world == null) return;

        if (!isInMinigameWorldOrLobby(player)) return;

        player.setBedSpawnLocation(world.getSpawnLocation());

        player.teleportAsync(world.getSpawnLocation()).thenAcceptAsync(result -> {
            if (!result) return;
            healPlayer(player, GameMode.ADVENTURE);
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, Integer.MAX_VALUE, 255, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, Integer.MAX_VALUE, 255, false, false, false));
        });
    }

    public static void healPlayer(@NotNull Player player, @NotNull GameMode gamemode) {
        if (!isInMinigameWorldOrLobby(player)) return;

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
        World world = getLobbyWorld();
        if (world == null) return;

        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, true);
        world.setPVP(false);
    }
}
