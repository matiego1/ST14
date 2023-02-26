package me.matiego.st14;

import lombok.Getter;
import lombok.Synchronized;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Logs;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

public class MiniGameManager {
    public MiniGameManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    @Getter (onMethod_ = {@Synchronized}) private MiniGame activeMiniGame = null;
    private BukkitTask task = null;

    public synchronized @Nullable World getActiveMiniGameWorld() {
        return activeMiniGame == null ? null : activeMiniGame.getWorld();
    }

    public synchronized boolean startMiniGame(@NotNull MiniGame miniGame, @NotNull Set<Player> players, @NotNull Player sender) {
        if (players.size() < miniGame.getMinimumPlayersAmount()) return false;
        if (players.size() > miniGame.getMaximumPlayersAmount()) return false;
        if (!players.contains(sender)) return false;
        if (activeMiniGame != null) return false;
        activeMiniGame = miniGame;

        try {
            activeMiniGame.startMiniGame(players, sender);
        } catch (Exception e) {
            Logs.error("An error occurred while starting the game: " + e.getMessage(), e);
            activeMiniGame = null;
            MiniGamesUtils.setLobbyRules();
            return false;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            MiniGame mg = getActiveMiniGame();
            if (mg == null || !mg.isStarted()) {
                activeMiniGame = null;
                if (task != null) {
                    task.cancel();
                }
                MiniGamesUtils.setLobbyRules();
            }
        }, 20, 20);
        return true;
    }

    public void stopMiniGame() {
        if (activeMiniGame == null) return;
        try {
            activeMiniGame.stopMiniGame();
            if (task != null) {
                task.cancel();
            }
        } catch (Exception e) {
            Logs.error("An error occurred while stoping the game", e);
        }
        activeMiniGame = null;
        MiniGamesUtils.setLobbyRules();
    }



    public void onPlayerJoin(@NotNull Player player) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!MiniGamesUtils.isInMinigameWorldOrLobby(player)) return;

            MiniGame miniGame = getActiveMiniGame();
            if (miniGame == null) {
                MiniGamesUtils.teleportToLobby(player);
                return;
            }
            if (miniGame.isInMiniGame(player)) return;

            try {
                miniGame.onPlayerJoin(player);
            } catch (Exception e) {
                Logs.error("An error occurred while handling the game", e);
                MiniGamesUtils.teleportToLobby(player);
            }
        }, 1);
    }

    public void onPlayerQuit(@NotNull Player player) {
        MiniGame miniGame = getActiveMiniGame();
        if (miniGame == null) return;
        if (!miniGame.isInMiniGame(player)) return;

        try {
            miniGame.onPlayerQuit(player);
        } catch (Exception e) {
            Logs.error("An error occurred while handing game", e);
        }
    }

    public void onPlayerDeath(@NotNull Player player) {
        if (!MiniGamesUtils.isInMinigameWorldOrLobby(player)) return;

        MiniGame miniGame = getActiveMiniGame();
        if (miniGame == null) return;
        if (!miniGame.isInMiniGame(player)) return;

        try {
            miniGame.onPlayerDeath(player);
        } catch (Exception e) {
            Logs.error("An error occurred while handing game", e);
        }
    }
}
