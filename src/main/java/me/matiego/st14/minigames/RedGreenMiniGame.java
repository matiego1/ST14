package me.matiego.st14.minigames;

import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.bossbar.BossBar;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class RedGreenMiniGame extends MiniGame {
    public RedGreenMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private Location spawn = null;
    private int minDelay;
    private int maxDelay;
    private int nextCanMoveChange = -1;
    private int lastCanMoveChange = nextCanMoveChange;
    private boolean canMove = true;

    @Override
    public @NotNull String getMiniGameName() {
        return "Czerwone-Zielone";
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

        minDelay = Math.max(2, plugin.getConfig().getInt(configPath + "min-delay", 3));
        maxDelay = Math.min(60, plugin.getConfig().getInt(configPath + "max-delay", 10));
        if (minDelay > maxDelay) throw new MiniGameException("minDelay is greater than maxDelay");
    }

    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    @Override
    protected void setUpWorldBorder(@NotNull World world) {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(world.getWorldBorder().getCenter());
        worldBorder.setSize(world.getWorldBorder().getSize());
        worldBorder.setDamageAmount(world.getWorldBorder().getDamageAmount());
        worldBorder.setDamageBuffer(world.getWorldBorder().getDamageBuffer());
        worldBorder.setWarningDistance(world.getWorldBorder().getWarningDistance());
        worldBorder.setWarningTime(world.getWorldBorder().getWarningTime());
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        BossBarTimer timer = new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
        timer.setColor(BossBar.Color.GREEN);
        return timer;
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        nextCanMoveChange = 3 + Utils.getRandomNumber(minDelay, maxDelay);
        lastCanMoveChange = miniGameTime;
        canMove = true;

        sendActionBar("&aBiegnij!");

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
        if (miniGameTime == nextCanMoveChange) {
            changeCanMove();
        }
        tickPlayers();
        sendActionBar(canMove ? "&aBiegnij!" : "&cStój!");
    }

    private void changeCanMove() {
        nextCanMoveChange = miniGameTime + Utils.getRandomNumber(minDelay, maxDelay);
        lastCanMoveChange = miniGameTime;
        canMove = !canMove;
        worldBorder.setWarningDistance(canMove ? 0 : (int) Math.ceil(worldBorder.getSize()));
        timer.setColor(canMove ? BossBar.Color.GREEN : BossBar.Color.RED);
    }

    private void tickPlayers() {
        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());
            player.setFireTicks(0);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (getPlayerStatus(player) != PlayerStatus.IN_MINI_GAME) return;

        if (lobby) return;

        if (isInArea(player, "winner-area")) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> worldBorder.setWarningDistance(0), 20);
            endGameWithWinner(player);
            return;
        }

        if (miniGameTime - lastCanMoveChange < 1) return;
        if (canMove) return;
        if (isInArea(player, "lobby-area")) return;

        player.damage(plugin.getConfig().getInt(configPath + "damage", 7));
    }

    private boolean isInArea(@NotNull Player player, @NotNull String area) {
        int minX = plugin.getConfig().getInt(mapConfigPath + area + ".minX");
        int minZ = plugin.getConfig().getInt(mapConfigPath + area + ".minZ");
        int maxX = plugin.getConfig().getInt(mapConfigPath + area + ".maxX");
        int maxZ = plugin.getConfig().getInt(mapConfigPath + area + ".maxZ");

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
