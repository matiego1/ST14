package me.matiego.st14.minigames;

import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import org.bukkit.GameMode;
import org.bukkit.GameRules;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UHCMiniGame extends MiniGame {
    public UHCMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private int mapSize = 500;
    private int prepareTime = 60 * 10;
    private int shrinkBorderBeforeEnd = 60 * 10;
    private double goldenAppleChance = 0.005;

    @Override
    protected @NotNull String getMiniGameName() {
        return "UHC";
    }

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
        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", prepareTime));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "shrink-border-before-end", shrinkBorderBeforeEnd));
        if (prepareTime > totalMiniGameTime) throw new MiniGameException("incorrect game times");
        goldenAppleChance = Math.max(0, Math.min(1, plugin.getConfig().getDouble(configPath + "golden-apple-chance", goldenAppleChance)));
    }

    @Override
    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ENTITY_DROPS, true);
        world.setGameRule(GameRules.FALL_DAMAGE, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 128);
        world.setGameRule(GameRules.FIRE_DAMAGE, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, false);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, prepareTime, "&eRozpoczęcie walki");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        players.forEach(player -> {
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);

            player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20 * 30, 5));
            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 30, 5));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 5, 5));
        });

        MiniGamesUtils.spreadPlayers(spectatorSpawn, mapSize / 2);
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == prepareTime) {
            timer.stopTimerAndHideBossBar();
            timer = new BossBarTimer(plugin, totalMiniGameTime - prepareTime, "&eKoniec minigry");
            timer.startTimer();

            getPlayers().forEach(player -> {
                timer.showBossBarToPlayer(player);

                player.setHealth(20);
                player.setSaturation(20);
                player.setFoodLevel(20);
                player.setFireTicks(0);
            });

            sendMessage("PvP zostało włączone! Wszyscy gracze zostali uleczeni.");

            World world = MiniGamesUtils.getMiniGamesSurvivalWorld();
            if (world != null) world.setGameRule(GameRules.PVP, true);
        }

        if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
            sendMessage("Bariera zaczęła się zmniejszać!");
            worldBorder.changeSize(Math.max(1, 0.05 * mapSize), shrinkBorderBeforeEnd * 20L);
        }
    }

    @EventHandler
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        if (!isInMiniGame(event.getPlayer())) return;
        Block block = event.getBlock();
        if (!block.getType().name().contains("LEAVES")) return;

        if (random.nextDouble() >= goldenAppleChance) return;
        block.getWorld().dropItemNaturally(block.getLocation(), new ItemStack(Material.GOLDEN_APPLE));
    }
}
