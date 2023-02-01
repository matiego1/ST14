package me.matiego.st14;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.WebSocketFactory;
import lombok.Getter;
import me.matiego.st14.commands.*;
import me.matiego.st14.commands.discord.*;
import me.matiego.st14.commands.minecraft.*;
import me.matiego.st14.listeners.*;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.GUI;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
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

import java.awt.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public final class Main extends JavaPlugin implements Listener {
    @Getter private static Main instance;
    private MySQL mySQL;
    @Getter private OfflinePlayers offlinePlayers;
    @Getter private Economy economy;
    @Getter private IncognitoManager incognitoManager;
    @Getter private AccountsManager accountsManager;
    @Getter private ChatMinecraft chatMinecraft;
    @Getter private AfkManager afkManager;
    @Getter private TimeManager timeManager;
    @Getter private PremiumManager premiumManager;
    @Getter private TeleportsManager teleportsManager;
    @Getter private CommandManager commandManager;
    @Getter private BackpackManager backpackManager;
    @Getter private RewardsManager rewardsManager;
    @Getter private AntyLogoutManager antyLogoutManager;
    private TabListManager tabListManager;
    private ChatReportsManager chatReportsManager;

    @Getter private TellCommand tellCommand;
    @Getter private TpaCommand tpaCommand;
    @Getter private SuicideCommand suicideCommand;
    @Getter private IncognitoCommand incognitoCommand;

    @Getter private GravesListener gravesListener;

    private JDA jda;
    private boolean isJdaEnabled = false;
    private ExecutorService callbackThreadPool;

    @Override
    public void onEnable() {
        instance = this;
        long time = Utils.now();
        //Check Bukkit version
        if (!Bukkit.getBukkitVersion().equals("1.19.3-R0.1-SNAPSHOT")) {
            Logs.error("Detected incompatible Bukkit version: " + Bukkit.getBukkitVersion() + ".");
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
        offlinePlayers = new OfflinePlayers(this);
        accountsManager = new AccountsManager(this);
        chatMinecraft = new ChatMinecraft(this);
        afkManager = new AfkManager(this);
        timeManager = new TimeManager(this);
        economy = new Economy(this, true);
        premiumManager = new PremiumManager(this);
        tabListManager = new TabListManager(this);
        teleportsManager = new TeleportsManager();
        rewardsManager = new RewardsManager(this);
        backpackManager = new BackpackManager(this);
        chatReportsManager = new ChatReportsManager();
        antyLogoutManager = new AntyLogoutManager(this);

        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, getEconomy(), vault, ServicePriority.High);

        //Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        final ServerListener serverListener = new ServerListener(this);
        Bukkit.getPluginManager().registerEvents(serverListener, this);
        Bukkit.getPluginManager().registerEvents(new AfkListener(this), this);
        Bukkit.getPluginManager().registerEvents(new EntityListener(), this);
        Bukkit.getPluginManager().registerEvents(getTeleportsManager(), this);
        gravesListener = new GravesListener();
        Bukkit.getPluginManager().registerEvents(getGravesListener(), this);

        Bukkit.getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", serverListener);
        Bukkit.getPluginManager().registerEvents(this, this);

        //Counting plugin
        if (Bukkit.getPluginManager().getPlugin("Counting") != null) {
            Bukkit.getPluginManager().registerEvents(new CountingListener(this), this);
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
                            new DiscordListener(),
                            getChatMinecraft()
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
        EmbedBuilder eb = new EmbedBuilder();
        eb.setDescription("Bot has been enabled!");
        eb.setTimestamp(Instant.now());
        eb.setColor(Color.GREEN);
        Logs.discord(eb.build());

        //register commands
        tellCommand = new TellCommand();
        tpaCommand = new TpaCommand(this);
        suicideCommand = new SuicideCommand();
        incognitoCommand = new IncognitoCommand(this);
        commandManager = new CommandManager(Arrays.asList(
                incognitoCommand,
                new AccountsCommand(this),
                new VersionCommand(),
                new TimeCommand(this),
                new EconomyCommand(this),
                //Minecraft commands
                new SayCommand(this),
                new St14Command(),
                new DifficultyCommand(),
                new GameModeCommand(),
                new ReplyCommand(),
                new McreloadCommand(),
                new WorldsCommand(this),
                new StopCommand(),
                new BackpackCommand(this),
                tellCommand,
                tpaCommand,
                suicideCommand,
                //Discord commands
                new PingCommand(),
                new ListCommand(),
                new FeedbackCommand(),
                new AllPlayersCommand(),
                new VerifyCommand(this)
        ));
        Bukkit.getPluginManager().registerEvents(commandManager, this);
        jda.addEventListener(commandManager);

        chatReportsManager.start();
        commandManager.setEnabled(true);
        getAfkManager().start();
        tabListManager.start();
        getRewardsManager().start();
        getAntyLogoutManager().start();
        getChatMinecraft().unblock();
        Utils.registerRecipes();
        Utils.kickPlayersAtMidnightTask();

        Utils.async(() -> {
            Utils.deleteOldLogFiles();

            Updates updates = new Updates();
            HashMap<Plugin, Updates.Response> versions = new Updates().checkSpigotMc(
                    Arrays.stream(Bukkit.getPluginManager().getPlugins())
                            .filter(Plugin::isEnabled)
                            .filter(p -> !p.equals(this))
                            .collect(Collectors.toList())
            );
            Iterator<Map.Entry<Plugin, Updates.Response>> it = versions.entrySet().iterator();
            while (it.hasNext()) {
                Updates.Response response = it.next().getValue();
                if (response == Updates.Response.UNKNOWN_ID) it.remove();
                else if (response == Updates.Response.UP_TO_DATE) it.remove();
                else if (response == Updates.Response.NEWER) it.remove();
            }
            updates.log(versions);
            updates.logDiscord(versions);
        });
    }

    @Override
    public void onDisable() {
        long time = Utils.now();
        //disable commands
        if (commandManager != null) commandManager.setEnabled(false);
        //close all plugin's inventories
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof GUI) player.closeInventory();
        }
        //disable managers
        if (antyLogoutManager != null) antyLogoutManager.stop();
        if (afkManager != null) afkManager.stop();
        if (tabListManager != null) tabListManager.stop();
        if (rewardsManager != null) rewardsManager.stop();
        if (chatMinecraft != null) chatMinecraft.block();
        if (teleportsManager != null) teleportsManager.cancelAll();
        if (economy != null) economy.setEnabled(false);
        if (chatReportsManager != null) chatReportsManager.stop();
        //unregister all events
        HandlerList.unregisterAll((Plugin) this);
        //disable Discord bot
        if (jda != null) {
            EmbedBuilder eb = new EmbedBuilder();
            eb.setDescription("Bot has been disabled!");
            eb.setTimestamp(Instant.now());
            eb.setColor(Color.RED);
            TextChannel chn = DiscordUtils.getConsoleChannel();
            if (chn != null) {
                chn.sendMessageEmbeds(eb.build()).complete();
            }

            isJdaEnabled = false;
            jda.getEventManager().getRegisteredListeners().forEach(listener -> jda.getEventManager().unregister(listener));
            CompletableFuture<Void> shutdownTask = new CompletableFuture<>();
            jda.addEventListener(new ListenerAdapter() {
                @Override
                public void onShutdown(@NotNull ShutdownEvent event) {
                    shutdownTask.complete(null);
                }
            });
            jda.shutdown();
            try {
                shutdownTask.get(5, TimeUnit.SECONDS);
                Logs.info("Successfully shut down the Discord bot.");
            } catch (Exception e) {
                Logs.warning("Discord bot took too long to shut down, skipping. Ignore any errors from this point.");
            }
            jda = null;
            if (callbackThreadPool != null) callbackThreadPool.shutdownNow();
            callbackThreadPool = null;
        }
        //end all tasks
        Bukkit.getScheduler().cancelTasks(this);
        for (BukkitWorker task : Bukkit.getScheduler().getActiveWorkers()) {
            if (task.getOwner().equals(this)) {
                Logs.error("Task with id " + task.getTaskId() + " has not been canceled. Interrupting...");
                try {
                    task.getThread().interrupt();
                }catch (Exception ignored) {}
            }
        }
        //close MySQL connection
        if (mySQL != null) mySQL.close();

        Logs.info("Plugin disabled! Took " + (Utils.now() - time) + " ms.");
        instance = null;
    }

    public @NotNull Connection getConnection() throws SQLException {
        return mySQL.getConnection();
    }

    public @Nullable JDA getJda() {
        return jda != null && isJdaEnabled ? jda : null;
    }
}
