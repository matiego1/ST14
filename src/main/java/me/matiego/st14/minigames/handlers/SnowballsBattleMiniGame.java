package me.matiego.st14.minigames.handlers;

import lombok.SneakyThrows;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

public class SnowballsBattleMiniGame extends MiniGame {
    @SneakyThrows(MiniGameException.class)
    public SnowballsBattleMiniGame(@NotNull Main plugin, int gameTimeInSeconds) {
        super(plugin, gameTimeInSeconds);
        if (gameTimeInSeconds <= 120) throw new MiniGameException("too little time");
    }

    @Override
    protected @NotNull String getMiniGameName() {
        return "Bitwa na śnieżki";
    }

    private final int PREPARE_TIME_IN_SECONDS = 60;
    private final String CONFIG_PATH = "minigames.snowballs-battle.";

    private int gameTime = 0;

    private Location spawn = null;
    private Location spectatorSpawn = null;

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isStarted()) throw new MiniGameException("minigame is already started");

        World world = MiniGamesUtils.getLobbyWorld();
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
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);

        Utils.broadcastMessage(
                sender,
                Prefix.MINI_GAMES,
                "Rozpocząłeś minigrę &d" + getMiniGameName(),
                "Gracz " + sender.getName() + " rozpoczął minigrę &d" + getMiniGameName(),
                "Gracz **" + sender.getName() + "** rozpoczął minigrę **" + getMiniGameName() + "**"
        );

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        MiniGamesUtils.teleportPlayers(players.stream().toList(), spectatorSpawn).thenAcceptAsync(success -> Utils.sync(() -> {
            if (!success) {
                scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana.", "&dStart anulowany", "");
                return;
            }

            broadcastMessage("&dRozpoczynanie minigry za...");

            runTaskLater(() -> broadcastMessage("15"), 20);
            runTaskLater(() -> broadcastMessage("10"), 120);
            runTaskLater(() -> broadcastMessage("5"), 220);
            runTaskLater(() -> broadcastMessage("&d3"), 260);
            runTaskLater(() -> broadcastMessage("&d2"), 280);
            runTaskLater(() -> broadcastMessage("&d1"), 300);
            runTaskLater(() -> {
                List<Player> playersToStartGameWith = getPlayers();

                if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
                    scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
                    return;
                }

                lobby = false;

                broadcastMessage("&dMinigra rozpoczęta. &ePowodzenia!");
                showTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

                gameTime = 0;
                timer = new BossBarTimer(plugin, PREPARE_TIME_IN_SECONDS, "&eRozpoczęcie bitwy", BossBar.Color.BLUE);
                timer.startTimer();

                playersToStartGameWith.forEach(player -> {
                    player.teleportAsync(spawn);
                    changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
                    MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                    player.setBedSpawnLocation(spectatorSpawn, true);
                    timer.showBossBarToPlayer(player);
                });

                runTaskTimer(() -> {
                    gameTime++;

                    if (gameTime == PREPARE_TIME_IN_SECONDS) {
                        timer.stopTimerAndHideBossBar();
                        timer = new BossBarTimer(plugin, gameTimeInSeconds - PREPARE_TIME_IN_SECONDS, "&eKoniec minigry", BossBar.Color.BLUE);
                        timer.startTimer();
                        getPlayers().forEach(player -> timer.showBossBarToPlayer(player));

                        world.setPVP(true);
                    }

                    if (gameTime == gameTimeInSeconds) {
                        scheduleStopMiniGameAndSendReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
                    }

                    List<Player> playersInGame = getPlayersInMiniGame();
                    if (gameTime % 30 == 0) {
                        playersInGame.forEach(player -> player.setHealth(Math.min(player.getHealth() + 2, 20)));
                    }
                    playersInGame.stream()
                            .map(HumanEntity::getInventory)
                            .filter(inv -> !inv.containsAtLeast(new ItemStack(Material.SNOWBALL), 128))
                            .forEach(inv -> inv.addItem(new ItemStack(Material.SNOWBALL, 2)));
                }, 20, 20);
            }, 320);
        }));
    }

    @Override
    public void onPlayerJoin(@NotNull Player player) {
        if (!isMiniGameStarted) return;

        if (timer != null) timer.showBossBarToPlayer(player);

        changePlayerStatus(player, PlayerStatus.SPECTATOR);
        MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);

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
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.teleportAsync(spectatorSpawn);
        }, 5);
    }

    @Override
    public @Nullable World getWorld()  {
        return MiniGamesUtils.getLobbyWorld();
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
