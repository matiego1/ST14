package me.matiego.st14.minigames;

import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.BossBarTimer;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public class TagMiniGame extends MiniGame {
    public TagMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private Location spawn = null;
    private int prepareTime = 15;
    private int breakFromGlowing = 60;
    private int lastChange = 0;
    private Player tag = null;

    @Override
    public @NotNull String getMiniGameName() {
        return "Berek";
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

        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        breakFromGlowing = Math.max(0, plugin.getConfig().getInt(configPath + "break-from-glowing", prepareTime));
    }

    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, false);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, false);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setGameRule(GameRule.NATURAL_REGENERATION, false);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eRozpoczęcie berka");
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
        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eKoniec minigry");
            timer.startTimer();
            getPlayers().forEach(player -> timer.showBossBarToPlayer(player));

            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setPVP(true);

            lastChange = miniGameTime;
            setRandomTag();
        }

        tickPlayers();
    }

    private void tickPlayers() {
        List<Player> playersInMiniGame = getPlayersInMiniGame();
        sendActionBar("&eGracz " + tag.getName() + " goni!");
        playersInMiniGame.forEach(player -> tickPlayer(player, playersInMiniGame.size()));
    }

    private void tickPlayer(@NotNull Player player, int playersLeft)  {
        player.setLevel(playersLeft);
        player.setFireTicks(0);

        if (lastChange - miniGameTime >= breakFromGlowing) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 2 * 20, 255, false, false, true));
        }
    }

    private void setRandomTag() {
        List<Player> players = getPlayersInMiniGame();
        if (players.isEmpty()) return;
        Collections.shuffle(players);
        tag = players.get(0);
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
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        if (event.getDamager().getType() != EntityType.PLAYER) return;

        //todo: zmień berka
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
