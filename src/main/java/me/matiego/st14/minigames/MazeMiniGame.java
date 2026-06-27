package me.matiego.st14.minigames;

import com.sk89q.worldedit.extent.clipboard.Clipboard;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.objects.minigames.maze.MazeCell;
import me.matiego.st14.objects.minigames.maze.MazeGenerator;
import me.matiego.st14.utils.MiniGamesUtils;
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
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class MazeMiniGame extends MiniGame {
    public MazeMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private Location spawn = null;
    private MazeCell endCell = null;
    private int giveCompassBeforeEndInSeconds = -1;

    @Override
    public @NotNull String getMiniGameName() {
        return "Labirynt";
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    public @NotNull MapType getMapType() {
        return MapType.PASTED_MAP;
    }

    @Override
    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");
        spawn = MiniGamesUtils.getLocationFromConfig(world, configPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, configPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
        giveCompassBeforeEndInSeconds = plugin.getConfig().getInt(configPath + "compass-before-end", 30);
    }

    @Override
    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.ENTITY_DROPS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
    }

    @Override
    protected void manipulatePastedMap(@NotNull World world, @NotNull Clipboard clipboard) {
        // TODO: get these numbers from config
        MazeGenerator generator = new MazeGenerator(30, 27, 0, 20);
        generator.build(baseLocation);
        endCell = generator.getEndCell();
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
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 999999, 255, true, false, false));
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
            Location location = endCell.getCellLocation(baseLocation);

            playersInMiniGame.forEach(player -> {
                ItemStack item = new ItemStack(Material.COMPASS);
                CompassMeta meta = (CompassMeta) item.getItemMeta();
                meta.setLodestoneTracked(false);
                meta.setLodestone(location);
                item.setItemMeta(meta);

                player.getInventory().addItem(item);
            });
        }
    }


    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (getPlayerStatus(player) != PlayerStatus.IN_MINI_GAME) return;
        if (!endCell.isInside(baseLocation, player.getLocation())) return;
        if (lobby) return;
        endGameWithWinner(player);
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
