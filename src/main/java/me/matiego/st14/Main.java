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
import me.matiego.st14.utils.Logs;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.concurrent.*;

public final class Main extends JavaPlugin implements Listener {

    public Main() {
        instance = this;
    }
    @Getter private static Main instance;
    private MySQL mySQL;
    @Getter private OfflinePlayers offlinePlayers;
    @Getter private Economy economy;
    @Getter private IncognitoManager incognitoManager;
    private CommandManager commandManager;
    @Getter private AccountsManager accountsManager;
    @Getter private ChatMinecraft chatMinecraft;
    @Getter private AfkManager afkManager;
    @Getter private TimeManager timeManager;

    @Getter (onMethod_ = {@Nullable}) private JDA jda;
    private ExecutorService callbackThreadPool;


    @Override
    public void onEnable() {
        //Save config file
        saveDefaultConfig();

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

        //Economy
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null) {
            Logs.error("Vault plugin not found!");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        economy = new Economy(this);
        Bukkit.getServicesManager().register(net.milkbowl.vault.economy.Economy.class, getEconomy(), vault, ServicePriority.High);

        //Register managers
        incognitoManager = new IncognitoManager(this);
        offlinePlayers = new OfflinePlayers(this);
        accountsManager = new AccountsManager(this);
        chatMinecraft = new ChatMinecraft(this);
        afkManager = new AfkManager(this);
        timeManager = new TimeManager(this);

        //Register commands
        commandManager = new CommandManager(Arrays.asList(
                new IncognitoCommand(this),
                new AccountsCommand(this),
                //Minecraft commands
                new St14Command(),
                //Discord commands
                new PingCommand()
        ));

        //Register listeners
        Bukkit.getPluginManager().registerEvents(new PlayerListener(this), this);
        final ServerListener serverListener = new ServerListener(this);
        Bukkit.getPluginManager().registerEvents(serverListener, this);
        Bukkit.getMessenger().registerIncomingPluginChannel(this, "minecraft:brand", serverListener);
        Bukkit.getPluginManager().registerEvents(new AfkListener(this), this);
        Bukkit.getPluginManager().registerEvents(commandManager, this);
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
                    .setToken(getConfig().getString("bot-token", ""))
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
        } catch (Exception e) {
            Logs.error("An error occurred while enabling the Discord bot." + (e instanceof InvalidTokenException ? " Is the provided bot token correct?" : ""), e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        Logs.info("Plugin enabled successfully!");
    }

    @EventHandler
    public void onReady(@NotNull ServerLoadEvent event) {
        if (event.getType() != ServerLoadEvent.LoadType.STARTUP) return;
        commandManager.setEnabled(true);
        getAfkManager().start();
    }

    @Override
    public void onDisable() {
        //TODO: Plugin shutdown logic
    }

    public @NotNull Connection getConnection() throws SQLException {
        return mySQL.getConnection();
    }
}
