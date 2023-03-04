package me.matiego.st14.minigames.handlers;

import lombok.SneakyThrows;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

public class TNTRun extends MiniGame {
    @SneakyThrows(MiniGameException.class)
    public TNTRun(@NotNull Main plugin, int gameTimeInSeconds) {
        super(plugin, gameTimeInSeconds);
        if (gameTimeInSeconds <= 30) throw new MiniGameException("too little time");
    }

    private final int PREPARE_TIME_IN_SECONDS = 10;
    private final String CONFIG_PATH = "minigames.tnt-run.";

    private int gameTime = 0;

    private Location spawn = null;
    private Location spectatorSpawn = null;

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

        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);

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

            Utils.async(() -> {
                //TODO: refresh map
            });

            countdownToStart(() -> {
                List<Player> playersToStartGameWith = getPlayers();

                if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
                    scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
                    return;
                }

                lobby = false;

                broadcastMessage("&dMinigra rozpoczęta. &ePowodzenia!");
                showTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

                gameTime = 0;
                timer = new BossBarTimer(plugin, PREPARE_TIME_IN_SECONDS, "&eKoniec minigry", BossBar.Color.BLUE);
                timer.startTimer();

                playersToStartGameWith.forEach(player -> {
                    player.teleportAsync(spawn);
                    changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
                    MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                    player.setBedSpawnLocation(spectatorSpawn, true);
                    timer.showBossBarToPlayer(player);
                });

                runTaskTimer(this::miniGameTick, 20, 20);
            }, 15);

        }));
    }

    @Override
    public void onPlayerJoin(@NotNull Player player) {
        if (!isMiniGameStarted) return;

        if (timer != null) timer.showBossBarToPlayer(player);

        changePlayerStatus(player, PlayerStatus.SPECTATOR);
        if (lobby) {
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        } else {
            MiniGamesUtils.healPlayer(player, GameMode.SPECTATOR);
        }

        player.teleportAsync(spectatorSpawn);

        if (lobby) {
            broadcastMessage("Gracz " + player.getName() + " dołącza do minigry!");
        } else {
            broadcastMessage("Gracz " + player.getName() + " obserwuje minigrę");
        }
    }

    @Override
    public void onPlayerQuit(@NotNull Player player) {
        if (!isMiniGameStarted) return;
        if (!isInMiniGame(player)) return;

        if (timer != null) timer.hideBossBarFromPlayer(player);

        PlayerStatus status = getPlayerStatus(player);
        changePlayerStatus(player, PlayerStatus.NOT_IN_MINI_GAME);

        if (lobby) {
            broadcastMessage("Gracz " + player.getName() + " opuścił minigrę.");
            return;
        }

        if (status == PlayerStatus.IN_MINI_GAME) {
            broadcastMessage("Gracz " + player.getName() + " opuścił minigrę.");
            endGameIfLessThanTwoPlayersLeft();
        } else {
            broadcastMessage("Gracz " + player.getName() + " przestał obserwować minigrę.");
        }
    }

    @Override
    public void onPlayerDeath(@NotNull Player player) {
        if (!isMiniGameStarted || lobby) return;
        if (getPlayerStatus(player) != PlayerStatus.IN_MINI_GAME) return;

        changePlayerStatus(player, PlayerStatus.SPECTATOR);

        if (endGameIfLessThanTwoPlayersLeft()) return;

        broadcastMessage("Gracz " + player.getName() + " obserwuje minigrę.");
        runTaskLater(() -> {
            MiniGamesUtils.healPlayer(player, GameMode.SPECTATOR);
            player.teleportAsync(spectatorSpawn);
        }, 5);
    }

    @Override
    protected void miniGameTick() {
        gameTime++;

        if (gameTime == gameTimeInSeconds) {
            scheduleStopMiniGameAndSendReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
        }

        int maxDistance = plugin.getConfig().getInt(CONFIG_PATH + "map-radius", 100);
        getPlayers().stream()
                .filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR)
                .filter(player -> player.getLocation().distanceSquared(spectatorSpawn) > maxDistance * maxDistance)
                .forEach(player -> player.teleportAsync(spectatorSpawn));

        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setFoodLevel(20);
            player.setSaturation(20);
            player.setExhaustion(0);
            player.setHealth(20);
            player.setLevel(playersInMiniGame.size());


            if (gameTime >= PREPARE_TIME_IN_SECONDS) {
                Block blockUnderPlayer = player.getLocation().getBlock().getRelative(BlockFace.DOWN);
                if (!blockUnderPlayer.getType().isSolid()) return;

                blockUnderPlayer.setType(Material.AIR);
                blockUnderPlayer.getRelative(BlockFace.DOWN).setType(Material.AIR);
            }
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
}
