package me.matiego.st14.minigames.handlers;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class RedGreenMiniGame extends MiniGame {
    public RedGreenMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
    }

    private final String CONFIG_PATH = "minigames.red-green.";

    private String mapConfigPath = "minigames.skywars.maps";
    private Location spawn = null;
    private int maxDelay = 3;
    private int minDelay = 10;
    private int nextCanMoveChange = 0;
    private boolean canMove = true;

    @Override
    public @NotNull String getMiniGameName() {
        return "Czerwone-Zielone";
    }

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setRandomMapConfigPath();
        loadDataFromConfig(world);
        registerEvents();
        setUpGameRules(world);
        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        MiniGamesUtils.teleportPlayers(players.stream().toList(), spectatorSpawn).thenAcceptAsync(success -> Utils.sync(() -> {
            if (!success) {
                scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", "");
                return;
            }

            startCountdown(15);
        }));
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

        spawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        minDelay = plugin.getConfig().getInt(CONFIG_PATH + "min-delay", 3);
        maxDelay = plugin.getConfig().getInt(CONFIG_PATH + "max-delay", 10);
        if (minDelay > maxDelay) throw new MiniGameException("incorrect delays");
    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
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

        nextCanMoveChange = 5 + Utils.getRandomNumber(minDelay, maxDelay);
        canMove = true;

        timer = new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
        timer.setColor(BossBar.Color.GREEN);
        timer.startTimer();

        playersToStartGameWith.forEach(player -> {
            player.teleportAsync(spawn);
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setBedSpawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
        });
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == nextCanMoveChange) {
            changeCanMove();
        }
        tickPlayers();
    }

    private void changeCanMove() {
        nextCanMoveChange += Utils.getRandomNumber(minDelay, maxDelay);
        canMove = !canMove;
        worldBorder.setWarningDistance(canMove ? 0 : NumberConversions.ceil(worldBorder.getSize()));
        timer.setColor(canMove ? BossBar.Color.GREEN : BossBar.Color.RED);
    }

    private void tickPlayers() {
        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());
            player.setFireTicks(0);
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isInMiniGame(player)) return;
        if (lobby) return;
        if (isInArea(player, "winner-area")) {
            endGameWithWinner(player);
            return;
        }
        if (canMove || isInArea(player, "lobby-area")) return;
        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.PROJECTILE, player.getHealth()));
        player.setHealth(0);
    }

    private boolean isInArea(@NotNull Player player, @NotNull String area) {
        int minX = plugin.getConfig().getInt(CONFIG_PATH + area + ".minX");
        int minZ = plugin.getConfig().getInt(CONFIG_PATH + area + ".minZ");
        int maxX = plugin.getConfig().getInt(CONFIG_PATH + area + ".maxX");
        int maxZ = plugin.getConfig().getInt(CONFIG_PATH + area + ".maxZ");

        int x = player.getLocation().getBlockX();
        int z = player.getLocation().getBlockZ();

        return minX <= x && x <= maxX && minZ <= z && z <= maxZ;
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(@NotNull FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setFoodLevel(20);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        event.setCancelled(true);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
    }
}
