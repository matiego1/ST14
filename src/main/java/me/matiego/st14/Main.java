package me.matiego.st14;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.neovisionaries.ws.client.DualStackMode;
import com.neovisionaries.ws.client.WebSocketFactory;
import lombok.Getter;
import me.matiego.st14.commands.AccountsCommand;
import me.matiego.st14.commands.IncognitoCommand;
import me.matiego.st14.commands.PingCommand;
import me.matiego.st14.commands.St14Command;
import me.matiego.st14.listeners.AfkListener;
import me.matiego.st14.listeners.DiscordListener;
import me.matiego.st14.listeners.PlayerListener;
import me.matiego.st14.listeners.ServerListener;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.GUI;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.*;

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
    private CommandManager commandManager;

    @Getter (onMethod_ = {@Nullable}) private JDA jda;
    private ExecutorService callbackThreadPool;


    @Override
    public void onEnable() {
        instance = this;
        long time = Utils.now();
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
        economy = new Economy(this);
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, getEconomy(), vault, ServicePriority.High);

        //Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        final ServerListener serverListener = new ServerListener(this);
        Bukkit.getPluginManager().registerEvents(serverListener, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", serverListener);
        Bukkit.getPluginManager().registerEvents(new AfkListener(this), this);
        Bukkit.getPluginManager().registerEvents(this, this);

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
                            new DiscordListener()
                    )
                    .build();
            jda.awaitReady();
        } catch (Exception e) {
            Logs.error("An error occurred while enabling the Discord bot." + (e instanceof InvalidTokenException ? " Is the provided bot token correct?" : ""), e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        //register commands
        commandManager = new CommandManager(Arrays.asList(
                new IncognitoCommand(this),
                new AccountsCommand(this),
                //Minecraft commands
                new St14Command(),
                //Discord commands
                new PingCommand()
        ));
        Bukkit.getPluginManager().registerEvents(commandManager, this);

        Logs.info("Plugin enabled! Took " + (Utils.now() - time) + " ms.");
    }

    @EventHandler
    public void onServerLoad(@NotNull ServerLoadEvent event) {
        if (event.getType() != ServerLoadEvent.LoadType.STARTUP) return;
        commandManager.setEnabled(true);
        getAfkManager().start();
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
        AfkManager manager = getAfkManager();
        if (manager != null) manager.stop();
        //unregister all events
        HandlerList.unregisterAll((Plugin) this);
        //disable Discord bot
        if (jda != null) {
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
}
