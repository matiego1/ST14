package me.matiego.st14.minigames;

import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.BossBarTimer;
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
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MazeMiniGame extends MiniGame {
    public MazeMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private Location spawn = null;
    private int giveCompassBeforeEndInSeconds = -1;

    @Override
    public @NotNull String getMiniGameName() {
        return "Labirynt";
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        spawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
        giveCompassBeforeEndInSeconds = plugin.getConfig().getInt(configPath + "compass-before-end", 30);
    }

    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        players.forEach(player -> {
            player.teleportAsync(spawn);
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setRespawnLocation(spectatorSpawn, true);
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
