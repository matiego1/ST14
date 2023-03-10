package me.matiego.st14.minigames;

import lombok.SneakyThrows;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.WorldBorder;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class MiniGame implements Listener {
    public MiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        this.plugin = plugin;
        this.gameTimeInSeconds = totalGameTimeInSeconds;
    }
    protected final Main plugin;
    protected final int gameTimeInSeconds;
    protected boolean isMiniGameStarted = false;
    protected boolean lobby = true;
    protected BossBarTimer timer;
    protected Location spectatorSpawn;
    protected WorldBorder worldBorder;
    private final Set<BukkitTask> tasks = new HashSet<>();
    private final HashMap<Player, PlayerStatus> players = new HashMap<>();

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

    @SuppressWarnings("SameParameterValue")
    @SneakyThrows(MiniGameException.class)
    protected synchronized void countdownToStart(@NotNull Runnable startMiniGame, int countdownTimeInSeconds) {
        broadcastMessage("&dRozpoczynanie minigry za...");

        if (countdownTimeInSeconds % 5 != 0) throw new MiniGameException("time must be multiple of 5");
        if (countdownTimeInSeconds < 5) throw new MiniGameException("time must be greater than or equal to 5");

        int delay = 20;
        for (; countdownTimeInSeconds >= 5; countdownTimeInSeconds -= 5) {
            String message = String.valueOf(countdownTimeInSeconds);
            runTaskLater(() -> broadcastMessage(message), delay);
            delay += 100;
        }
        runTaskLater(() -> broadcastMessage("&d3"), delay - 60);
        runTaskLater(() -> broadcastMessage("&d2"), delay - 40);
        runTaskLater(() -> broadcastMessage("&d1"), delay - 20);
        runTaskLater(startMiniGame, delay);
    }

    protected synchronized void broadcastMiniGameStartMessage(@NotNull Player sender) {
        Utils.broadcastMessage(
                sender,
                Prefix.MINI_GAMES,
                "Rozpocz????e?? minigr?? &d" + getMiniGameName(),
                "Gracz " + sender.getName() + " rozpocz???? minigr?? &d" + getMiniGameName(),
                "Gracz **" + sender.getName() + "** rozpocz???? minigr?? **" + getMiniGameName() + "**"
        );
    }

    protected synchronized void broadcastMessage(@NotNull String message) {
        if (message.isBlank()) return;

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

    @SuppressWarnings("SameParameterValue")
    protected synchronized void sendActionBar(@NotNull String actionBar) {
        for (Player player : getPlayers()) {
            player.sendActionBar(Utils.getComponentByString(actionBar));
        }
    }

    protected synchronized boolean endGameIfLessThanTwoPlayersLeft() {
        List<Player> players = getPlayersInMiniGame();
        if (players.size() <= 1) {
            if (players.isEmpty()) {
                scheduleStopMiniGameAndSendReason("Koniec minigry! Napotkano b????d przy wy??anianiu zwyci??zcy.", "&dKoniec minigry", "");
            } else {
                endGameWithWinner(players.get(0));
            }
            return true;
        }
        return false;
    }

    protected synchronized void endGameWithWinner(@NotNull Player winner) {
        scheduleStopMiniGameAndSendReason("Koniec minigry! Wygrywa gracz &d" + winner, "&dKoniec minigry", "");
        if (plugin.getIncognitoManager().isIncognito(winner.getUniqueId())) {
            Logs.discord("Gracz **" + winner + "** wygrywa minigr?? **" + getMiniGameName() + "**!");
            return;
        }
        plugin.getChatMinecraft().sendMessage("Gracz **" + winner + "** wygrywa minigr?? **" + getMiniGameName() + "**!", Prefix.MINI_GAMES.getDiscord());
    }

    protected synchronized void scheduleStopMiniGameAndSendReason(@NotNull String message, @NotNull String title, @NotNull String subtitle) {
        lobby = true;

        broadcastMessage(message);
        showTitle(title, subtitle);

        if (timer != null) timer.stopTimerAndHideBossBar();

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

    public void onPlayerJoin(@NotNull Player player) {
        if (!isMiniGameStarted) return;

        if (timer != null) timer.showBossBarToPlayer(player);
        player.setWorldBorder(worldBorder);

        changePlayerStatus(player, PlayerStatus.SPECTATOR);

        if (lobby) {
            broadcastMessage("Gracz " + player.getName() + " do????cza do minigry!");
        } else {
            broadcastMessage("Gracz " + player.getName() + " obserwuje minigr??");

        }
        runTaskLater(() -> {
            if (lobby) {
                MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            } else {
                MiniGamesUtils.healPlayer(player, getSpectatorGameMode());
            }

            player.teleportAsync(spectatorSpawn);
        }, 3);
    }

    public void onPlayerQuit(@NotNull Player player) {
        if (!isMiniGameStarted) return;
        if (!isInMiniGame(player)) return;

        if (timer != null) timer.hideBossBarFromPlayer(player);

        PlayerStatus status = getPlayerStatus(player);
        changePlayerStatus(player, PlayerStatus.NOT_IN_MINI_GAME);

        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Opu??ci??e?? minigr??."));

        if (lobby) {
            broadcastMessage("Gracz " + player.getName() + " opu??ci?? minigr??.");
            return;
        }

        if (status == PlayerStatus.IN_MINI_GAME) {
            broadcastMessage("Gracz " + player.getName() + " opu??ci?? minigr??.");
            endGameIfLessThanTwoPlayersLeft();
        } else {
            broadcastMessage("Gracz " + player.getName() + " przesta?? obserwowa?? minigr??.");
        }
    }

    public void onPlayerDeath(@NotNull Player player) {
        if (!isMiniGameStarted) return;

        if (lobby) {
            if (isInMiniGame(player)) {
                runTaskLater(() -> player.teleportAsync(spectatorSpawn), 3);
            }
            return;
        }

        if (getPlayerStatus(player) != PlayerStatus.IN_MINI_GAME) return;

        changePlayerStatus(player, PlayerStatus.SPECTATOR);

        if (endGameIfLessThanTwoPlayersLeft()) return;

        broadcastMessage("Gracz " + player.getName() + " obserwuje minigr??.");
        runTaskLater(() -> {
            MiniGamesUtils.healPlayer(player, getSpectatorGameMode());
            player.teleportAsync(spectatorSpawn);
        }, 3);
    }

    public void stopMiniGame() {
        if (!isMiniGameStarted) return;

        broadcastMessage("Minigra zako??czona!");
        showTitle("&dMinigra zako??czona!", "");

        if (timer != null) timer.stopTimerAndHideBossBar();
        cancelAllTasks();
        HandlerList.unregisterAll(this);

        getPlayers().forEach(MiniGamesUtils::teleportToLobby);

        isMiniGameStarted = false;
    }

    protected void registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public abstract @NotNull String getMiniGameName();
    public abstract void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException;
    protected abstract void miniGameTick();
    public abstract @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount();
    public abstract @Range(from = 2, to = Integer.MAX_VALUE) int getMaximumPlayersAmount();
    public abstract @NotNull GameMode getSpectatorGameMode();

    protected enum PlayerStatus {
        IN_MINI_GAME,
        SPECTATOR,
        NOT_IN_MINI_GAME
    }
}
