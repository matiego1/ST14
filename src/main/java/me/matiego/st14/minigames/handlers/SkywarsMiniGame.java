package me.matiego.st14.minigames.handlers;

import com.sk89q.worldedit.math.BlockVector3;
import lombok.SneakyThrows;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Pair;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SkywarsMiniGame extends MiniGame {
    @SneakyThrows(MiniGameException.class)
    public SkywarsMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
        if (totalGameTimeInSeconds <= PREPARE_TIME_IN_SECONDS) throw new MiniGameException("too little time");
    }

    private final int PREPARE_TIME_IN_SECONDS = 60;
    private final int SHRINK_BORDER_BEFORE_END_IN_SECONDS = 180;
    private final String CONFIG_PATH = "minigames.skywars.";

    private int gameTime = 0;
    private int mapRadius = 100;
    private String mapConfigPath = "minigames.skywars.maps";
    private Location baseLocation = null;
    private List<Location> spawns = null;
    private List<Pair<Location, ChestType>> chests = null;

    @Override
    public @NotNull String getMiniGameName() {
        return "Skywars";
    }

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isStarted()) throw new MiniGameException("minigame is already started");

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setRandomMapConfigPath();

        baseLocation = MiniGamesUtils.getLocationFromConfig(world, CONFIG_PATH + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spectatorSpawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "radius", 100));

        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTime(10);

        loadChests();
        if (chests == null) throw new MiniGameException("cannot load chests");

        loadSpawns();
        if (spawns == null || spawns.isEmpty()) throw new MiniGameException("cannot load spawns");

        cancelAllTasks();
        getPlayers().forEach(player -> changePlayerStatus(player, PlayerStatus.NOT_IN_MINI_GAME));
        if (timer != null) timer.stopTimerAndHideBossBar();

        isMiniGameStarted = true;
        lobby = true;

        registerEvents();

        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);

        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eGenerowanie areny...");
        Utils.async(() -> {
            try {
                File file = getMapFile();
                if (file == null) throw new NullPointerException("map file is null");
                MiniGamesUtils.pasteSchematic(
                        MiniGamesUtils.getMiniGamesWorld(),
                        BlockVector3.at(baseLocation.getBlockX(), baseLocation.getBlockY(), baseLocation.getBlockZ()),
                        file
                );
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy generowaniu areny. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while pasting a map for the minigame", e);
                return;
            }

            try {
                List<Player> playersList = players.stream().toList();
                if (!MiniGamesUtils.teleportPlayers(playersList, spectatorSpawn).get()) {
                    Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", ""));
                    return;
                }
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while teleporting players", e);
                return;
            }

            Utils.sync(() -> countdownToStart(() -> {
                List<Player> playersToStartGameWith = getPlayers();

                if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
                    scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
                    return;
                }

                lobby = false;

                broadcastMessage("&dMinigra rozpoczęta. &ePowodzenia!");
                showTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

                gameTime = 0;
                timer = new BossBarTimer(plugin, gameTimeInSeconds, "&eKoniec minigry");
                timer.startTimer();

                teleportPlayersToIslands(playersToStartGameWith);

                runTaskTimer(this::miniGameTick, 20, 20);
            }, 15));
        });
    }

    @Override
    protected void miniGameTick() {
        gameTime++;

        if (gameTime == PREPARE_TIME_IN_SECONDS) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, gameTimeInSeconds - PREPARE_TIME_IN_SECONDS, "&eKoniec minigry");
            timer.startTimer();
            getPlayers().forEach(player -> {
                timer.showBossBarToPlayer(player);
                player.setWorldBorder(worldBorder);
            });

            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setPVP(true);
        }

        if (gameTime == gameTimeInSeconds) {
            scheduleStopMiniGameAndSendReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
        }

        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());

            if (gameTime == gameTimeInSeconds - SHRINK_BORDER_BEFORE_END_IN_SECONDS) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, SHRINK_BORDER_BEFORE_END_IN_SECONDS * 20, 255, false, false, true));
            }
        });

        if (gameTime == gameTimeInSeconds - SHRINK_BORDER_BEFORE_END_IN_SECONDS) {
            worldBorder.setSize(Math.max(1, 0.1 * mapRadius), TimeUnit.SECONDS, SHRINK_BORDER_BEFORE_END_IN_SECONDS);
        }

        getPlayers().stream()
                .filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR)
                .filter(player -> distanceSquared(player.getLocation(), spectatorSpawn) > mapRadius * mapRadius)
                .forEach(player -> {
                    player.teleportAsync(spectatorSpawn);
                    player.sendActionBar(Utils.getComponentByString("&cOdleciałeś za daleko"));
                });
    }

    @Override
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount() {
        return 2;
    }

    @Override
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMaximumPlayersAmount() {
        return 15;
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    private void setRandomMapConfigPath() throws MiniGameException {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(mapConfigPath);
        if (section == null) throw new MiniGameException("cannot find any map");

        List<String> maps = new ArrayList<>(section.getKeys(false));
        if (maps.isEmpty()) throw new MiniGameException("cannot find any map");

        Collections.shuffle(maps);

        mapConfigPath += "." + maps.get(0);
    }

    private void loadSpawns() {
        spawns = plugin.getConfig().getStringList(mapConfigPath + "spawns").stream()
                .map(s -> MiniGamesUtils.getRelativeLocationFromString(baseLocation, s))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void loadChests() {
        chests = plugin.getConfig().getStringList(mapConfigPath + "chests").stream()
                .map(ChestType::parseConfigValue)
                .filter(Objects::nonNull)
                .map(p -> new Pair<>(MiniGamesUtils.getRelativeLocationFromString(baseLocation, p.getFirst()), p.getSecond()))
                .filter(p -> p.getFirst() != null)
                .collect(Collectors.toList());
    }

    private @Nullable File getMapFile() {
        File dir = new File(plugin.getDataFolder(), "mini-games");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        File file = new File(dir, plugin.getConfig().getString(mapConfigPath + "map-file", ""));
        if (!file.exists()) return null;
        return file;
    }

    private void teleportPlayersToIslands(@NotNull List<Player> players) {
        int i = 0;
        for (Player player : players) {
            player.setBedSpawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
            if (i >= spawns.size()) {
                changePlayerStatus(player, PlayerStatus.SPECTATOR);
                player.teleportAsync(spectatorSpawn);
                MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                broadcastMessage("Gracz " + player.getName() + " obserwuję minigrę.");
            } else {
                changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
                player.teleportAsync(spawns.get(i));
                MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);

                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(spawns.get(i));
                border.setSize(plugin.getConfig().getDouble(mapConfigPath + "border-radius", 10));
                border.setDamageAmount(5);
                border.setDamageBuffer(0);
                border.setWarningDistance(0);
                player.setWorldBorder(border);

                i++;
            }
        }
    }

    private double distanceSquared(@NotNull Location l1, @NotNull Location l2) {
        double a = l1.getX() - l2.getX();
        double b = l1.getZ() - l2.getZ();
        return a * a + b * b;
    }

    private enum ChestType {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC;

        public static @Nullable Pair<String, ChestType> parseConfigValue(@NotNull String value) {
            for (ChestType type : values()) {
                if (value.toUpperCase().endsWith(";" + type)) {
                    return new Pair<>(value.substring(0, value.length() - type.toString().length() - 1), type);
                }
            }
            return null;
        }
    }
}
