package me.matiego.st14.minigames.handlers;

import com.sk89q.worldedit.math.BlockVector3;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class SpleefMiniGame extends MiniGame {
    public SpleefMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
    }

    private final String CONFIG_PATH = "minigames.spleef.";

    private String mapConfigPath = "minigames.skywars.maps";
    private Location spawn = null;

    @Override
    public @NotNull String getMiniGameName() {
        return "Spleef";
    }

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setRandomMapConfigPath();
        loadDataFromConfig(world);
        registerEvents();
        setUpGameRules(world);
        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eGenerowanie areny...");
        Utils.async(() -> {
            try {
                pasteMap(world);
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy generowaniu areny. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while pasting a map for the minigame", e);
                return;
            }

            try {
                if (!MiniGamesUtils.teleportPlayers(players.stream().toList(), spawn).get()) {
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

    private void setRandomMapConfigPath() throws MiniGameException {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(mapConfigPath);
        if (section == null) throw new MiniGameException("cannot find any map");

        List<String> maps = new ArrayList<>(section.getKeys(false));
        if (maps.isEmpty()) throw new MiniGameException("cannot find any map");

        Collections.shuffle(maps);

        mapConfigPath += "." + maps.get(0);
    }

    private void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, CONFIG_PATH + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getRelativeLocationFromConfig(baseLocation, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, false);
    }

    private void pasteMap(@NotNull World world) throws Exception {
        File file = getRandomMapFile();
        if (file == null) throw new NullPointerException("map file is null");

        MiniGamesUtils.pasteSchematic(
                world,
                BlockVector3.at(baseLocation.getBlockX(), baseLocation.getBlockY(), baseLocation.getBlockZ()),
                file
        );
    }

    private @Nullable File getRandomMapFile() {
        File dir = new File(plugin.getDataFolder(), "mini-games");
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }

        List<String> mapFiles = plugin.getConfig().getStringList(mapConfigPath + "map-files");
        Collections.shuffle(mapFiles);

        for (String mapFile : mapFiles) {
            File file = new File(dir, mapFile);
            if (file.exists()) {
                return file;
            }
        }
        return null;
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

        timer = new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
        timer.startTimer();

        playersToStartGameWith.forEach(player -> {
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
        int maxDistance = Math.max(0, plugin.getConfig().getInt(CONFIG_PATH + "map-radius", 100));
        getPlayers().stream()
                .filter(player -> distanceSquared(player.getLocation(), spectatorSpawn) > maxDistance * maxDistance)
                .filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR)
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
        return GameMode.SPECTATOR;
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
