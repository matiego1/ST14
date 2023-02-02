package me.matiego.st14;

import lombok.Getter;
import lombok.Synchronized;
import me.matiego.st14.utils.Game;
import me.matiego.st14.utils.Logs;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;
import java.util.UUID;

public class GameManager {
    public GameManager(@NotNull Main plugin) {
        this.plugin = plugin;
        World lobby = Bukkit.getWorld(plugin.getConfig().getString("games.lobby-world", ""));
        if (lobby != null) {
            lobbyWorld = lobby.getUID();
        } else {
            lobbyWorld = null;
        }
    }

    private final Main plugin;
    private final UUID lobbyWorld;
    @Getter (onMethod_ = {@Synchronized}) private Game activeGame = null;
    private BukkitTask task = null;

    public synchronized @Nullable World getActiveGameWorld() {
        return activeGame == null ? null : activeGame.getWorld();
    }
    public @Nullable World getLobbyWorld() {
        if (lobbyWorld == null) return null;
        return Bukkit.getWorld(lobbyWorld);
    }

    public synchronized boolean startGame(@NotNull Game game, @NotNull Set<Player> players, @NotNull Player sender) {
        if (players.size() < game.getMinimumPlayersAmount()) return false;
        if (players.size() > game.getMaximumPlayersAmount()) return false;
        if (!players.contains(sender)) return false;
        if (activeGame != null) return false;
        activeGame = game;

        try {
            activeGame.startGame(players, sender);
        } catch (Exception e) {
            Logs.error("An error occurred while starting the game", e);
            activeGame = null;
            return false;
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Game g = getActiveGame();
            if (g == null || !g.isStarted()) {
                activeGame = null;
                if (task != null) {
                    task.cancel();
                }
            }
        }, 20, 20);
        return false;
    }

    public void stopGame() {
        stopGame(Bukkit.getConsoleSender());
    }
    public void stopGame(@NotNull CommandSender sender) {
        try {
            activeGame.stopGame(sender);
            if (task != null) {
                task.cancel();
            }
        } catch (Exception e) {
            Logs.error("An error occurred while stoping the game", e);
        }
        activeGame = null;
    }

    public void teleportToLobby(@NotNull Player player) {
        //TODO: teleportToLobby
    }

    public void onPlayerJoin(@NotNull Player player) {
        if (!player.getWorld().equals(getActiveGameWorld())) return;
        Game game = getActiveGame();
        if (game == null) {
            teleportToLobby(player);
            return;
        }
        try {
            game.onPlayerJoin(player);
        } catch (Exception e) {
            Logs.error("An error occurred while handling game", e);
            teleportToLobby(player);
        }
    }

    public void onPlayerQuit(@NotNull Player player) {
        if (!player.getWorld().equals(getActiveGameWorld())) return;
        Game game = getActiveGame();
        if (game == null) return;
        try {
            game.onPlayerQuit(player);
        } catch (Exception e) {
            Logs.error("An error occurred while handing game", e);
        }
    }

    public void onPlayerDeath(@NotNull Player player) {
        if (!player.getWorld().equals(getActiveGameWorld())) return;
        Game game = getActiveGame();
        if (game == null) return;
        try {
            game.onPlayerDeath(player);
        } catch (Exception e) {
            Logs.error("An error occurred while handing game", e);
        }
    }
}
