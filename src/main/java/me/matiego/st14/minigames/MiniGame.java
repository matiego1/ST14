package me.matiego.st14.minigames;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.utils.Utils;
import me.matiego.st14.utils.WorldEditUtils;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public abstract class MiniGame implements Listener {
    public MiniGame(@NotNull Main plugin, @Range(from = 0, to = Integer.MAX_VALUE) int totalMiniGameTime, @NotNull String configPath, @Nullable String mapName) {
        this.plugin = plugin;
        this.totalMiniGameTime = totalMiniGameTime;
        this.configPath = configPath;
        this.mapName = mapName;
    }

    //<editor-fold defaultstate="collapsed" desc="variables">
    protected final Main plugin;
    protected final String configPath;
    protected final int totalMiniGameTime;
    protected String mapName;
    protected String mapConfigPath = null;
    protected Location spectatorSpawn;
    protected Location baseLocation;
    protected WorldBorder worldBorder;
    protected BossBarTimer timer;
    @Getter(onMethod_ = {@Synchronized})
    protected boolean isMiniGameStarted = false;
    protected boolean lobby = true;
    protected int miniGameTime = 0;
    private final Set<BukkitTask> tasks = new HashSet<>();
    private final HashMap<Player, PlayerStatus> players = new HashMap<>();

    protected void clearExistingData() {
        cancelAllTasks();
        getPlayers().forEach(player -> changePlayerStatus(player, PlayerStatus.NOT_IN_MINI_GAME));
        if (timer != null) {
            timer.stopTimerAndHideBossBar();
            timer = null;
        }

        spectatorSpawn = null;
        baseLocation = null;
        worldBorder = null;
        isMiniGameStarted = false;
        lobby = true;
        miniGameTime = 0;
        mapConfigPath = null;
    }

    protected void setMapConfigPath() throws MiniGameException {
        if (mapName != null) {
            mapConfigPath = configPath + "maps." + mapName + ".";
            return;
        }

        List<String> maps = getMaps(plugin, configPath);
        if (maps.isEmpty()) throw new MiniGameException("cannot find any map");
        Collections.shuffle(maps);
        mapConfigPath = configPath + "maps." + maps.get(0) + ".";
        mapName = maps.get(0);
    }

    public static @NotNull List<String> getMaps(@NotNull Main plugin, @NotNull String configPath) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(configPath + "maps");
        if (section == null) return new ArrayList<>();
        return new ArrayList<>(section.getKeys(false));
    }

    public @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount() {
        return 2;
    }
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMaximumPlayersAmount() {
        return 15;
    }

    protected abstract @NotNull String getMiniGameName();
    protected abstract @NotNull GameMode getSpectatorGameMode();

    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="player status">
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

    protected enum PlayerStatus {
        IN_MINI_GAME,
        SPECTATOR,
        NOT_IN_MINI_GAME
    }

    public synchronized boolean isInMiniGame(@NotNull Player player) {
        return players.containsKey(player);
    }

    public synchronized @NotNull List<Player> getPlayers() {
        return players.keySet().stream().toList();
    }

    public synchronized @NotNull List<Player> getPlayersInMiniGame() {
        return players.keySet().stream().filter(player -> getPlayerStatus(player) == PlayerStatus.IN_MINI_GAME).collect(Collectors.toList());
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="minigame tasks">
    protected synchronized void runTaskLater(@NotNull Runnable task, long delay) {
        tasks.add(Bukkit.getScheduler().runTaskLater(plugin, task, delay));
    }

    protected synchronized void runTaskTimer(@NotNull Runnable task, long delay, long period) {
        tasks.add(Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
    }

    protected synchronized void cancelAllTasks() {
        for (BukkitTask task : tasks) {
            task.cancel();
        }
        tasks.clear();
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="messaging utils">
    protected synchronized void sendMessage(@NotNull String message) {
        if (message.isBlank()) return;

        Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + message));
        Logs.discord(PlainTextComponentSerializer.plainText().serialize(Utils.getComponentByString(Prefix.MINI_GAMES + message)));
        for (Player player : getPlayers()) {
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + message));
        }
    }

    protected synchronized void sendTitle(@NotNull String title, @NotNull String subtitle) {
        for (Player player : getPlayers()) {
            player.showTitle(Title.title(Utils.getComponentByString(title), Utils.getComponentByString(subtitle)));
        }
    }

    protected synchronized void sendActionBar(@NotNull String actionBar) {
        for (Player player : getPlayers()) {
            player.sendActionBar(Utils.getComponentByString(actionBar));
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="map utils">
    protected @Nullable File getRandomMapFile() {
        File dir = new File(plugin.getDataFolder(), "mini-games");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        if (mapConfigPath == null) return null;

        List<String> mapFiles = plugin.getConfig().getStringList(mapConfigPath + "map-files");
        Collections.shuffle(mapFiles);

        for (String mapFile : mapFiles) {
            File file = new File(dir, mapFile);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    protected @NotNull Clipboard pasteMap(@NotNull World world, @NotNull File file) throws Exception {
        if (!file.exists()) throw new NullPointerException("map file does not exist");

        return WorldEditUtils.pasteSchematic(
                world,
                BlockVector3.at(baseLocation.getBlockX(), baseLocation.getBlockY(), baseLocation.getBlockZ()),
                file
        );
    }

    //TODO: refresh minigame world
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="minigame start logic">
    public abstract void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException;

    @SneakyThrows(MiniGameException.class)
    protected synchronized void startCountdown(int countdownTimeInSeconds) {
        sendMessage("&dRozpoczynanie minigry za...");

        if (countdownTimeInSeconds % 5 != 0) throw new MiniGameException("time must be multiple of 5");
        if (countdownTimeInSeconds < 5) throw new MiniGameException("time must be greater than or equal to 5");

        int delay = 20;
        for (; countdownTimeInSeconds >= 5; countdownTimeInSeconds -= 5) {
            String message = String.valueOf(countdownTimeInSeconds);
            runTaskLater(() -> sendMessage(message), delay);
            delay += 100;
        }

        runTaskLater(() -> sendMessage("&d3"), delay - 60);
        runTaskLater(() -> sendMessage("&d2"), delay - 40);
        runTaskLater(() -> sendMessage("&d1"), delay - 20);

        runTaskLater(() -> {
            miniGameTime = 0;
            onCountdownEnd();
            runTaskTimer(() -> {
                miniGameTime++;
                miniGameTick();

                if (miniGameTime == totalMiniGameTime) {
                    scheduleStopMiniGameAndSendReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
                }
            }, 20, 20);
        }, delay);
    }

    protected abstract void onCountdownEnd();

    protected synchronized void broadcastMiniGameStartMessage(@NotNull Player sender) {
        Utils.broadcastMessage(
                sender,
                Prefix.MINI_GAMES,
                "Rozpocząłeś minigrę &d" + getMiniGameName() + "&3 na mapie \"" + mapName + "\"",
                "Gracz " + sender.getName() + " rozpoczął minigrę &d" + getMiniGameName()  + "&3 na mapie \"" + mapName + "\"",
                "Gracz **" + sender.getName() + "** rozpoczął minigrę **" + getMiniGameName() + "** na mapie **" + mapName + "**"
        );
    }

    protected void registerEvents() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
    //</editor-fold>

    protected abstract void miniGameTick();

    //<editor-fold defaultstate="collapsed" desc="join, quit, death handlers">
    public void onPlayerJoin(@NotNull Player player) {
        if (!isMiniGameStarted) return;

        if (timer != null) timer.showBossBarToPlayer(player);
        player.setWorldBorder(worldBorder);

        changePlayerStatus(player, PlayerStatus.SPECTATOR);

        if (lobby) {
            sendMessage("Gracz " + player.getName() + " dołącza do minigry!");
        } else {
            sendMessage("Gracz " + player.getName() + " obserwuje minigrę");

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

        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Opuściłeś minigrę."));

        if (lobby) {
            sendMessage("Gracz " + player.getName() + " opuścił minigrę.");
            return;
        }

        if (status == PlayerStatus.IN_MINI_GAME) {
            sendMessage("Gracz " + player.getName() + " opuścił minigrę.");
            endGameIfLessThanTwoPlayersLeft();
        } else {
            sendMessage("Gracz " + player.getName() + " przestał obserwować minigrę.");
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

        sendMessage("Gracz " + player.getName() + " obserwuje minigrę.");
        runTaskLater(() -> {
            MiniGamesUtils.healPlayer(player, getSpectatorGameMode());
            player.teleportAsync(spectatorSpawn);
        }, 3);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="minigame end logic">
    protected synchronized boolean endGameIfLessThanTwoPlayersLeft() {
        List<Player> players = getPlayersInMiniGame();
        if (players.size() <= 1) {
            if (players.isEmpty()) {
                scheduleStopMiniGameAndSendReason("Koniec minigry! Napotkano błąd przy wyłanianiu zwycięzcy.", "&dKoniec minigry", "");
            } else {
                endGameWithWinner(players.get(0));
            }
            return true;
        }
        return false;
    }

    protected synchronized void endGameWithWinner(@NotNull Player winner) {
        scheduleStopMiniGameAndSendReason("Koniec minigry! Wygrywa gracz &d" + winner.getName(), "&dKoniec minigry", "");
        if (plugin.getIncognitoManager().isIncognito(winner.getUniqueId())) {
            Logs.discord("Gracz **" + winner.getName() + "** wygrywa minigrę **" + getMiniGameName() + "**!");
        } else {
            plugin.getChatMinecraftManager().sendMessage("Gracz **" + winner.getName() + "** wygrywa minigrę **" + getMiniGameName() + "**!", Prefix.MINI_GAMES.getDiscord());
        }

        plugin.getMiniGamesManager().giveRewardToWinner(winner, Utils.round(plugin.getConfig().getDouble(configPath + "winner-reward", 20), 2));
    }

    protected synchronized void scheduleStopMiniGameAndSendReason(@NotNull String message, @NotNull String title, @NotNull String subtitle) {
        lobby = true;

        sendMessage(message);
        sendTitle(title, subtitle);

        if (timer != null) timer.stopTimerAndHideBossBar();

        cancelAllTasks();
        runTaskLater(this::stopMiniGame, 100);
    }

    public void stopMiniGame() {
        if (!isMiniGameStarted) return;

        sendMessage("Minigra zakończona!");
        sendTitle("&dMinigra zakończona!", "");

        if (timer != null) timer.stopTimerAndHideBossBar();
        cancelAllTasks();
        HandlerList.unregisterAll(this);

        getPlayers().forEach(MiniGamesUtils::teleportToLobby);

        isMiniGameStarted = false;
    }

    //</editor-fold>
}
