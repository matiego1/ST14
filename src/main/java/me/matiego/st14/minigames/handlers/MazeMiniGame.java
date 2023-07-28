package me.matiego.st14.minigames.handlers;

import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

public class MazeMiniGame extends MiniGame {
    public MazeMiniGame(@NotNull Main plugin, @Range(from = 0, to = Integer.MAX_VALUE) int totalMiniGameTime) {
        super(plugin, totalMiniGameTime);
    }

    private Location spawn = null;
    private int giveCompassBeforeEndInSeconds = -1;

    @Override
    public @NotNull String getMiniGameName() {
        return "Labirynt";
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

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isMiniGameStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        configPath = "minigames.maze.";

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setRandomMapConfigPath(configPath + "maps");
        loadDataFromConfig(world);
        registerEvents();
        setUpGameRules(world);
        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eTeleportowanie graczy...");
        Utils.async(() -> {
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

            Utils.sync(() -> startCountdown(10));
        });
    }

    private void loadDataFromConfig(@NotNull World world) throws MiniGameException {
//        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
//        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
        giveCompassBeforeEndInSeconds = plugin.getConfig().getInt(configPath + "compass-before-end", 30);
    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
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

        timer = new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
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
        List<Player> playersInMiniGame = getPlayersInMiniGame();

        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());
            player.setFireTicks(0);
            player.setHealth(20);
        });

        if (totalMiniGameTime - miniGameTime == giveCompassBeforeEndInSeconds) {
            int minX = plugin.getConfig().getInt(mapConfigPath + "winner-area.minX");
            int minZ = plugin.getConfig().getInt(mapConfigPath + "winner-area.minZ");
            int maxX = plugin.getConfig().getInt(mapConfigPath + "winner-area.maxX");
            int maxZ = plugin.getConfig().getInt(mapConfigPath + "winner-area.maxZ");

            int x = (minX + maxX) / 2;
            int z = (minZ + maxZ) / 2;

            playersInMiniGame.forEach(player -> {
                player.getInventory().addItem(new ItemStack(Material.COMPASS));
                setCompassTarget(player, x, z);
            });
        }
    }

    private void setCompassTarget(@NotNull Player player, int x, int z) {
        player.setCompassTarget(new Location(
                player.getWorld(),
                x,
                player.getLocation().getY(),
                z
        ));
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (getPlayerStatus(player) != PlayerStatus.IN_MINI_GAME) return;
        if (!isInWinnerArea(player)) return;
        if (lobby) return;
        endGameWithWinner(player);
    }

    private boolean isInWinnerArea(@NotNull Player player) {
        int minX = plugin.getConfig().getInt(mapConfigPath + "winner-area.minX");
        int minZ = plugin.getConfig().getInt(mapConfigPath + "winner-area.minZ");
        int maxX = plugin.getConfig().getInt(mapConfigPath + "winner-area.maxX");
        int maxZ = plugin.getConfig().getInt(mapConfigPath + "winner-area.maxZ");

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
