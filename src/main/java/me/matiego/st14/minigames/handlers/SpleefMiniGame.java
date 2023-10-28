package me.matiego.st14.minigames.handlers;

import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.BoundingBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.Comparator;
import java.util.List;

public class SpleefMiniGame extends MiniGame {
    public SpleefMiniGame(@NotNull Main plugin, @Range(from = 0, to = Integer.MAX_VALUE) int totalMiniGameTime, @NotNull String configPath, @Nullable String mapName) {
        super(plugin, totalMiniGameTime, configPath, mapName);
    }

    private Location spawn;

    @Override
    public @NotNull String getMiniGameName() {
        return "Spleef";
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
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            player.teleportAsync(spawn);
            MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
            player.setBedSpawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
            giveToolsToPlayer(player);
        });
    }

    private void giveToolsToPlayer(@NotNull Player player) {
        player.getInventory().addItem(
                createTool(Material.NETHERITE_SHOVEL),
                createTool(Material.NETHERITE_PICKAXE),
                createTool(Material.NETHERITE_HOE)
        );
    }

    private @NotNull ItemStack createTool(Material toolMaterial) {
        ItemStack tool = new ItemStack(toolMaterial);

        ItemMeta meta = tool.getItemMeta();
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE, ItemFlag.HIDE_ENCHANTS);
        tool.setItemMeta(meta);

        tool.addUnsafeEnchantment(Enchantment.DIG_SPEED, 50);

        return tool;
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

        playersInMiniGame.sort(Comparator.comparingInt(a -> a.getLocation().getBlockY()));
        if (playersInMiniGame.size() < 2) return;
        Player max1 = playersInMiniGame.get(playersInMiniGame.size() - 1);
        Player max2 = playersInMiniGame.get(playersInMiniGame.size() - 2);

        if (max1.getLocation().getBlockY() - max2.getLocation().getBlockY() > Math.max(0, plugin.getConfig().getInt(mapConfigPath + "vertical-max-distance", 10)) && miniGameTime % 3 == 0) {
            breakBlocksUnderPlayer(max1);
        }
    }

    private void breakBlocksUnderPlayer(@NotNull Player player) {
        if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) return;

        BoundingBox box = player.getBoundingBox().clone();
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
        runTaskLater(() -> {
            block.setType(Material.AIR);
            block.getRelative(BlockFace.DOWN).setType(Material.AIR);
        }, 5);
        return true;
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
