package me.matiego.st14.minigames.handlers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.utils.Utils;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PvPMiniGame extends MiniGame {
    public PvPMiniGame(@NotNull Main plugin, @Range(from = 0, to = Integer.MAX_VALUE) int totalMiniGameTime) {
        super(plugin, totalMiniGameTime);
    }

    private final List<Location> spawns = new ArrayList<>();
    private int prepareTime = 30;
    private int levelUpBeforeEnd = 90;

    @Override
    public @NotNull String getMiniGameName() {
        return "PvP";
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

        configPath = "minigames.pvp.";

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
        //todo: load spawns
//        spawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spawn");
//        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        levelUpBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "level-up-before-end", levelUpBeforeEnd));
        if (totalMiniGameTime < prepareTime + levelUpBeforeEnd) throw new MiniGameException("incorrect game times");
    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
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

        timer = new BossBarTimer(plugin, prepareTime, "&eRozpoczęcie bitwy");
        timer.startTimer();

        playersToStartGameWith.forEach(player -> {
            //todo: teleport players to spawn
//            player.teleportAsync(spawn);
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setBedSpawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
            //todo: give tools to players
        });
    }

    @Override
    protected void miniGameTick() {

    }
}
