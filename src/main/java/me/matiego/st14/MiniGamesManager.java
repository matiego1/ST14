package me.matiego.st14;

import lombok.Getter;
import lombok.Synchronized;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeEqualityPredicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MiniGamesManager {
    public MiniGamesManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    @Getter (onMethod_ = {@Synchronized}) private MiniGame activeMiniGame = null;
    private BukkitTask task = null;
    private final Set<UUID> editors = new HashSet<>();

    public synchronized boolean startMiniGame(@NotNull MiniGame miniGame, @NotNull Set<Player> players, @NotNull Player sender) {
        players.removeIf(this::isInEditorMode);
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
        setEditorMode(player, false);
        onPlayerJoin0(player);
    }
    public void onPlayerJoin0(@NotNull Player player) {
        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) return;

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
    }

    public void onPlayerQuit(@NotNull Player player) {
        setEditorMode(player, false);
        onPlayerQuit0(player);
    }
    private void onPlayerQuit0(@NotNull Player player) {
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
        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) return;
        if (isInEditorMode(player)) return;

        MiniGame miniGame = getActiveMiniGame();
        if (miniGame == null) return;
        if (!miniGame.isInMiniGame(player)) return;

        try {
            miniGame.onPlayerDeath(player);
        } catch (Exception e) {
            Logs.error("An error occurred while handing game", e);
        }
    }

    public synchronized boolean isInEditorMode(@NotNull Player player) {
        return editors.contains(player.getUniqueId());
    }

    public synchronized void setEditorMode(@NotNull Player player, boolean mode) {
        if (mode == isInEditorMode(player)) return;

        if (mode) {
            onPlayerQuit0(player);
            editors.add(player.getUniqueId());
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Jesteś w trybie edytora."));
        } else {
            editors.remove(player.getUniqueId());
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Już nie jesteś w trybie edytora."));
            onPlayerJoin0(player);
        }
        changePermissions(player);
    }

    private void changePermissions(@NotNull Player player) throws IllegalStateException {
        try {
            String permission = plugin.getConfig().getString("minigames.editor-permission");
            if (permission == null) return;
            LuckPermsProvider.get().getUserManager().modifyUser(player.getUniqueId(), user -> {
                Node node = Node.builder(permission).build();
                NodeMap data = user.data();
                if (data.contains(node, NodeEqualityPredicate.ONLY_KEY).asBoolean()) {
                    data.remove(node);
                } else {
                    data.add(node);
                }
            });
        } catch (Exception ignored) {}
    }
}
