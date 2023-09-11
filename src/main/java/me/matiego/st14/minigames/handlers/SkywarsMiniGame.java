package me.matiego.st14.minigames.handlers;

import com.sk89q.worldedit.math.BlockVector3;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.utils.Utils;
import me.matiego.st14.utils.WorldEditUtils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
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
    public SkywarsMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
    }

    private final String CONFIG_PATH = "minigames.skywars.";

    private List<Location> spawns = null;
    private String mapConfigPath = "minigames.skywars.maps";
    private int mapRadius = 100;
    private int prepareTime = 60;
    private int shrinkBorderBeforeEnd = 180;

    @Override
    public @NotNull String getMiniGameName() {
        return "Skywars";
    }

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        //noinspection ConstantValue
        if (true) throw new MiniGameException("not fixed yet");

        if (isMiniGameStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setRandomMapConfigPath();
        loadDataFromConfig(world);
        setUpGameRules(world);
        setUpWorldBorder();
        registerEvents();

        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eGenerowanie areny...");
        Utils.async(() -> {
            try {
                pasteMap(world);
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy generowaniu areny. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while pasting a map for the minigame", e);
                return;
            }

            try {
                if (!MiniGamesUtils.teleportPlayers(players.stream().toList(), spectatorSpawn).get()) {
                    Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", ""));
                    return;
                }
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while teleporting players", e);
                return;
            }

            Utils.sync(() -> startCountdown(15));
        });
    }

    private void setRandomMapConfigPath() throws MiniGameException {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(mapConfigPath);
        if (section == null) throw new MiniGameException("cannot find any map");

        List<String> maps = new ArrayList<>(section.getKeys(false));
        if (maps.isEmpty()) throw new MiniGameException("cannot find any map");

        Collections.shuffle(maps);

        mapConfigPath += "." + maps.get(0);
    }

    private void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, CONFIG_PATH + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spectatorSpawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "radius", 100));

        prepareTime = Math.max(0, plugin.getConfig().getInt("prepare-time", 30));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt("shrink-border-before-end", 180));
        if (totalMiniGameTime < prepareTime + shrinkBorderBeforeEnd) throw new MiniGameException("incorrect game times");

        loadSpawns();
        if (spawns == null || spawns.isEmpty()) throw new MiniGameException("cannot load spawns");
    }

    private void loadSpawns() {
        spawns = plugin.getConfig().getStringList(mapConfigPath + "spawns").stream()
                .map(s -> MiniGamesUtils.getRelativeLocationFromString(baseLocation, s))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
    }

    private void setUpWorldBorder() {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTime(10);
    }

    private void pasteMap(@NotNull World world) throws Exception {
        File file = getRandomMapFile();
        if (file == null) throw new NullPointerException("map file is null");

        WorldEditUtils.pasteSchematicAndGenerateChests(
                world,
                BlockVector3.at(baseLocation.getBlockX(), baseLocation.getBlockY(), baseLocation.getBlockZ()),
                file,
                chestType -> {
                    ChestType type = ChestType.getTypeByName(chestType);
                    return type == null ? null : type.getRandomItems();
                }
        );
    }

    protected @Nullable File getRandomMapFile() {
        File dir = new File(plugin.getDataFolder(), "mini-games");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

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

    @Override
    protected void onCountdownEnd() {
        List<Player> playersToStartGameWith = getPlayers();

        if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
            scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
            return;
        }

        lobby = false;

        broadcastMessage("&dMinigra rozpoczęta. &ePowodzenia!");
        showTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

        timer = new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
        timer.startTimer();

        teleportPlayersToIslands(playersToStartGameWith);
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

    @Override
    protected void miniGameTick() {
        tickPlayers();
        teleportSpectatorsBackIfTooFarAway();

        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eKoniec minigry");
            timer.startTimer();

            getPlayers().forEach(player -> {
                timer.showBossBarToPlayer(player);
                player.setWorldBorder(worldBorder);
            });

            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setPVP(true);
        }

        if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
            worldBorder.setSize(Math.max(1, 0.1 * mapRadius), TimeUnit.SECONDS, shrinkBorderBeforeEnd);
        }
    }

    private void tickPlayers() {
        getPlayersInMiniGame().forEach(player -> {
            if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, shrinkBorderBeforeEnd * 20, 255, false, false, true));
            }
        });
    }

    private void teleportSpectatorsBackIfTooFarAway() {
        int maxDistance = Math.max(0, plugin.getConfig().getInt(CONFIG_PATH + "map-radius", 100));
        getPlayers().stream()
                .filter(player -> distance(player.getLocation(), spectatorSpawn) > maxDistance)
                .filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR)
                .forEach(player -> {
                    player.teleportAsync(spectatorSpawn);
                    player.sendActionBar(Utils.getComponentByString("&cOdleciałeś za daleko"));
                });
    }

    private double distance(@NotNull Location l1, @NotNull Location l2) {
        return Math.max(Math.abs(l1.getX() - l2.getX()), Math.abs(l1.getZ() - l2.getZ()));
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

    private enum ChestType {
        COMMON,
        UNCOMMON,
        RARE,
        EPIC;

        public static @Nullable ChestType getTypeByName(@NotNull String name) {
            for (ChestType type : values()) {
                if (type.toString().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }

        public @NotNull List<ItemStack> getRandomItems() {
            //TODO: getRandomItems
            return new ArrayList<>();
        }
    }
}
