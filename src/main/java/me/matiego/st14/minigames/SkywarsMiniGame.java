package me.matiego.st14.minigames;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.utils.Utils;
import me.matiego.st14.utils.WorldEditUtils;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SkywarsMiniGame extends MiniGame {
    public SkywarsMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private final List<Location> spawns = new ArrayList<>();

    private int mapRadius = 100;
    private int prepareTime = 60;
    private int shrinkBorderBeforeEnd = 180;

    @Override
    public @NotNull String getMiniGameName() {
        return "Skywars";
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected boolean shouldPasteMap() {
        return true;
    }

    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "radius", mapRadius));
        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "shrink-border-before-end", shrinkBorderBeforeEnd));
        if (totalMiniGameTime < prepareTime + shrinkBorderBeforeEnd) throw new MiniGameException("incorrect game times");
    }

    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
    }

    private void setUpSkywarsWorldBorder() {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTime(10);
    }

    @Override
    protected void manipulatePastedMap(@NotNull World world, @NotNull Clipboard clipboard) throws MiniGameException {
        Utils.sync(() -> sendActionBar("&eGenerowanie skrzynek..."));
        loadSpawnsAndGenerateChests(world, clipboard);
        if (spawns.size() < 2) throw new MiniGameException("not enough spawns found");
        setUpSkywarsWorldBorder();

        Utils.sync(() -> spectatorSpawn.getNearbyEntitiesByType(Item.class, mapRadius).forEach(Entity::remove));
    }

    private void loadSpawnsAndGenerateChests(@NotNull World world, @NotNull Clipboard clipboard) {
        for (int x = 0; x <= clipboard.getDimensions().getX(); x++) {
            for (int y = 0; y <= clipboard.getDimensions().getY(); y++) {
                for (int z = 0; z <= clipboard.getDimensions().getZ(); z++) {

                    BlockVector3 blockLocation = BlockVector3.at(x, y, z).add(clipboard.getMinimumPoint());
                    Material blockMaterial = BukkitAdapter.adapt(clipboard.getBlock(blockLocation).getBlockType());
                    if (blockMaterial == null || !blockMaterial.toString().contains("SIGN")) continue;

                    BaseBlock baseBlock = clipboard.getFullBlock(blockLocation);
                    if (baseBlock == null) continue;

                    String line1 = WorldEditUtils.getSignLine(baseBlock, 1).toLowerCase();
                    if (line1.contains("[spectator]")) {
                        Location loc = new Location(world, baseLocation.getBlockX() + x + 0.5, baseLocation.getBlockY() + y - 1, baseLocation.getBlockZ() + z + 0.5);
                        Utils.sync(() -> loc.getBlock().setType(Material.AIR));
                        spectatorSpawn = loc;
                    } else if (line1.contains("[spawn]")) {
                        Location loc = new Location(world, baseLocation.getBlockX() + x + 0.5, baseLocation.getBlockY() + y - 1, baseLocation.getBlockZ() + z + 0.5);
                        Utils.sync(() -> loc.getBlock().setType(Material.AIR));
                        spawns.add(loc);
                    } else if (line1.contains("[chest]")) {
                        Location loc = new Location(world, baseLocation.getBlockX() + x, baseLocation.getBlockY() + y - 1, baseLocation.getBlockZ() + z);
                        Utils.sync(() -> {
                            loc.getBlock().setType(Material.CHEST);
                            generateChest(loc, WorldEditUtils.getSignLine(baseBlock, 2).replace(":", ""));
                        });
                    }
                }
            }
        }
    }

    private void generateChest(@NotNull Location location, @NotNull String type) {
        if (!(location.getBlock().getState() instanceof Container chest)) return;

        Utils.async(() -> {
            List<ItemStack> items = getRandomItems(type);
            for (int i = 0; i < Math.min(chest.getInventory().getSize(), items.size()); i++) {
                chest.getInventory().setItem(i, items.get(i));
            }
        });
    }

    private @NotNull List<ItemStack> getRandomItems(@NotNull String type) {
        int max = Math.min(27, plugin.getConfig().getInt(configPath + "chests." + type + ".max-items", 1));
        int min = Math.max(1, plugin.getConfig().getInt(configPath + "chests." + type + ".min-items", 15));
        if (min > max) {
            int x = min;
            min = max;
            max = x;
        }

        List<ItemStack> items = new ArrayList<>();
        for (String string : plugin.getConfig().getStringList(configPath + "chests." + type + ".items")) {
            ItemStack item = MiniGamesUtils.getItemStackFromString(string);
            if (item != null) items.add(item);
        }

        Collections.shuffle(items);

        int itemsAmount = (int) Math.min(max, Math.max(min, Math.ceil(ThreadLocalRandom.current().nextGaussian()) + Math.floor((max + min) / 2d) - plugin.getConfig().getInt(configPath + "chests." + type + ".subtract-from-mean", 0)));
        items = items.subList(0, itemsAmount);

        while (items.size() < 27) {
            items.add(new ItemStack(Material.AIR));
        }

        Collections.shuffle(items);

        return items;
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eOtwarcie wysp");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        Collections.shuffle(spawns);
        int i = 0;
        for (Player player : players) {
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
            if (i >= spawns.size()) {
                changePlayerStatus(player, PlayerStatus.SPECTATOR);
                player.teleportAsync(spectatorSpawn);
                MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                sendMessage("Gracz " + player.getName() + " obserwuję minigrę, ponieważ nie starczyło dla niego miejsca.");
            } else {
                changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
                player.teleportAsync(spawns.get(i));
                MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);

                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(spawns.get(i));
                border.setSize(Math.max(5, plugin.getConfig().getInt(mapConfigPath + "island-radius")));
                border.setDamageAmount(5);
                border.setDamageBuffer(0);
                border.setWarningDistance(0);
                player.setWorldBorder(border);

                i++;
            }
        }
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eKoniec minigry");
            timer.startTimer();

            getPlayers().forEach(player -> {
                timer.showBossBarToPlayer(player);
                player.setWorldBorder(worldBorder);
            });

            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setPVP(true);
        }

        if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
            worldBorder.setSize(Math.max(1, 0.1 * mapRadius), TimeUnit.SECONDS, shrinkBorderBeforeEnd);
            getPlayersInMiniGame().forEach(player -> player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, shrinkBorderBeforeEnd * 20, 255, false, false, true)));
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (getPlayerStatus(player) != PlayerStatus.SPECTATOR) return;
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFoodLevelChange(@NotNull FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (getPlayerStatus(player) != PlayerStatus.SPECTATOR) return;
        event.setFoodLevel(20);
    }
}
