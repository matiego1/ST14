package me.matiego.st14.minigames.handlers;

import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.Set;

public class SnowballsBattleMiniGame extends MiniGame {
    public SnowballsBattleMiniGame(@NotNull Main plugin, @Range(from = 0, to = Integer.MAX_VALUE) int totalMiniGameTime, @NotNull String configPath, @Nullable String mapName) {
        super(plugin, totalMiniGameTime, configPath, mapName);
    }

    private Location spawn = null;
    private int prepareTime = 60;
    private int levelUpBeforeEnd = 60;
    private int increaseHealthInterval = 30;

    @Override
    public @NotNull String getMiniGameName() {
        return "Bitwa na śnieżki";
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        if (isMiniGameStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setMapConfigPath();
        loadDataFromConfig(world);
        registerEvents();
        setUpGameRules(world);
        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eTeleportowanie graczy...");
        Utils.async(() -> {
            try {
                if (!MiniGamesUtils.teleportPlayers(players.stream().toList(), spectatorSpawn).get()) {
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

    private void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        spawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        levelUpBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "level-up-before-end", levelUpBeforeEnd));
        increaseHealthInterval = Math.max(1, plugin.getConfig().getInt(configPath + "increase-health-interval", increaseHealthInterval));
        if (totalMiniGameTime < prepareTime + levelUpBeforeEnd) throw new MiniGameException("incorrect game times");
    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    @Override
    protected void onCountdownEnd() {
        List<Player> playersToStartGameWith = getPlayers();

        if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
            scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
            return;
        }

        lobby = false;

        sendMessage("&dMinigra rozpoczęta. &ePowodzenia!");
        sendTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

        timer = new BossBarTimer(plugin, prepareTime, "&eRozpoczęcie bitwy");
        timer.startTimer();

        playersToStartGameWith.forEach(player -> {
            player.teleportAsync(spawn);
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setBedSpawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
        });
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eKoniec minigry");
            timer.startTimer();
            getPlayers().forEach(player -> timer.showBossBarToPlayer(player));

            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setPVP(true);
        }

        tickPlayers();
    }

    private void tickPlayers() {
        List<Player> playersInMiniGame = getPlayersInMiniGame();
        playersInMiniGame.forEach(player -> tickPlayer(player, playersInMiniGame.size()));
    }

    private void tickPlayer(@NotNull Player player, int playersLeft)  {
        player.setLevel(playersLeft);
        player.setFireTicks(0);

        if (miniGameTime % increaseHealthInterval == 0) {
            increasePlayerHealth(player);
        }

        if (miniGameTime == totalMiniGameTime - levelUpBeforeEnd) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, levelUpBeforeEnd * 20, 255, false, false, true));
        }

        giveSnowballsToPlayer(player);
    }

    private void increasePlayerHealth(@NotNull Player player) {
        player.setHealth(Math.min(player.getHealth() + 2, 20));
        player.spawnParticle(Particle.HEART, player.getLocation().add(0, 0.5, 0), 5, 0.5, 1, 0.5, 0.00001);
        player.playSound(player.getLocation().add(0, 0.5, 0), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, SoundCategory.NEUTRAL, 3, 1);
    }

    private void giveSnowballsToPlayer(@NotNull Player player) {
        double damageAmount = plugin.getConfig().getDouble(configPath + "snowball-damage.normal", 1);
        int maxSnowballsAmount = plugin.getConfig().getInt(configPath + "max-snowballs-amount.normal", 32);
        int snowballsPerSecond = plugin.getConfig().getInt(configPath + "snowballs-per-second.normal", 1);

        if (miniGameTime >= totalMiniGameTime - levelUpBeforeEnd) {
            damageAmount = plugin.getConfig().getDouble(configPath + "snowball-damage.level-up", 4);
            maxSnowballsAmount = plugin.getConfig().getInt(configPath + "max-snowballs-amount.level-up", 5);
            snowballsPerSecond = plugin.getConfig().getInt(configPath + "snowballs-per-second.level-up", 1);
        }

        Inventory inv = player.getInventory();
        ItemStack snowball = plugin.getEntityDamageByEntityListener().getSnowball(damageAmount);
        if (inv.containsAtLeast(snowball, maxSnowballsAmount)) return;
        snowball.setAmount(snowballsPerSecond);
        inv.addItem(snowball);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        event.setUseInteractedBlock(Event.Result.DENY);
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityDamageByBlock(@NotNull EntityDamageByBlockEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FIRE && event.getCause() != EntityDamageEvent.DamageCause.FIRE_TICK) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setCancelled(true);
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() == EntityType.SNOWBALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setCancelled(true);
    }

    @EventHandler (ignoreCancelled = true)
    public void onCraftItem(@NotNull CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
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
