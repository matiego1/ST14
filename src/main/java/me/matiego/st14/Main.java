package me.matiego.st14;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.WebSocketFactory;
import lombok.Getter;
import me.matiego.st14.commands.*;
import me.matiego.st14.commands.discord.*;
import me.matiego.st14.commands.minecraft.*;
import me.matiego.st14.listeners.*;
import me.matiego.st14.managers.*;
import me.matiego.st14.rewards.RewardForPlaying;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public final class Main extends JavaPlugin implements Listener {
    @Getter private static Main instance;
    private MySQL mySQL;
    @Getter private OfflinePlayersManager offlinePlayersManager;
    @Getter private EconomyManager economyManager;
    @Getter private IncognitoManager incognitoManager;
    @Getter private AccountsManager accountsManager;
    @Getter private ChatMinecraftManager chatMinecraftManager;
    @Getter private AfkManager afkManager;
    @Getter private TimeManager timeManager;
    @Getter private PremiumManager premiumManager;
    @Getter private TeleportsManager teleportsManager;
    @Getter private CommandManager commandManager;
    @Getter private BackpackManager backpackManager;
    @Getter private RewardsManager rewardsManager;
    @Getter private AntyLogoutManager antyLogoutManager;
    @Getter private MiniGamesManager miniGamesManager;
    @Getter private BanknoteManager banknoteManager;
    @Getter private WorldsLastLocationManager worldsLastLocationManager;
    @Getter private DynmapManager dynmapManager;
    @Getter private ListenersManager listenersManager;
    @Getter private BansManager bansManager;
    @Getter private HomeManager homeManager;
    @Getter private NonPremiumManager nonPremiumManager;
    private TabListManager tabListManager;
    private ChatReportsManager chatReportsManager;
    private DidYouKnowManager didYouKnowManager;

    @Getter private TellCommand tellCommand;
    @Getter private TpaCommand tpaCommand;
    @Getter private SuicideCommand suicideCommand;
    @Getter private IncognitoCommand incognitoCommand;

    @Getter private PlayerBedEnterListener playerBedEnterListener;
    @Getter private PlayerQuitListener playerQuitListener;
    @Getter private PlayerMoveListener playerMoveListener;
    @Getter private GraveCreateListener graveCreateListener;
    @Getter private EntityDamageByEntityListener entityDamageByEntityListener;

    private JDA jda;
    private boolean isJdaEnabled = false;
    private ExecutorService callbackThreadPool;

    @Override
    public void onEnable() {
        instance = this;
        long time = Utils.now();
        //Check Bukkit version
        if (!Bukkit.getBukkitVersion().equals("1.20.1-R0.1-SNAPSHOT")) {
            Logs.error("Detected incompatible Bukkit version: " + Bukkit.getBukkitVersion() + ".");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        //Check if server is PaperMC
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
        } catch (ClassNotFoundException e) {
            Logs.error("This plugin is compatible only with PaperMC!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        //Save config file
        try {
            saveDefaultConfig();
        } catch (IllegalArgumentException e) {
            Logs.error("An error occurred while loading the config file", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        //Vault plugin
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null) {
            Logs.error("Vault plugin not found!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        //Open the MySQL connection
        Logs.info("Opening the MySQL connection...");
        String username = getConfig().getString("database.username", "");
        String password = getConfig().getString("database.password", "");
        try {
            mySQL = new MySQL("jdbc:mysql://" + getConfig().getString("database.host") + ":" + getConfig().getString("database.port") + "/" + getConfig().getString("database.database") + "?user=" + username + "&password=" + password, username, password);
        } catch (Exception e) {
            Logs.error("An error occurred while opening the MySQL connection.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        if (!mySQL.createTables()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        //Register managers
        incognitoManager = new IncognitoManager(this);
        offlinePlayersManager = new OfflinePlayersManager(this);
        accountsManager = new AccountsManager(this);
        chatMinecraftManager = new ChatMinecraftManager(this);
        afkManager = new AfkManager(this);
        timeManager = new TimeManager(this);
        economyManager = new EconomyManager(this, true);
        premiumManager = new PremiumManager(this);
        tabListManager = new TabListManager(this);
        teleportsManager = new TeleportsManager(this);
        rewardsManager = new RewardsManager(this);
        backpackManager = new BackpackManager(this);
        chatReportsManager = new ChatReportsManager();
        antyLogoutManager = new AntyLogoutManager(this);
        miniGamesManager = new MiniGamesManager(this);
        listenersManager = new ListenersManager(this);
        didYouKnowManager = new DidYouKnowManager(this);
        banknoteManager = new BanknoteManager(this);
        worldsLastLocationManager = new WorldsLastLocationManager(this);
        dynmapManager = new DynmapManager(this);
        bansManager = new BansManager(this);
        homeManager = new HomeManager(this);
        nonPremiumManager = new NonPremiumManager(this);

        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, getEconomyManager(), vault, ServicePriority.High);

        //Register listeners
        playerBedEnterListener = new PlayerBedEnterListener();
        playerQuitListener = new PlayerQuitListener(this);
        playerMoveListener = new PlayerMoveListener(this);
        graveCreateListener = new GraveCreateListener();
        entityDamageByEntityListener = new EntityDamageByEntityListener(this);
        listenersManager.registerListeners(
                new AsyncChatListener(this),
                new AsyncPlayerPreLoginListener(this),
                new BlockBreakListener(this),
                new BlockFormListener(),
                new BlockPistonExtendListener(),
                new BlockPlaceListener(),
                new CraftItemListener(this),
                new EntityChangeBlockListener(),
                entityDamageByEntityListener,
                new EntityDeathListener(this),
                new EntityExplodeListener(this),
                new EntityPortalListener(this),
                new EntityToggleGlideListener(this),
                new FoodLevelChangeListener(this),
                graveCreateListener,
                new GS4QueryListener(this),
                new InventoryCloseListener(this),
                new PlayerAdvancementCriterionGrantListener(),
                new PlayerAdvancementDoneListener(),
                playerBedEnterListener,
                new PlayerBedLeaveListener(this),
                new PlayerBucketEmptyListener(),
                new PlayerChangedWorldListener(this),
                new PlayerCommandPreprocessListener(this),
                new PlayerCommandSendListener(this),
                new PlayerDeathListener(this),
                new PlayerInteractListener(this),
                new PlayerItemFrameChangeListener(this),
                new PlayerJoinListener(this),
                new PlayerLaunchProjectileListener(this),
                new PlayerLoginEventListener(this),
                playerMoveListener,
                new PlayerPortalListener(this),
                playerQuitListener,
                new PlayerResourcePackStatusListener(),
                new PlayerRespawnListener(this),
                new PlayerTeleportListener(),
                new ServerCommandListener(),
                new ServerListPingListener(this),
                new SignChangeListener(this),
                new StructureGrowListener()

        );
        listenersManager.registerListener("minecraft:brand", new PluginMessageReceivedListener(this));
        getDynmapManager().registerListeners();

        //Counting plugin
        if (Bukkit.getPluginManager().getPlugin("Counting") != null) {
            listenersManager.registerListener(new CountingMessageSendListener(this));
        }

        //Enable the Discord bot
        Logs.info("Enabling the Discord bot...");
        RestAction.setDefaultFailure(throwable -> Logs.error("An error occurred!", throwable));
        if (jda != null) {
            try {
                jda.shutdownNow();
                callbackThreadPool.shutdownNow();
            } catch (Exception e) {
                Logs.error("An error occurred while shutting down the Discord bot.", e);
            }
            jda = null;
            callbackThreadPool = null;
            Logs.warning("Restart the server instead of reloading it. It may break this plugin.");
        }
        try {
            callbackThreadPool = new ForkJoinPool(Runtime.getRuntime().availableProcessors(), pool -> {
                ForkJoinWorkerThread worker = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                worker.setName("Counting - JDA Callback " + worker.getPoolIndex());
                return worker;
            }, null, true);
            jda = JDABuilder.create(DiscordUtils.getIntents())
                    .setToken(getConfig().getString("discord.bot-token", ""))
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setCallbackPool(callbackThreadPool, false)
                    .setGatewayPool(Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("Counting - JDA Gateway").build()), true)
                    .setRateLimitPool(new ScheduledThreadPoolExecutor(5, new ThreadFactoryBuilder().setNameFormat("Counting - JDA Rate Limit").build()), true)
                    .setWebsocketFactory(new WebSocketFactory().setDualStackMode(DualStackMode.IPV4_ONLY))
                    .setHttpClient(DiscordUtils.getHttpClient())
                    .setAutoReconnect(true)
                    .setBulkDeleteSplittingEnabled(false)
                    .setEnableShutdownHook(false)
                    .setContextEnabled(false)
                    .disableCache(DiscordUtils.getDisabledCacheFlag())
                    .setActivity(Activity.playing("Serwer ST14"))
                    .addEventListeners(
                            new DiscordMessageReceivedListener(),
                            getChatMinecraftManager()
                    )
                    .build();
            jda.awaitReady();
            isJdaEnabled = true;
            onDiscordBotEnable();
        } catch (Exception e) {
            Logs.error("An error occurred while enabling the Discord bot." + (e instanceof InvalidTokenException ? " Is the provided bot token correct?" : ""), e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Logs.info("Plugin enabled! Took " + (Utils.now() - time) + " ms.");
    }

    private void onDiscordBotEnable() {
        //register commands
        tellCommand = new TellCommand(this);
        tpaCommand = new TpaCommand(this);
        suicideCommand = new SuicideCommand(this);
        incognitoCommand = new IncognitoCommand(this);
        commandManager = new CommandManager(Objects.requireNonNull(getJda()), Arrays.asList(
                new AccountsCommand(this),
                new BanCommand(this),
                new CoordinatesCommand(this),
                new DifficultyCommand(this),
                new EconomyCommand(this),
                new GameModeCommand(this),
                incognitoCommand,
                new NonPremiumCommand(this),
                new PremiumCommand(this),
                new SayCommand(this),
                new SpawnCommand(this),
                new StopCommand(this),
                new TimeCommand(this),
                new VersionCommand(this),
                //Minecraft commands
                new BackpackCommand(this),
                new HomeCommand(this),
                new McreloadCommand(this),
                new MiniGameCommand(this),
                new ReplyCommand(this),
                new St14Command(this),
                suicideCommand,
                tellCommand,
                tpaCommand,
                new WorldsCommand(this),
                //Discord commands
                new AllPlayersCommand(this),
                new FeedbackCommand(),
                new ListCommand(this),
                new PingCommand(),
                new VerifyCommand(this)
        ));
        listenersManager.registerListener(commandManager);
        jda.addEventListener(commandManager);

        //start managers
        chatReportsManager.start();
        commandManager.setEnabled(true);
        getAfkManager().start();
        tabListManager.start();
        getRewardsManager().getRewardForPlaying().start();
        getAntyLogoutManager().start();
        getChatMinecraftManager().unblock();
        didYouKnowManager.start();
        Utils.registerRecipes();
        Utils.kickPlayersAtMidnightTask();

        Utils.async(() -> {
            Utils.deleteOldLogFiles();

            UpdatesManager updates = new UpdatesManager();
            HashMap<Plugin, UpdatesManager.Response> versions = new UpdatesManager().checkSpigotMc(
                    Arrays.stream(Bukkit.getPluginManager().getPlugins())
                            .filter(Plugin::isEnabled)
                            .filter(p -> !p.equals(this))
                            .collect(Collectors.toList())
            );
            Iterator<Map.Entry<Plugin, UpdatesManager.Response>> it = versions.entrySet().iterator();
            while (it.hasNext()) {
                UpdatesManager.Response response = it.next().getValue();
                if (response == UpdatesManager.Response.UNKNOWN_ID) it.remove();
                else if (response == UpdatesManager.Response.UP_TO_DATE) it.remove();
                else if (response == UpdatesManager.Response.NEWER) it.remove();
            }
            updates.log(versions);
        });
    }

    @Override
    public void onDisable() {
        long time = Utils.now();
        //disable commands
        if (commandManager != null) commandManager.setEnabled(false);
        //stop minigame
        if (miniGamesManager != null) {
            miniGamesManager.stopMiniGame();
        }
        //close all plugin's inventories
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GUI) player.closeInventory();
        }
        //disable managers
        if (antyLogoutManager != null) antyLogoutManager.stop();
        if (afkManager != null) afkManager.stop();
        if (tabListManager != null) tabListManager.stop();
        if (chatMinecraftManager != null) chatMinecraftManager.block();
        if (teleportsManager != null) teleportsManager.cancelAll();
        if (economyManager != null) economyManager.setEnabled(false);
        if (chatReportsManager != null) chatReportsManager.stop();
        if (didYouKnowManager != null) didYouKnowManager.stop();
        if (rewardsManager != null) {
            RewardForPlaying rewardForPlaying = rewardsManager.getRewardForPlaying();
            if (rewardForPlaying != null) rewardForPlaying.stop();
        }
        //unregister all events
        HandlerList.unregisterAll((Plugin) this);
        //disable Discord bot
        if (jda != null) {
            Logs.infoWithBlock("Shutting down Discord bot...");

            isJdaEnabled = false;
            jda.getEventManager().getRegisteredListeners().forEach(listener -> jda.getEventManager().unregister(listener));

            try {
                disableDiscordBot();
            } catch (Exception e) {
                Logs.error("An error occurred while shutting down Discord bot.", e);
            }
        }
        if (callbackThreadPool != null) {
            callbackThreadPool.shutdownNow();
            callbackThreadPool = null;
        }
        //end all tasks
        Bukkit.getScheduler().cancelTasks(this);
        for (BukkitWorker task : Bukkit.getScheduler().getActiveWorkers()) {
            if (task.getOwner().equals(this)) {
                Logs.error("Task with id " + task.getTaskId() + " has not been canceled. Interrupting...");
                try {
                    task.getThread().interrupt();
                } catch (Exception ignored) {}
            }
        }
        //close MySQL connection
        if (mySQL != null) mySQL.close();

        Logs.info("Plugin disabled! Took " + (Utils.now() - time) + " ms.");
        instance = null;
    }

    private void disableDiscordBot() throws Exception {
        if (jda == null) return;
        jda.shutdown();
        if (jda.awaitShutdown(5, TimeUnit.SECONDS)) return;
        Logs.warning("Discord bot took too long to shut down, skipping.");
        jda.shutdownNow();
        if (jda.awaitShutdown(3, TimeUnit.SECONDS)) return;
        jda = null;
    }

    public @NotNull Connection getMySQLConnection() throws SQLException {
        return mySQL.getConnection();
    }

    public @Nullable JDA getJda() {
        return jda != null && isJdaEnabled ? jda : null;
    }
}
