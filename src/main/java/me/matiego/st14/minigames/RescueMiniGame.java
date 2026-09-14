package me.matiego.st14.minigames;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
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

public class RescueMiniGame extends MiniGame {
    public RescueMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private int mapSize = 500;
    private int startWorldBorderSize = 5;
    private int prepareTime = 5;
    private int compassRefreshInterval = 60;
    private UUID escaperUuid;
    private WorldBorder prepareWorldBorder;

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
        world.setGameRule(GameRules.KEEP_INVENTORY, true);
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
        return new BossBarTimer(plugin, prepareTime, "&eWypuszczenie ratujących");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        int i = Utils.getRandomNumber(0, players.size() - 1);
        escaperUuid = players.get(i).getUniqueId();

        prepareWorldBorder = Bukkit.createWorldBorder();
        prepareWorldBorder.setCenter(spectatorSpawn);
        prepareWorldBorder.setSize(startWorldBorderSize);
        prepareWorldBorder.setWarningDistance(0);
        prepareWorldBorder.setDamageBuffer(0);
        prepareWorldBorder.setDamageAmount(5);
        prepareWorldBorder.setWarningTimeTicks(10);

        players.forEach(player -> {
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            if (isEscaper(player)) {
                MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
            } else {
                MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                player.setWorldBorder(prepareWorldBorder);
            }
            player.teleportAsync(spectatorSpawn);
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
        if (escaper == null || miniGameTime == totalMiniGameTime) {
            players.removeIf(this::isEscaper);
            endGameWithWinners("Ratujący", players);
            return;
        }

        if (miniGameTime < prepareTime) sendActionBar("&eUcieka gracz " + escaper.getName());
        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eWygrana ratujących");
            timer.startTimer();

            players.forEach(player -> {
                timer.showBossBarToPlayer(player);

                if (isEscaper(player)) return;
                player.setWorldBorder(worldBorder);
                MiniGamesUtils.healPlayer(player, GameMode.CREATIVE);
                player.give(getCompass(escaper.getLocation(), escaper.getName()));
            });

            sendMessage("Ratujący zostali wypuszczeni!");
        }

        if (miniGameTime > prepareTime && miniGameTime % compassRefreshInterval == 0) {
            players.forEach(player -> updateCompass(player, escaper.getLocation(), escaper.getName()));
            sendActionBar("&aKompasy zaktualizowane!");
        }
    }

    @Override
    protected void changePlayerStatusAfterDeath(@NotNull Player player) {
        if (!isEscaper(player)) {
            runTaskLater(() -> {
                MiniGamesUtils.healPlayer(player, miniGameTime >= prepareTime ? GameMode.CREATIVE : GameMode.ADVENTURE);
                player.setWorldBorder(prepareWorldBorder);
            }, 5);
        }
        endGameWithWinner(player);
    }

    private boolean isEscaper(@NotNull Player player) {
        return player.getUniqueId().equals(escaperUuid);
    }

    private @NotNull ItemStack getCompass(@NotNull Location location, @NotNull String name) {
        ItemStack item = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        meta.displayName(Utils.getComponentByString(name).decoration(TextDecoration.ITALIC, false));
        meta.setLodestoneTracked(false);
        meta.setLodestone(location);
        item.setItemMeta(meta);

        return item;
    }

    private void updateCompass(@NotNull Player player, @NotNull Location newLocation, @NotNull String name) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            if (item.getType() != Material.COMPASS) continue;

            CompassMeta meta = (CompassMeta) item.getItemMeta();
            if (!meta.hasLodestone()) continue;
            Component displayName = meta.displayName();
            if (displayName == null || !Utils.getPlainTextByComponent(displayName).equals(name)) continue;

            meta.setLodestone(newLocation);
            item.setItemMeta(meta);

            return;
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
