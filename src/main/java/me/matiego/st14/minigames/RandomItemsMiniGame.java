package me.matiego.st14.minigames;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import me.matiego.st14.BossBarTimer;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.objects.minigames.MiniGameException;
import me.matiego.st14.objects.minigames.MiniGameType;
import me.matiego.st14.utils.MiniGamesUtils;
import me.matiego.st14.utils.Utils;
import me.matiego.st14.utils.WorldEditUtils;
import org.bukkit.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.stream.Collectors;

public class RandomItemsMiniGame extends MiniGame {
    public RandomItemsMiniGame(@NotNull Main plugin, @NotNull MiniGameType miniGameType, @Nullable String mapName) {
        super(plugin, miniGameType, mapName);
    }

    private List<Location> spawns = new ArrayList<>();
    private List<ItemStack> items = new ArrayList<>();
    private List<Material> blockedItems = new ArrayList<>();
    private int mapRadius = 50;
    private int giveItemInterval = 30;
    private int shrinkBorderBeforeEnd = 60;

    @Override
    protected @NotNull String getMiniGameName() {
        return "Losowe itemy";
    }

    @Override
    protected @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }

    @Override
    protected @NotNull MapType getMapType() {
        return MapType.PASTED_MAP;
    }

    @Override
    protected void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "radius", mapRadius));
        giveItemInterval = Math.max(5, plugin.getConfig().getInt(configPath + "give-item-interval", giveItemInterval));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "shrink-border-before-end", shrinkBorderBeforeEnd));
        if (totalMiniGameTime < shrinkBorderBeforeEnd) throw new MiniGameException("incorrect game times");

        blockedItems = plugin.getConfig().getStringList(configPath + "blocked-items").stream()
                .map(n -> {
                    try {
                        return Material.valueOf(n);
                    } catch (IllegalArgumentException e) {
                        Logs.warning("invalid item in random items minigame config: `" + n + "`");
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        blockedItems.addAll(List.of(
                Material.BARRIER,
                Material.LIGHT,
                Material.JIGSAW,
                Material.STRUCTURE_BLOCK,
                Material.STRUCTURE_VOID,
                Material.COMMAND_BLOCK,
                Material.REPEATING_COMMAND_BLOCK,
                Material.CHAIN_COMMAND_BLOCK,
                Material.COMMAND_BLOCK_MINECART,
                Material.DEBUG_STICK,
                Material.SPAWNER,
                Material.KNOWLEDGE_BOOK,
                Material.DRAGON_EGG,
                Material.TEST_BLOCK,
                Material.TEST_INSTANCE_BLOCK
        ));
        generateItemsList();
    }

    private void generateItemsList() {
        items = Arrays.stream(Material.values())
                .filter(m -> !m.isLegacy())
                .filter(m -> !m.isAir())
                .filter(Material::isItem)
                .filter(m -> !blockedItems.contains(m))
                .map(ItemStack::new)
                .collect(Collectors.toList());

        Collections.shuffle(items);
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
    }

    @Override
    protected void manipulatePastedMap(@NotNull World world, @NotNull Clipboard clipboard) throws MiniGameException {
        loadSpawns(world, clipboard);
        if (spawns.size() < 2) throw new MiniGameException("not enough spawns found");

        Utils.sync(() -> spectatorSpawn.getNearbyEntitiesByType(Item.class, mapRadius).forEach(Entity::remove));
    }

    private void loadSpawns(@NotNull World world, @NotNull Clipboard clipboard) {
        spawns = new ArrayList<>();

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
                    }
                }
            }
        }
    }

    @Override
    protected void setUpWorldBorder(@NotNull World world) {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTimeTicks(10);
    }

    @Override
    protected @NotNull BossBarTimer getBossBarTimer() {
        return new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
    }

    @Override
    protected void manipulatePlayersToStartGameWith(@NotNull List<Player> players) {
        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world != null) world.setGameRule(GameRules.PVP, true);

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

                i++;
            }
        }
    }

    @Override
    protected void miniGameTick() {
        if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
            worldBorder.changeSize(Math.max(1, 0.1 * mapRadius), shrinkBorderBeforeEnd * 20L);
        }

        if (miniGameTime == 1 || miniGameTime % giveItemInterval == 0) {
            giveRandomItemToPlayers();
        }
    }

    private void giveRandomItemToPlayers() {
        getPlayersInMiniGame().forEach(player -> {
            if (!MiniGamesUtils.isInAnyMiniGameWorld(player)) return;
            if (items.isEmpty()) generateItemsList();
            player.getInventory().addItem(items.removeLast());
        });
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
