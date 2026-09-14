package me.matiego.st14.minigames;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class TagMiniGame extends MiniGame {
    public TagMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private Location spawn = null;
    private int prepareTime = 15;
    private final Set<UUID> tagged = new HashSet<>();

    @Override
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount() {
        return 3;
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    public @NotNull MapType getMapType() {
        return MapType.NORMAL_MAP;
    }

    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        spawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spawn");
        if (spawn == null) throw new MiniGameException("cannot load spawn location");
        spectatorSpawn = MiniGamesUtils.getLocationFromConfig(world, mapConfigPath + "spectator-spawn");
        if (spectatorSpawn == null) throw new MiniGameException("cannot load spectator spawn location");

        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
    }

    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ENTITY_DROPS, false);
        world.setGameRule(GameRules.FALL_DAMAGE, true);
        world.setGameRule(GameRules.FIRE_DAMAGE, false);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 0);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eRozpoczęcie berka");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
        meta.setColor(Color.BLACK);
        chestplate.setItemMeta(meta);

        players.forEach(player -> {
            player.teleportAsync(spawn);
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
            player.setRespawnLocation(spawn, true);
            timer.showBossBarToPlayer(player);

            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, totalMiniGameTime * 20, 255, false, false, true));
            player.getInventory().setChestplate(chestplate.clone());
        });
    }

    @Override
    protected void miniGameTick() {
        List<Player> players = getPlayersInMiniGame();

        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eKoniec minigry");
            timer.startTimer();
            getPlayers().forEach(player -> timer.showBossBarToPlayer(player));

            World world = MiniGamesUtils.getMiniGamesWorld();
            if (world != null) world.setGameRule(GameRules.PVP, true);

            tagged.clear();
            tagRandomPlayer(players);
        }

        players.forEach(player -> {
            player.setLevel(players.size());
            player.setFireTicks(0);
        });
    }

    @Override
    public void onPlayerQuit(@NotNull Player player) {
        super.onPlayerQuit(player);
        if (lobby) return;

        tagged.remove(player.getUniqueId());
        List<Player> players = getPlayersInMiniGame();

        int tags = tagged.size();
        int notTags = players.size() - tags;

        if (tags == 0 && notTags == 2) {
            scheduleStopMiniGameAndSendReason("Koniec minigry! Brak zwycięzcy. Zostało za mało graczy, żeby wylosować nowego berka.", "&dKoniec minigry", "");
            return;
        }

        if (tags == 1 && notTags == 1) {
            players.removeIf(p -> tagged.contains(p.getUniqueId()));
            endGameWithWinner(players.getFirst());
            return;
        }

        if (tags == 0) tagRandomPlayer(players);
    }

    private void tagRandomPlayer(@NotNull List<Player> players) {
        if (players.isEmpty()) return;
        int i = Utils.getRandomNumber(0, players.size() - 1);
        Player player = players.get(i);

        sendMessage("Gracz " + player.getName() + " został wylosowany na pierwszego berka!");
        tagged.add(player.getUniqueId());
        markTaggedPlayer(player);
    }

    private void markTaggedPlayer(@NotNull Player player) {
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta meta = (LeatherArmorMeta) chestplate.getItemMeta();
        meta.setColor(Color.YELLOW);
        chestplate.setItemMeta(meta);
        player.getInventory().setChestplate(chestplate);
    }

    @Override
    protected void changePlayerStatusAfterDeath(@NotNull Player player) {
        runTaskLater(() -> player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, totalMiniGameTime * 20, 255, false, false, true)), 5);
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        event.setCancelled(true);
    }

    @EventHandler (ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (!isInMiniGame((Player) event.getWhoClicked())) return;
        event.setCancelled(true);
    }

    @EventHandler (ignoreCancelled = true)
    public void onEntityDamageByBlock(@NotNull EntityDamageByBlockEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setCancelled(true);
    }

    @EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityDamageByEntity(@NotNull EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        if (!(event.getDamager() instanceof Player damager)) return;
        if (!isInMiniGame(damager)) return;

        event.setCancelled(true);

        if (!tagged.contains(damager.getUniqueId())) return;
        if (tagged.contains(player.getUniqueId())) return;

        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, totalMiniGameTime * 20, 255, false, false, true));
        sendMessage("Gracz " + player.getName() + " dołącza do drużyny berków!");
        tagged.add(player.getUniqueId());
        markTaggedPlayer(player);

        List<Player> players = getPlayersInMiniGame();
        if (tagged.size() >= players.size() - 1) {
            Player winner = players.stream()
                    .filter(p -> !tagged.contains(p.getUniqueId()))
                    .findFirst()
                    .orElse(null);

            if (winner != null) {
                endGameWithWinner(winner);
            } else {
                scheduleStopMiniGameAndSendReason("Koniec minigry! Napotkano błąd przy wyłanianiu zwycięzcy.", "&dKoniec minigry", "");
            }
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onFoodLevelChange(@NotNull FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        event.setFoodLevel(20);
    }
}
