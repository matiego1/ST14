package me.matiego.st14.minigames;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class MiniGame {
    public MiniGame(@NotNull Main plugin, int gameTimeInSeconds) {
        this.plugin = plugin;
        this.gameTimeInSeconds = gameTimeInSeconds;
    }
    protected final Main plugin;
    protected final int gameTimeInSeconds;
    protected boolean isMiniGameStarted = false;
    protected boolean lobby = true;
    private final Set<BukkitTask> tasks = new HashSet<>();
    private final HashMap<Player, PlayerStatus> players = new HashMap<>();
    protected BossBarTimer timer;

    protected synchronized void changePlayerStatus(@NotNull Player player, @NotNull PlayerStatus status) {
        if (status == PlayerStatus.NOT_IN_MINI_GAME) {
            players.remove(player);
        } else {
            players.put(player, status);
        }
    }

    protected synchronized @NotNull PlayerStatus getPlayerStatus(@NotNull Player player) {
        return players.getOrDefault(player, PlayerStatus.NOT_IN_MINI_GAME);
    }

    protected synchronized void runTaskLater(@NotNull Runnable task, long delay) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    @SuppressWarnings("SameParameterValue")
    protected synchronized void runTaskTimer(@NotNull Runnable task, long delay, long period) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    protected synchronized void cancelAllTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }

    protected synchronized void broadcastMessage(@NotNull String message) {
        Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + message));
        Logs.discord(PlainTextComponentSerializer.plainText().serialize(Utils.getComponentByString(Prefix.MINI_GAMES + message)));
        for (Player player : getPlayers()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + message));
        }
    }

    protected synchronized void showTitle(@NotNull String title, @NotNull String subtitle) {
        for (Player player : getPlayers()) {
            player.showTitle(Title.title(Utils.getComponentByString(title), Utils.getComponentByString(subtitle)));
        }
    }

    protected boolean endGameIfLessThanTwoPlayersLeft() {
        List<Player> players = getPlayersInMiniGame();
        if (players.size() <= 1) {
            String winner = (players.isEmpty() ? "???" : players.get(0).getName());
            scheduleStopMiniGameAndSendReason("Koniec minigry! Wygrywa gracz &d" + winner, "&dKoniec minigry", "");
            plugin.getChatMinecraft().sendMessage("Gracz **" + winner + "** wygrywa minigrę **" + getMiniGameName() + "**!", Prefix.MINI_GAMES.getDiscord());
            return true;
        }
        return false;
    }

    protected void scheduleStopMiniGameAndSendReason(@NotNull String message, @NotNull String title, @NotNull String subtitle) {
        broadcastMessage(message);
        showTitle(title, subtitle);

        if (timer != null) timer.stopTimerAndHideBossBar();

        lobby = true;

        cancelAllTasks();
        runTaskLater(this::stopMiniGame, 100);
    }

    public synchronized boolean isInMiniGame(@NotNull Player player) {
        return players.containsKey(player);
    }

    public synchronized @NotNull List<Player> getPlayers() {
        return players.keySet().stream().toList();
    }

    public synchronized @NotNull List<Player> getPlayersInMiniGame() {
        return players.keySet().stream().filter(player -> getPlayerStatus(player) == PlayerStatus.IN_MINI_GAME).toList();
    }

    public synchronized boolean isStarted() {
        return isMiniGameStarted;
    }

    public void stopMiniGame() {
        if (!isMiniGameStarted) return;

        broadcastMessage("Minigra zakończona!");
        showTitle("&dMinigra zakończona!", "");

        if (timer != null) timer.stopTimerAndHideBossBar();
        cancelAllTasks();

        getPlayers().forEach(MiniGamesUtils::teleportToLobby);

        isMiniGameStarted = false;
    }


    protected abstract @NotNull String getMiniGameName();
    public abstract void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException;
    public abstract void onPlayerJoin(@NotNull Player player);
    public abstract void onPlayerQuit(@NotNull Player player);
    public abstract void onPlayerDeath(@NotNull Player player);
    public abstract @Nullable World getWorld();
    public abstract @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount();
    public abstract @Range(from = 2, to = Integer.MAX_VALUE) int getMaximumPlayersAmount();

    protected enum PlayerStatus {
        IN_MINI_GAME,
        SPECTATOR,
        NOT_IN_MINI_GAME
    }
}
