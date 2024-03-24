package me.matiego.st14.minigames;

import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.BossBarTimer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class PvPMiniGame extends MiniGame {
    public PvPMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private final List<Location> spawns = new ArrayList<>();
    private final List<ItemStack> items = new ArrayList<>();
    private int mapRadius = 100;
    private int prepareTime = 30;
    private int shrinkBorderBeforeEnd = 180;

    @Override
    public @NotNull String getMiniGameName() {
        return "PvP";
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
//        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
//        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");
        for (String spawn : plugin.getConfig().getStringList(mapConfigPath + "spawns")) {
            spawns.add(MiniGamesUtils.getLocationFromString(world, spawn));
        }
        if (spawns.size() < 2) throw new MiniGameException("not enough spawns found");

        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "radius", mapRadius));
        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "shrink-border-before-end", shrinkBorderBeforeEnd));
        if (totalMiniGameTime < prepareTime + shrinkBorderBeforeEnd) throw new MiniGameException("incorrect game times");

        List<String> itemsNames = plugin.getConfig().getStringList(mapConfigPath + "items");
        if (itemsNames.isEmpty()) {
            itemsNames = plugin.getConfig().getStringList(configPath + "items");
        }
        for (String string : itemsNames) {
            ItemStack item = MiniGamesUtils.getItemStackFromString(string);
            if (item != null) items.add(item);
        }
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

    @Override
    protected void setUpWorldBorder(@NotNull World world) {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTime(10);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eRozpoczęcie bitwy");
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
                giveToolsToPlayer(player);

                i++;
            }
        }
    }

    private void giveToolsToPlayer(@NotNull Player player) {
        for (ItemStack item : items) {
            switch (item.getType().toString().replaceFirst(".+_", "")) {
                case "HELMET" -> player.getInventory().setItem(EquipmentSlot.HEAD, item);
                case "CHESTPLATE" -> player.getInventory().setItem(EquipmentSlot.CHEST, item);
                case "LEGGINGS" -> player.getInventory().setItem(EquipmentSlot.LEGS, item);
                case "BOOTS" -> player.getInventory().setItem(EquipmentSlot.FEET, item);
                case "SHIELD" -> player.getInventory().setItem(EquipmentSlot.OFF_HAND, item);
                default -> player.getInventory().addItem(item);
            }
        }
    }

    @Override
    protected void miniGameTick() {
        tickPlayers();

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

    private void tickPlayers() {
        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> player.setLevel(playersInMiniGame.size()));
    }

    @EventHandler(ignoreCancelled = true)
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
