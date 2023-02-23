package me.matiego.st14.minigames.handlers;

import lombok.SneakyThrows;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SnowballsBattleMiniGame extends MiniGame {
    @SneakyThrows(MiniGameException.class)
    public SnowballsBattleMiniGame(@NotNull Main plugin, int gameTimeInSeconds) {
        super(plugin, gameTimeInSeconds);
        if (gameTimeInSeconds <= 120) throw new MiniGameException("too little time");
    }

    private final int PREPARE_TIME_IN_SECONDS = 60;
    private final String CONFIG_PATH = "minigames.snowballs-battle.";
    private UUID worldUuid;

    private boolean lobby = true;

    private BossBarTimer timer;
    private int gameTime = 0;

    private Location spawn = null;
    private Location spectatorSpawn = null;

    @Override
    public void startGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        cancelAllTasks();
        getPlayers().forEach(player -> changePlayerStatus(player, PlayerStatus.NOT_IN_GAME));
        if (timer != null) timer.stopTimerAndHideBossBar();

        World world = Bukkit.getWorld(plugin.getConfig().getString("minigames.world", ""));
        if (world == null) throw new MiniGameException("cannot load world");
        worldUuid = world.getUID();

        spawn = plugin.getConfig().getLocation(CONFIG_PATH + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = plugin.getConfig().getLocation(CONFIG_PATH + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        if (isStarted()) throw new MiniGameException("minigame is already started");

        isGameStarted = true;
        lobby = true;

        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Dołączyłeś do minigry: &dBitwa na śnieżki &e(by Affarek)"));
        }

        MiniGamesUtils.teleportPlayers(players.stream().toList(), spectatorSpawn).thenAcceptAsync(success -> Utils.sync(() -> {
            if (!success) {
                players.forEach(player -> {
                    player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Napotkano niespodziewany błąd przy teleportowaniu graczy. Minigra anulowana."));
                    MiniGamesUtils.teleportToLobby(player);
                });
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
                    broadcastMessage("Za mało graczy! Anulowanie startu minigry");
                    scheduleStopGameWithReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
                }

                lobby = false;

                broadcastMessage("&dMinigra rozpoczęta. &ePowodzenia!");
                showTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

                gameTime = 0;
                timer = new BossBarTimer(plugin, PREPARE_TIME_IN_SECONDS, "Rozpoczęcie bitwy", BossBar.Color.BLUE);
                timer.startTimer();

                playersToStartGameWith.forEach(player -> {
                    changePlayerStatus(player, PlayerStatus.IN_GAME);
                    MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                    player.setBedSpawnLocation(spectatorSpawn, true);
                    timer.showBossBarToPlayer(player);
                });

                runTaskTimer(() -> {
                    gameTime++;

                    if (gameTime == PREPARE_TIME_IN_SECONDS) {
                        timer.stopTimerAndHideBossBar();
                        timer = new BossBarTimer(plugin, gameTimeInSeconds - PREPARE_TIME_IN_SECONDS, "Koniec minigry", BossBar.Color.BLUE);
                        timer.startTimer();
                        getPlayers().forEach(player -> timer.showBossBarToPlayer(player));
                    }
                    if (gameTime >= PREPARE_TIME_IN_SECONDS) {
                        getPlayersInGame().stream()
                                .map(HumanEntity::getInventory)
                                .filter(inv -> inv.containsAtLeast(new ItemStack(Material.SNOWBALL), 128))
                                .forEach(inv -> inv.addItem(new ItemStack(Material.SNOWBALL)));
                    }
                    if (gameTime == gameTimeInSeconds) {
                        scheduleStopGameWithReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
                    }
                }, 20, 20);
            }, 320);
        }));
    }

    private void scheduleStopGameWithReason(@NotNull String message, @NotNull String title, @NotNull String subtitle) {
        broadcastMessage(message);
        showTitle(title, subtitle);

        if (timer != null) timer.stopTimerAndHideBossBar();

        lobby = true;

        cancelAllTasks();
        runTaskLater(this::stopGame, 60);
    }

    private void stopGame() {
        stopGame(Bukkit.getConsoleSender());
    }
    @Override
    public void stopGame(@NotNull CommandSender sender) {
        if (!isGameStarted) return;

        broadcastMessage("Minigra zakończona!");
        showTitle("&dMinigra zakończona!", "");

        if (timer != null) timer.stopTimerAndHideBossBar();
        cancelAllTasks();

        getPlayers().forEach(MiniGamesUtils::teleportToLobby);

        isGameStarted = false;
    }

    @Override
    public void onPlayerJoin(@NotNull Player player) {
        if (!isGameStarted) return;

        if (timer != null) timer.showBossBarToPlayer(player);

        changePlayerStatus(player, PlayerStatus.SPECTATOR);
        MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);

        if (lobby) {
            broadcastMessage("Gracz " + player.getName() + " dołącza do minigry!");
            player.teleportAsync(spectatorSpawn);
        } else {
            broadcastMessage("Gracz " + player.getName() + " obserwuje minigrę");
            player.teleportAsync(spawn);
        }
    }

    @Override
    public void onPlayerQuit(@NotNull Player player) {
        if (!isGameStarted) return;

        if (timer != null) timer.hideBossBarFromPlayer(player);

        changePlayerStatus(player, PlayerStatus.NOT_IN_GAME);

        broadcastMessage("Gracz " + player + " opuścił minigrę.");
        player.sendMessage(Utils.getComponentByString(Prefix.MINI_GAMES + "Opuściłeś minigrę"));

        if (lobby) return;

        List<Player> players = getPlayersInGame();
        if (players.size() <= 1) {
            scheduleStopGameWithReason("Koniec minigry! Wygrywa gracz &d" + (players.isEmpty() ? "???" : players.get(0).getName()), "&dKoniec minigry", "");
        }
    }

    @Override
    public void onPlayerDeath(@NotNull Player player) {
        if (!isGameStarted || lobby) return;
        if (getPlayerStatus(player) != PlayerStatus.IN_GAME) return;

        changePlayerStatus(player, PlayerStatus.SPECTATOR);

        List<Player> players = getPlayersInGame();
        if (players.size() <= 1) {
            scheduleStopGameWithReason("Koniec minigry! Wygrywa gracz &d" + (players.isEmpty() ? "???" : players.get(0).getName()), "&dKoniec minigry", "");
            return;
        }

        runTaskLater(() -> MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE), 5);
        broadcastMessage("Gracz " + player.getName() + " obserwuje minigrę.");
        player.teleportAsync(spectatorSpawn);
    }

    @Override
    public @Nullable World getWorld()  {
        if (worldUuid == null) {
            Logs.error("Cannot load the minigame world! Stopping the game.");
            stopGame(Bukkit.getConsoleSender());
            return null;
        }
        World world = Bukkit.getWorld(worldUuid);
        if (world == null) {
            Logs.error("The minigame world has been deleted! Stopping the game.");
            stopGame(Bukkit.getConsoleSender());
            return null;
        }
        return world;
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
