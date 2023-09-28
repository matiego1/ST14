package me.matiego.st14.minigames.handlers;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.world.block.BaseBlock;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.minigames.MiniGame;
import me.matiego.st14.minigames.MiniGameException;
import me.matiego.st14.minigames.MiniGamesUtils;
import me.matiego.st14.objects.BossBarTimer;
import me.matiego.st14.utils.Utils;
import me.matiego.st14.utils.WorldEditUtils;
import org.bukkit.*;
import org.bukkit.block.Container;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class SkywarsMiniGame extends MiniGame {
    public SkywarsMiniGame(@NotNull Main plugin, int totalGameTimeInSeconds) {
        super(plugin, totalGameTimeInSeconds);
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
    public void startMiniGame(@NotNull Set<Player> players, @NotNull Player sender) throws MiniGameException {
        //noinspection ConstantValue
        if (true) throw new MiniGameException("not fixed yet");
        if (isMiniGameStarted()) throw new MiniGameException("minigame is already started");

        clearExistingData();
        isMiniGameStarted = true;
        lobby = true;

        configPath = "minigames.skywars.";

        World world = MiniGamesUtils.getMiniGamesWorld();
        if (world == null) throw new MiniGameException("cannot load world");

        setRandomMapConfigPath(configPath + "maps");
        loadDataFromConfig(world);
        setUpGameRules(world);
        setUpWorldBorder();
        registerEvents();
        broadcastMiniGameStartMessage(sender);

        for (Player player : players) {
            changePlayerStatus(player, PlayerStatus.SPECTATOR);
            MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
        }

        sendActionBar("&eGenerowanie areny...");
        Utils.async(() -> {
            try {
                File file = getRandomMapFile();
                if (file == null) throw new NullPointerException("map file is null");
                Clipboard clipboard = pasteMap(world, file);
                loadSpawnsAndGenerateChests(world, clipboard);
                if (spawns.isEmpty()) throw new MiniGameException("no spawns found");
            } catch (Exception e) {
                Utils.sync(() -> scheduleStopMiniGameAndSendReason("Napotkano niespodziewany błąd przy generowaniu areny. Minigra anulowana.", "&dStart anulowany", ""));
                Logs.error("An error occurred while pasting a map for the minigame", e);
                return;
            }

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

            Utils.sync(() -> startCountdown(15));
        });
    }

    private void loadDataFromConfig(@NotNull World world) throws MiniGameException {
        baseLocation = MiniGamesUtils.getLocationFromConfig(world, configPath + "base-location");
        if (baseLocation == null) throw new MiniGameException("cannot load base location");

        mapRadius = Math.max(5, plugin.getConfig().getInt(mapConfigPath + "radius", 100));

        prepareTime = Math.max(0, plugin.getConfig().getInt(configPath + "prepare-time", 30));
        shrinkBorderBeforeEnd = Math.max(0, plugin.getConfig().getInt(configPath + "shrink-border-before-end", 180));
        if (totalMiniGameTime < prepareTime + shrinkBorderBeforeEnd) throw new MiniGameException("incorrect game times");

    }

    private void setUpGameRules(@NotNull World world) {
        world.setPVP(false);
        world.setGameRule(GameRule.KEEP_INVENTORY, false);
        world.setGameRule(GameRule.DO_IMMEDIATE_RESPAWN, true);
        world.setGameRule(GameRule.DO_ENTITY_DROPS, true);
        world.setGameRule(GameRule.FALL_DAMAGE, true);
        world.setGameRule(GameRule.DO_FIRE_TICK, true);
        world.setGameRule(GameRule.FIRE_DAMAGE, true);
        world.setGameRule(GameRule.NATURAL_REGENERATION, true);
    }

    private void setUpWorldBorder() {
        worldBorder = Bukkit.createWorldBorder();
        worldBorder.setCenter(spectatorSpawn);
        worldBorder.setSize(mapRadius);
        worldBorder.setWarningDistance(0);
        worldBorder.setDamageBuffer(0);
        worldBorder.setDamageAmount(5);
        worldBorder.setWarningTime(10);
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
                        spectatorSpawn = new Location(world, baseLocation.getBlockX() + x + 0.5, baseLocation.getBlockY() + y + 0.5, baseLocation.getBlockZ() + z + 0.5);
                    } else if (line1.contains("[spawn]")) {
                        spawns.add(new Location(world, baseLocation.getBlockX() + x + 0.5, baseLocation.getBlockY() + y + 0.5, baseLocation.getBlockZ() + z + 0.5));
                    } else if (line1.contains("[chest]")) {
                        generateChest(new Location(world, baseLocation.getBlockX() + x, baseLocation.getBlockY() + y, baseLocation.getBlockZ() + z), WorldEditUtils.getSignLine(baseBlock, 2).replace(":", ""));
                    }
                }
            }
        }
    }

    private void generateChest(@NotNull Location location, @NotNull String type) {
        if (!(location.getBlock().getState() instanceof Container chest)) return;

        List<ItemStack> items = getRandomItems(type);
        for (int i = 0; i < Math.min(chest.getInventory().getSize(), items.size()); i++) {
            chest.getInventory().setItem(i, items.get(i));
        }
    }

    private @NotNull List<ItemStack> getRandomItems(@NotNull String type) {
        int max = Math.min(27, plugin.getConfig().getInt(configPath + "chests." + type + ".max-items", 1));
        int min = Math.max(1, plugin.getConfig().getInt(configPath + "chests." + type + ".min-items", 15));
        if (min > max) {
            int c = min;
            min = max;
            max = c;
        }
        List<String> items = plugin.getConfig().getStringList(configPath + "chests." + type + ".items");
        Collections.shuffle(items);

        int itemsAmount = (int) Math.min(Math.max(Math.ceil(ThreadLocalRandom.current().nextGaussian()), min), max);

        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < Math.min(itemsAmount, items.size()); i++) {
            String[] values = items.get(i).split(";");
            if (values.length < 3) {
                Logs.warning("incorrect skywars item! (#1) config value: `" + items.get(i) + "`");
                continue;
            }
            Material material = null;
            try {
                material = Material.valueOf(values[0]);
            } catch (Exception ignored) {}
            if (material == null) {
                Logs.warning("incorrect skywars item! (#2) config value: `" + items.get(i) + "`");
                continue;
            }

            int amount = -1;
            try {
                amount = Utils.getRandomNumber(Integer.parseInt(values[1]), Integer.parseInt(values[2]));
            } catch (Exception ignored) {}
            if (amount <= 0) {
                Logs.warning("incorrect skywars item! (#3) config value: `" + items.get(i) + "`");
                continue;
            }

            ItemStack item = new ItemStack(material, amount);

            for (int j = 3; j < values.length; j++) {
                String[] enchantmentValues = values[j].split(",");
                if (enchantmentValues.length != 2) {
                    Logs.warning("incorrect skywars item! (#4) config value: `" + items.get(i) + "`");
                    continue;
                }
                Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchantmentValues[0]));
                if (enchantment == null) {
                    Logs.warning("incorrect skywars item! (#5) config value: `" + items.get(i) + "`");
                    continue;
                }
                int level = -1;
                try {
                    level = Integer.parseInt(enchantmentValues[1]);
                } catch (Exception ignored) {}
                if (level <= 0) {
                    Logs.warning("incorrect skywars item! (#6) config value: `" + items.get(i) + "`");
                    continue;
                }
                item.addUnsafeEnchantment(enchantment, level);
            }

            result.add(item);
        }

        while (items.size() < 27) {
            result.add(new ItemStack(Material.AIR));
        }

        Collections.shuffle(items);

        return result;
    }

    @Override
    protected void onCountdownEnd() {
        List<Player> playersToStartGameWith = getPlayers();

        if (playersToStartGameWith.size() < getMinimumPlayersAmount()) {
            scheduleStopMiniGameAndSendReason("Za mało graczy! Anulowanie startu minigry...", "&dStart anulowany", "&eZa mało graczy");
            return;
        }

        lobby = false;

        broadcastMessage("&dMinigra rozpoczęta. &ePowodzenia!");
        showTitle("&dMinigra rozpoczęta", "&ePowodzenia!");

        timer = new BossBarTimer(plugin, totalMiniGameTime, "&eKoniec minigry");
        timer.startTimer();

        teleportPlayersToIslands(playersToStartGameWith);
    }

    private void teleportPlayersToIslands(@NotNull List<Player> players) {
        Collections.shuffle(spawns);
        int i = 0;
        for (Player player : players) {
            player.setBedSpawnLocation(spectatorSpawn, true);
            timer.showBossBarToPlayer(player);
            if (i >= spawns.size()) {
                changePlayerStatus(player, PlayerStatus.SPECTATOR);
                player.teleportAsync(spectatorSpawn);
                MiniGamesUtils.healPlayer(player, GameMode.ADVENTURE);
                broadcastMessage("Gracz " + player.getName() + " obserwuję minigrę.");
            } else {
                changePlayerStatus(player, PlayerStatus.IN_MINI_GAME);
                player.teleportAsync(spawns.get(i));
                MiniGamesUtils.healPlayer(player, GameMode.SURVIVAL);

                WorldBorder border = Bukkit.createWorldBorder();
                border.setCenter(spawns.get(i));
                border.setSize(mapRadius);
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
        tickPlayers();
        teleportSpectatorsBackIfTooFarAway();

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
        }
    }

    private void tickPlayers() {
        getPlayersInMiniGame().forEach(player -> {
            if (miniGameTime == totalMiniGameTime - shrinkBorderBeforeEnd) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, shrinkBorderBeforeEnd * 20, 255, false, false, true));
            }
        });
    }

    private void teleportSpectatorsBackIfTooFarAway() {
        getPlayers().stream()
                .filter(player -> distance(player.getLocation(), spectatorSpawn) > mapRadius)
                .filter(player -> getPlayerStatus(player) == PlayerStatus.SPECTATOR)
                .forEach(player -> {
                    player.teleportAsync(spectatorSpawn);
                    player.sendActionBar(Utils.getComponentByString("&cOdleciałeś za daleko"));
                });
    }

    private double distance(@NotNull Location l1, @NotNull Location l2) {
        return Math.max(Math.abs(l1.getX() - l2.getX()), Math.abs(l1.getZ() - l2.getZ()));
    }

    @Override
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMinimumPlayersAmount() {
        return 2;
    }

    @Override
    public @Range(from = 2, to = Integer.MAX_VALUE) int getMaximumPlayersAmount() {
        return 15;
    }

    @Override
    public @NotNull GameMode getSpectatorGameMode() {
        return GameMode.ADVENTURE;
    }
}
