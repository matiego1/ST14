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
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class ManhuntMiniGame extends MiniGame {
    public ManhuntMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private int mapSize = 500;
    private int startWorldBorderSize = 5;
    private int prepareTime = 15;
    private int compassRefreshInterval = 30;
    private UUID escaperUuid;

    @Override
    protected @NotNull GameMode getSpectatorGameMode() {
        return GameMode.SPECTATOR;
    }

    @Override
    public @NotNull MapType getMapType() {
        return MapType.SURVIVAL;
    }

    @Override
    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        mapSize = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "size", mapSize));
        startWorldBorderSize = Math.max(1, plugin.getConfig().getInt(configPath + "start-world-border-size", startWorldBorderSize));
        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        compassRefreshInterval = Math.max(1, plugin.getConfig().getInt(configPath + "compass-refresh-interval", compassRefreshInterval));
        if (prepareTime > totalMiniGameTime) throw new MiniGameException("incorrect game times");
    }

    @Override
    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ENTITY_DROPS, true);
        world.setGameRule(GameRules.FALL_DAMAGE, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 128);
        world.setGameRule(GameRules.FIRE_DAMAGE, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eWygrana uciekającego");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        int i = Utils.getRandomNumber(0, players.size() - 1);
        Player escaper = players.get(i);
        escaperUuid = escaper.getUniqueId();
        sendMessage("Ucieka gracz &d" + escaper.getName() + "&e!");

        WorldBorder worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(startWorldBorderSize);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTimeTicks(10);

        players.forEach(player -> {
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            if (isEscaper(player)) {
                MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
            } else {
                MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                player.setWorldBorder(worldBorder);
            }
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);

            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 5, 5));
        });
    }

    @Override
    protected void miniGameTick() {
        List<Player> players = getPlayersInMiniGame();
        Player escaper = players.stream()
                .filter(this::isEscaper)
                .findFirst()
                .orElse(null);
        if (escaper == null) {
            players.removeIf(this::isEscaper);
            endGameWithWinners("Goniący", players);
            return;
        }

        if (miniGameTime == totalMiniGameTime) {
            endGameWithWinner(escaper);
            return;
        }

        if (miniGameTime < prepareTime) sendActionBar("&eUcieka gracz " + escaper.getName());
        if (miniGameTime == prepareTime) {
            players.forEach(player -> {
                if (isEscaper(player)) return;
                player.setWorldBorder(worldBorder);
                MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
                player.give(getCompass(escaper.getLocation()));
            });

            World world = MiniGamesUtils.getMiniGamesSurvivalWorld();
            if (world != null) world.setGameRule(GameRules.PVP, true);

            sendActionBar("&aPvP włączone!");
        }

        if (miniGameTime % compassRefreshInterval == 0) {
            players.forEach(player -> updateCompass(player, escaper.getLocation()));
            sendActionBar("&aKompasy zaktualizowane!");
        }
    }

    private boolean isEscaper(@NotNull Player player) {
        return player.getUniqueId().equals(escaperUuid);
    }

    private @NotNull ItemStack getCompass(@NotNull Location location) {
        ItemStack item = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        meta.setLodestoneTracked(false);
        meta.setLodestone(location);
        item.setItemMeta(meta);

        return item;
    }

    private void updateCompass(@NotNull Player player, @NotNull Location newLocation) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.COMPASS) continue;

            CompassMeta meta = (CompassMeta) item.getItemMeta();
            if (!meta.hasLodestone()) continue;

            meta.setLodestone(newLocation);
            item.setItemMeta(meta);
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        if (event.getItemDrop().getItemStack().getType() != Material.COMPASS) return;
        if (isEscaper(event.getPlayer())) return;
        event.setCancelled(true);
    }
}
