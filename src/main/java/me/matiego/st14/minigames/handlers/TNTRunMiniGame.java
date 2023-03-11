package me.matiego.st14.minigames.handlers;

import com.sk89q.worldedit.math.BlockVector3;
import lombok.SneakyThrows;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TNTRunMiniGame extends MiniGame {
    @SneakyThrows(MiniGameException.class)
    public TNTRunMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
        if (totalGameTimeInSeconds <= PREPARE_TIME_IN_SECONDS) throw new MiniGameException("too little time");
    }

    private final int PREPARE_TIME_IN_SECONDS = 3;
    private final String CONFIG_PATH = "minigames.tnt-run.";

    private int gameTime = 0;

    private Location spawn = null;

    @Override
    public @NotNull String getMiniGameName() {
        return "TNT Run";
    }

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isStarted()) throw new MiniGameException("minigame is already started");

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        spawn = MiniGamesUtils.getLocationFromConfig(world, CONFIG_PATH + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, CONFIG_PATH + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        cancelAllTasks();
        getPlayers().forEach(player -> changePlayerStatus(player, PlayerStatus.NOT_IN_MINI_GAME));
        if (timer != null) timer.stopTimerAndHideBossBar();

        isMiniGameStarted = true;
        lobby = true;

        registerEvents();

        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);

        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eGenerowanie areny...");
        Utils.async(() -> {
            try {
                File file = getRandomMapFile();
                if (file == null) throw new NullPointerException("map file is null");
                MiniGamesUtils.pasteSchematic(
                        MiniGamesUtils.getMiniGamesWorld(),
                        BlockVector3.at(plugin.getConfig().getInt(CONFIG_PATH + "x"), plugin.getConfig().getInt(CONFIG_PATH + "y"), plugin.getConfig().getInt(CONFIG_PATH + "z")),
                        file
                );
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy generowaniu areny. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while pasting a map for the minigame", e);
                return;
            }

            try {
                if (!MiniGamesUtils.teleportPlayers(players.stream().toList(), spawn).get()) {
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

                playersToStartGameWith.forEach(player -> {
                    player.teleportAsync(spawn);
                    changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
                    MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                    player.setBedSpawnLocation(spectatorSpawn, true);
                    timer.showBossBarToPlayer(player);
                });

                runTaskTimer(this::miniGameTick, 20, 20);
                runTaskTimer(this::breakBlockUnderPlayers, 1, 1);
            }, 15));
        });
    }

    @Override
    protected void miniGameTick() {
        gameTime++;

        if (gameTime == gameTimeInSeconds) {
            scheduleStopMiniGameAndSendReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
        }

        int maxDistance = Math.max(0, plugin.getConfig().getInt(CONFIG_PATH + "map-radius", 100));
        getPlayers().stream()
                .filter(player -> distanceSquared(player.getLocation(), spectatorSpawn) > maxDistance * maxDistance)
                .filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR)
                .forEach(player -> {
                    player.teleportAsync(spectatorSpawn);
                    player.sendActionBar(Utils.getComponentByString("&cOdleciałeś za daleko"));
                });

        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());
            player.setFireTicks(0);
            player.setHealth(20);
        });
    }

    private double distanceSquared(@NotNull Location l1, @NotNull Location l2) {
        double a = l1.getX() - l2.getX();
        double b = l1.getZ() - l2.getZ();
        return a * a + b * b;
    }

    private void breakBlockUnderPlayers() {
        if (gameTime < PREPARE_TIME_IN_SECONDS) return;
        getPlayersInMiniGame().forEach(player -> {
            BoundingBox box = player.getBoundingBox().clone();
            World world = player.getWorld();

            runTaskLater(() -> {
                for (int x = NumberConversions.floor(box.getMinX()); x <= NumberConversions.floor(box.getMaxX()); x++) {
                    for (int z = NumberConversions.floor(box.getMinZ()); z <= NumberConversions.floor(box.getMaxZ()); z++) {
                        breakBlocksIfNonAir(world, x, NumberConversions.floor(box.getMinY()), z);
                    }
                }
            }, 5);
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
        return GameMode.SPECTATOR;
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() == EntityType.SNOWBALL) return;
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

    private @Nullable File getRandomMapFile() {
        File dir = new File(plugin.getDataFolder(), "mini-games");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        List<String> mapFiles = plugin.getConfig().getStringList(CONFIG_PATH + "map-files");
        Collections.shuffle(mapFiles);

        for (String mapFile : mapFiles) {
            File file = new File(dir, mapFile);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    private void breakBlocksIfNonAir(@NotNull World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == Material.AIR) return;
        block.setType(Material.AIR);
        world.getBlockAt(x, y - 1, z).setType(Material.AIR);
        world.getBlockAt(x, y - 2, z).setType(Material.AIR);
    }
}
