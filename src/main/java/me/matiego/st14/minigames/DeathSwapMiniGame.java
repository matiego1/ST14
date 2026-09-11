package me.matiego.st14.minigames;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class DeathSwapMiniGame extends MiniGame {
    public DeathSwapMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private int mapSize = 500;
    private int prepareTime = 60 * 10;
    private int shuffleInterval = 150;
    int nextShuffle = shuffleInterval;

    @Override
    protected @NotNull GameMode getSpectatorGameMode() {
        return GameMode.SPECTATOR;
    }

    @Override
    public @NotNull MapType getMapType() {
        return MapType.SURVIVAL;
    }

    @Override
    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        mapSize = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "size", mapSize));
        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        shuffleInterval = Math.max(20, plugin.getConfig().getInt(configPath + "shuffle-interval", shuffleInterval));
        nextShuffle = shuffleInterval;
        if (prepareTime > totalMiniGameTime) throw new MiniGameException("incorrect game times");
    }

    @Override
    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ENTITY_DROPS, true);
        world.setGameRule(GameRules.FALL_DAMAGE, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 128);
        world.setGameRule(GameRules.FIRE_DAMAGE, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eKoniec minigry");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        players.forEach(player -> {
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);

            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 5, 5));
        });

        MiniGamesUtils.spreadPlayers(spectatorSpawn, mapSize / 2);
    }

    @Override
    protected void miniGameTick() {
        int difference = nextShuffle - miniGameTime;
        sendActionBar("&eZamiana za " + difference);

        if (difference == 15) sendMessage("Losowa zamiana miejsc za 15 sekund!");
        else if (difference == 10) sendMessage(String.valueOf(difference));
        else if (4 <= difference && difference <= 5) sendMessage(String.valueOf(difference));
        else if (1 <= difference && difference <= 3) sendMessage("&d" + difference);
        else if (difference == 0) {
            sendMessage("&d&lZamiana miejsc!");

            List<Player> players = getPlayersInMiniGame();
            List<Player> newPositions = Utils.generateDerangement(players);
            for (int i = 0; i < players.size(); i++) {
                players.get(i).teleportAsync(newPositions.get(i).getLocation());
            }

            nextShuffle = miniGameTime + shuffleInterval;
        }
    }
}
