package me.matiego.st14.minigames;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TNTRunMiniGame extends MiniGame {
    public TNTRunMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private final int PREPARE_TIME_IN_SECONDS = 3;
    private Location spawn = null;

    @Override
    public @NotNull String getMiniGameName() {
        return "TNT Run";
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.SPECTATOR;
    }

    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
    }

    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
    }

    @Override
    protected boolean shouldPasteMap() {
        return true;
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
            player.teleportAsync(spawn);
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
        });

        runTaskTimer(this::breakBlocksUnderPlayers, 1, 1);
    }

    private void breakBlocksUnderPlayers() {
        if (miniGameTime < PREPARE_TIME_IN_SECONDS) return;
        getPlayersInMiniGame().forEach(this::breakBlocksUnderPlayer);
    }

    private void breakBlocksUnderPlayer(@NotNull Player player) {
        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) return;

        BoundingBox box = player.getBoundingBox();
        World world = player.getWorld();

        int y = (int) Math.floor(box.getMinY());
        int centerX = (int) Math.floor(box.getCenterX());
        int centerZ = (int) Math.floor(box.getCenterZ());

        int oppositeX = (int) Math.floor(box.getMinX());
        if (oppositeX == centerX) oppositeX = (int) Math.floor(box.getMaxX());

        int oppositeZ = (int) Math.floor(box.getMinZ());
        if (oppositeZ == centerZ) oppositeZ = (int) Math.floor(box.getMaxZ());

        if (breakBlocksIfNotAir(world, centerX, y, centerZ)) return;
        if (breakBlocksIfNotAir(world, centerX, y, oppositeZ)) return;
        if (breakBlocksIfNotAir(world, oppositeX, y, centerZ)) return;
        breakBlocksIfNotAir(world, oppositeX, y, oppositeZ);
    }

    private boolean breakBlocksIfNotAir(@NotNull World world, int x, int y, int z) {
        Block block = world.getBlockAt(x, y - 1, z);
        if (block.getType().isAir()) return false;
        if (block.getType().toString().contains("AIR")) Logs.error("isAir() nie działa");
        runTaskLater(() -> {
            block.setType(Material.AIR);
            block.getRelative(BlockFace.DOWN).setType(Material.AIR);
            block.getRelative(BlockFace.DOWN, 2).setType(Material.AIR);
        }, 5);
        return true;
    }

    @Override
    protected void miniGameTick() {
        teleportSpectatorsBackIfTooFarAway();
        tickPlayers();
    }

    private void teleportSpectatorsBackIfTooFarAway() {
        int maxDistance = Math.max(0, plugin.getConfig().getInt(mapConfigPath + "map-radius", 100));
        getSpectators().stream()
                .filter(player -> distanceSquared(player.getLocation(), spectatorSpawn) > maxDistance * maxDistance)
                .forEach(player -> {
                    player.teleportAsync(spectatorSpawn);
                    player.sendActionBar(Utils.getComponentByString("&cOdleciałeś za daleko"));
                });
    }

    private double distanceSquared(@NotNull Location l1, @NotNull Location l2) {
        double a = l1.getX() - l2.getX();
        double b = l1.getZ() - l2.getZ();
        return a * a + b * b;
    }

    private void tickPlayers() {
        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> {
            player.setLevel(playersInMiniGame.size());
            player.setFireTicks(0);
        });
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
}
