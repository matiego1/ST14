package me.matiego.st14.objects.minigames;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.Synchronized;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import me.matiego.st14.utils.WorldEditUtils;
import net.kyori.adventure.title.Title;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

public abstract class MiniGame implements Listener {
    public MiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        this.plugin = plugin;
        this.miniGameType = miniGameType;
        this.mapName = mapName;

        this.totalMiniGameTime = miniGameType.getGameTimeInSeconds();
        this.configPath = miniGameType.getConfigPath();
    }

    //<editor-fold defaultstate="collapsed" desc="variables">
    protected final Main plugin;
    protected final String configPath;
    protected final MiniGameType miniGameType;
    protected String mapName;
    protected final int totalMiniGameTime;
    protected String mapConfigPath = null;
    protected Location spectatorSpawn;
    protected Location baseLocation;
    protected WorldBorder worldBorder;
    protected BossBarTimer timer;
    @Getter(onMethod_ = {@Synchronized})
    protected boolean isMiniGameStarted = false;
    protected boolean lobby = true;
    protected int miniGameTime = 0;
    private final List<Player> votesToStop = new ArrayList<>();
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
        votesToStop.clear();
    }

    protected void setMapConfigPath() throws MiniGameException {
        if (mapName != null) {
            mapConfigPath = configPath + "maps." + mapName + ".";
            miniGameType.setPreviousMapName(mapName);
            return;
        }

        List<String> maps = getMaps(plugin, configPath);
        if (maps.isEmpty()) throw new MiniGameException("cannot find any map");
        if (maps.size() > 1) {
            maps.remove(miniGameType.getPreviousMapName());
        }
        Collections.shuffle(maps);
        mapConfigPath = configPath + "maps." + maps.get(0) + ".";
        mapName = maps.get(0);
        miniGameType.setPreviousMapName(mapName);
    }

    public static @NotNull List<String> getMaps(@NotNull Main plugin, @NotNull String configPath) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(configPath + "maps");
        if (section == null) return new ArrayList<>();
        return new ArrayList<>(section.getKeys(false));
    }

    @SuppressWarnings("SameReturnValue")
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount() {
        return 2;
    }
    @SuppressWarnings("SameReturnValue")
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

    public synchronized @NotNull PlayerStatus getPlayerStatus(@NotNull Player player) {
        return players.getOrDefault(player, PlayerStatus.NOT_IN_MINI_GAME);
    }

    public enum PlayerStatus {
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

    public synchronized @NotNull List<Player> getSpectators() {
        return players.keySet().stream().filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR).collect(Collectors.toList());
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
        Logs.discord(Utils.getPlainTextByComponent(Utils.getComponentByString(Prefix.MINI_GAMES + message)));
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
    public  void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isMiniGameStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setMapConfigPath();
        loadDataFromConfig(world);
        registerEvents();
        world.setPVP(false);
        setUpGameRules(world);
        setUpWorldBorder(world);
        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setWorldBorder(worldBorder);
        }

        long begin = Utils.now();
        Utils.async(() -> {
            if (shouldPasteMap()) {
                Utils.sync(() -> sendActionBar("&eGenerowanie areny..."));
                try {
                    File file = getRandomMapFile();
                    if (file == null) throw new NullPointerException("map file is null");
                    manipulatePastedMap(world, pasteMap(world, file));
                } catch (Exception e) {
                    Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy generowaniu areny. Minigra anulowana.", "&dStart anulowany", ""));
                    Logs.error("An error occurred while pasting a map for the minigame", e);
                    return;
                }
                Utils.sync(() -> sendActionBar("&eWygenerowano arenę w " + Utils.parseMillisToString(Utils.now() - begin, true)));
            }

            try {
                if (!MiniGamesUtils.teleportPlayers(players.stream().toList(), getLobbySpawn()).get()) {
                    Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", ""));
                    return;
                }
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while teleporting players", e);
                return;
            }

            Utils.sync(() -> startCountdown(10));
        });
    }

    protected abstract void loadDataFromConfig(@NotNull World world) throws MiniGameException;
    protected abstract void setUpGameRules(@NotNull World world);
    protected void setUpWorldBorder(@NotNull World world) {}
    protected boolean shouldPasteMap() {
        return false;
    }
    protected void manipulatePastedMap(@NotNull World world, @NotNull Clipboard clipboard) throws MiniGameException {}
    protected @NotNull Location getLobbySpawn() {
        return spectatorSpawn;
    }

    @SuppressWarnings("SameParameterValue - it will be easier to change the countdownTimeInSeconds value in the future")
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

    protected void onCountdownEnd() {
        List<Player> playersToStartGameWith = getPlayers();

        if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
            scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
            return;
        }

        lobby = false;

        sendMessage("&dMinigra rozpoczęta. &ePowodzenia!");
        sendTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

        timer = getBossBarTimer();
        timer.startTimer();

        manipulatePlayersToStartGameWith(playersToStartGameWith);
    }

    protected abstract @NotNull BossBarTimer getBossBarTimer();

    protected abstract void manipulatePlayersToStartGameWith(@NotNull List<Player> players);

    protected synchronized void broadcastMiniGameStartMessage(@NotNull Player sender) {
        Utils.broadcastMessage(
                sender,
                Prefix.MINI_GAMES,
                "Rozpocząłeś minigrę &d" + getMiniGameName() + "&e na mapie \"" + mapName + "\"",
                "Gracz " + sender.getName() + " rozpoczął minigrę &d" + getMiniGameName()  + "&e na mapie \"" + mapName + "\"",
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
                player.getInventory().addItem(new ItemStack(Material.SPYGLASS));
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
            if (endGameIfLessThanTwoPlayersLeft()) return;
            resetVotesToStop();
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

        resetVotesToStop();
        sendMessage("Gracz " + player.getName() + " obserwuje minigrę.");
        runTaskLater(() -> {
            MiniGamesUtils.healPlayer(player, getSpectatorGameMode());
            player.teleportAsync(spectatorSpawn);
            player.getInventory().addItem(new ItemStack(Material.SPYGLASS));
        }, 3);
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="minigame end logic">
    private synchronized void resetVotesToStop() {
        if (votesToStop.isEmpty()) return;
        votesToStop.clear();
        sendMessage("Oddane głosy za zakończeniem tej minigry zostały unieważnione.");
    }

    public synchronized void voteToStop(@NotNull Player player) {
        if (votesToStop.contains(player)) {
            votesToStop.remove(player);
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Anulowałeś swój głos za zakończeniem tej minigry."));
        } else {
            votesToStop.add(player);
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Oddałeś głos za zakończeniem tej minigry."));
        }
        int required = requiredVotesToStop();
        if (votesToStop.size() < required) {
            sendMessage("Głosy za zakończeniem tej minigry: " + votesToStop.size() + "/" + required + ". Aby zagłosować użyj komendy /minigame vote-stop");
            return;
        }
        scheduleStopMiniGameAndSendReason("Koniec minigry! " + votesToStop.size() + " graczy zagłosowało za jej zakończeniem.", "&dKoniec minigry", "");
    }

    private synchronized int requiredVotesToStop() {
        return Math.max(
                (int) Math.round(Math.max(0, Math.min(1, plugin.getConfig().getDouble(configPath + "votes-to-stop.percent"))) * getPlayersInMiniGame().size()),
                plugin.getConfig().getInt(configPath + "votes-to-stop.min")
        );
    }

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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (getPlayerStatus(event.getPlayer()) != PlayerStatus.SPECTATOR) return;
        if (event.getItemDrop().getItemStack().getType() != Material.SPYGLASS) return;
        event.setCancelled(true);
    }
}
