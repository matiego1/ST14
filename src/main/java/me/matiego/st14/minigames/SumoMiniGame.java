package me.matiego.st14.minigames;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SumoMiniGame extends MiniGame {
    public SumoMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private Location spawn;
    private int mapRadius = 50;
    private int prepareTime = 10;
    private int shrinkBorderBeforeEnd = 180;

    @Override
    protected @NotNull String getMiniGameName() {
        return "Sumo";
    }

    @Override
    protected @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "map-radius", mapRadius));

        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "shrink-border-before-end", shrinkBorderBeforeEnd));
    }

    @Override
    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ENTITY_DROPS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
    }

    @Override
    protected void setUpWorldBorder(@NotNull World world) {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTimeTicks(10);
    }

    @Override
    protected @NotNull Location getLobbySpawn() {
        return spawn;
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        players.forEach(player -> {
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            player.teleportAsync(spawn);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
        });
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == prepareTime) {
            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setGameRule(GameRules.PVP, true);
        }

        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());
            player.setFireTicks(0);
        });

        if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
            worldBorder.changeSize(Math.max(1, 0.1 * mapRadius), shrinkBorderBeforeEnd * 20L);
            getPlayersInMiniGame().forEach(player -> player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, shrinkBorderBeforeEnd * 20, 255, false, false, true)));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        event.setDropItems(false);
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
}
