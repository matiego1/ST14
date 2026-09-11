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
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ItemsRaceMiniGame extends MiniGame {
    public ItemsRaceMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private final int INVENTORY_SIZE = 41;
    private int mapSize = 500;
    private int prepareTime = 5;

    @Override
    protected @NotNull String getMiniGameName() {
        return "Items Race";
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
    }

    @Override
    protected void setUpGameRules(@NotNull World world) {
        world.setGameRule(GameRules.KEEP_INVENTORY, false);
        world.setGameRule(GameRules.IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRules.ENTITY_DROPS, true);
        world.setGameRule(GameRules.MOB_DROPS, true);
        world.setGameRule(GameRules.FALL_DAMAGE, true);
        world.setGameRule(GameRules.FIRE_SPREAD_RADIUS_AROUND_PLAYER, 128);
        world.setGameRule(GameRules.FIRE_DAMAGE, true);
        world.setGameRule(GameRules.NATURAL_HEALTH_REGENERATION, true);
        world.setGameRule(GameRules.ADVANCE_TIME, false);
        world.setGameRule(GameRules.ADVANCE_WEATHER, false);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        players.forEach(player -> {
            changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
            MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);
            player.setRespawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
        });
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == prepareTime) {
            World world = MiniGamesUtils.getMiniGamesSurvivalWorld();
            if (world != null) world.setGameRule(GameRules.PVP, true);

            sendActionBar("&aPvP włączone!");
        }

        getPlayers().forEach(this::checkPlayer);
    }

    private void checkPlayer(@NotNull Player player) {
        if (getNumberOfItems(player) < INVENTORY_SIZE) return;
        endGameWithWinner(player);
    }

    private int getNumberOfItems(@NotNull Player player) {
        ItemStack[] items = player.getInventory().getContents();
        Set<Material> materials = new HashSet<>(items.length);
        for (ItemStack item : items) {
            if (item == null) continue;
            materials.add(item.getType());
        }
        return materials.size();
    }

    @Override
    public void changePlayerStatusAfterDeath(@NotNull Player player) {}

    @EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        if (!isInMiniGame(player)) return;
        checkPlayer(player);
    }

    @EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onEntityPickUpItem(@NotNull EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!isInMiniGame(player)) return;
        checkPlayer(player);
    }
}
