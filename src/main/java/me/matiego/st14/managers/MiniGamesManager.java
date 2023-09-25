package me.matiego.st14.managers;

import lombok.Getter;
import lombok.Setter;
import lombok.Synchronized;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.data.NodeMap;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.NodeEqualityPredicate;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class MiniGamesManager {
    public MiniGamesManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_minigames\" table in the database.";
    @Nullable
    @Getter (onMethod_ = {@Synchronized})
    private MiniGame activeMiniGame = null;
    private BukkitTask task = null;
    private final Set<UUID> editors = new HashSet<>();
    @Setter
    private boolean serverStopping = false;

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
            if (mg == null || !mg.isMiniGameStarted()) {
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
    private void onPlayerJoin0(@NotNull Player player) {
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
            Utils.broadcastMessage(
                    player,
                    Prefix.MINI_GAMES,
                    "Jesteś w trybie edytora.",
                    "Gracz " + player.getName() + " wszedł w tryb edytora w świecie z minigrami.",
                    "Gracz **" + player.getName() + "** wszedł w tryb edytora w świecie z minigrami."
            );
        } else {
            editors.remove(player.getUniqueId());
            Utils.broadcastMessage(
                    player,
                    Prefix.MINI_GAMES,
                    "Już nie jesteś w trybie edytora.",
                    "Gracz " + player.getName() + " wyszedł z trybu edytora w świecie z minigrami.",
                    "Gracz **" + player.getName() + "** wyszedł z trybu edytora w świecie z minigrami."
            );
            onPlayerJoin0(player);
        }
        changePermissions(player);
    }

    private void changePermissions(@NotNull Player player) {
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

    public void giveRewardToWinner(@NotNull Player player, double reward) {
        if (serverStopping) return;

        Utils.async(() -> increaseWinsAmount(player.getUniqueId()));

        long now = Utils.now();

        RewardsManager.Data data = plugin.getRewardsManager().getRewardForMiniGame().get(player.getUniqueId());
        if (data == null) return;
        if (now - data.getLast() <= 15 * 60 * 1000) return;

        if (reward <= 0) return;

        double limit = data.getLimit();
        final double max = plugin.getConfig().getDouble("minigames.max-winner-reward", 100);
        if (limit >= max) return;
        limit += reward;
        if (limit >= max) {
            reward -= Math.max(0, limit - max);
            limit = max;
        }

        data.setLast(now);
        data.setLimit(limit);
        if (!plugin.getRewardsManager().getRewardForMiniGame().set(player.getUniqueId(), data)) return;

        if (plugin.getEconomyManager().depositPlayer(player, reward).transactionSuccess()) {
            Utils.broadcastMessage(
                    player,
                    Prefix.MINI_GAMES,
                    "Dostałeś " + plugin.getEconomyManager().format(reward) + " za wygraną minigrę!",
                    "Gracz " + player.getName() + " dostał " + plugin.getEconomyManager().format(reward) + " za wygraną minigrę!",
                    "Gracz **" + player.getName() + "** dostał **" + plugin.getEconomyManager().format(reward) + "** za wygraną minigrę!"
            );
        }
    }

    private void increaseWinsAmount(@NotNull UUID uuid) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_minigames(uuid, wins) VALUES(?, 1) ON DUPLICATE KEY UPDATE wins = wins + 1")) {
            stmt.setString(1, uuid.toString());
            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_minigames(uuid VARCHAR(36) NOT NULL, wins INT, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_minigames\"", e);
        }
        return false;
    }
}
