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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.NumberConversions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

public class RedGreenMiniGame extends MiniGame {
    @SneakyThrows(MiniGameException.class)
    public RedGreenMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
        if (totalGameTimeInSeconds <= 0) throw new MiniGameException("too little time");
    }

    private final String CONFIG_PATH = "minigames.red-green.";
    private final int MIN_DELAY_IN_SECONDS = 3;
    private final int MAX_DELAY_IN_SECONDS = 10;

    private Location spawn = null;
    private int gameTime = 0;
    private int nextCanMoveChange = 0;
    private boolean canMove = true;

    @Override
    public @NotNull String getMiniGameName() {
        return "Czerwone-Zielone";
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

        WorldBorder defaultBorder = spawn.getWorld().getWorldBorder();
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setSize(defaultBorder.getSize());
        worldBorder.setDamageAmount(defaultBorder.getDamageAmount());
        worldBorder.setDamageBuffer(defaultBorder.getDamageBuffer());
        worldBorder.setCenter(defaultBorder.getCenter());
        worldBorder.setWarningTime(0);
        worldBorder.setWarningDistance(0);

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
        world.setGameRule(GameRule.FIRE_DAMAGE, false);

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
                nextCanMoveChange = 5 + Utils.getRandomNumber(MIN_DELAY_IN_SECONDS, MAX_DELAY_IN_SECONDS);
                canMove = true;

                timer = new BossBarTimer(plugin, gameTimeInSeconds, "&eKoniec minigry");
                timer.setColor(BossBar.Color.GREEN);
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
    protected void miniGameTick() {
        gameTime++;

        if (gameTime == gameTimeInSeconds) {
            scheduleStopMiniGameAndSendReason("&dKoniec minigry! &eRozgrywka zakończyła się remisem.", "&dKoniec minigry", "&eRemis");
        }

        if (gameTime == nextCanMoveChange) {
            nextCanMoveChange += Utils.getRandomNumber(MIN_DELAY_IN_SECONDS, MAX_DELAY_IN_SECONDS);
            canMove = !canMove;
            worldBorder.setWarningDistance(canMove ? 0 : NumberConversions.ceil(worldBorder.getSize()));
            timer.setColor(canMove ? BossBar.Color.GREEN : BossBar.Color.RED);
        }

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

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!isInMiniGame(player)) return;
        if (lobby) return;
        if (isInArea(player, "winner-area")) {
            endGameWithWinner(player);
            return;
        }
        if (canMove || !isInArea(player, "game-area")) return;
        player.setLastDamageCause(new EntityDamageEvent(player, EntityDamageEvent.DamageCause.MAGIC, player.getHealth()));
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
}
