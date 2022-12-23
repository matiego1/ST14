package me.matiego.st14.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.PremiumManager;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefixes;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class PlayerListener implements Listener {

    private final Main plugin;
    public PlayerListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final HashMap<UUID, BukkitTask> disablingIncognito = new HashMap<>();
    private final HashMap<UUID, Player> sleepingPlayers = new HashMap<>();
    private final HashMap<UUID, BossBar> positionBossBars = new HashMap<>();

    @EventHandler (priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        AsyncPlayerPreLoginEvent.Result result = event.getLoginResult();
        if (result == AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            String msg = check(event, uuid);
            if (msg == null) return;
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.getComponentByString(msg));
            return;
        }
        if (result == AsyncPlayerPreLoginEvent.Result.KICK_FULL) {
            String msg = check(event, uuid);
            if (msg != null) return;
            PremiumManager manager = plugin.getPremiumManager();
            if (manager.isPremium(uuid) && manager.makeSpaceForPlayer(uuid)) {
                event.allow();
            }
        }
    }
    private @Nullable String check(@NotNull AsyncPlayerPreLoginEvent event, @NotNull UUID uuid) {
        //refresh offline players names
        if (event.getName().length() > 36) {
            return "&cTwój nick jest za długi! Może mieć maksymalnie 36 znaków.";
        }
        plugin.getOfflinePlayers().refresh(uuid, event.getName());
        //check if Discord bot is online
        JDA jda = plugin.getJda();
        if (jda == null) {
            return Prefixes.DISCORD + "&cBot na Discord jest offline! Nie możesz dołączyć do serwera.";
        }
        //check if player has linked account
        if (plugin.getAccountsManager().isRequired(uuid)) {
            UserSnowflake id = plugin.getAccountsManager().getUserByPlayer(uuid);
            if (id == null) {
                String code = plugin.getAccountsManager().getNewVerificationCode(uuid);
                return Prefixes.DISCORD + "\n" +
                        "Nie połączyłeś konta Discord z twoim kontem minecraft!\n\n" +
                        "Użyj komendy &9/accounts &bna Discord\n" +
                        "z kodem &9" + code + "&b\n\n" +
                        "&cUWAGA! &bKod będzie ważny tylko 5 minut.";
            }
            Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
            if (guild == null) {
                Logs.warning("A guild id in the config file is not correct.");
                return null;
            }
            Member member = guild.retrieveMember(id).complete();
            if (member == null) {
                return Prefixes.DISCORD + "Wygląda na to, że nie ma cię na naszym serwerze Discord! Dołącz do niego, aby grać na tym serwerze.";
            }
            Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-id"));
            if (role == null) {
                Logs.warning("A role id in the config file is not correct.");
                return null;
            }
            if (!member.getRoles().contains(role)) {
                plugin.getAccountsManager().unlink(uuid);
                return Prefixes.DISCORD + "Twoje konto zostało rozłączone przed administratora. Dołącz ponownie, aby je połączyć.";
            }
        }
        return null;
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLoginMonitor(@NotNull AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        IncognitoManager manager = plugin.getIncognitoManager();
        manager.getIncognitoPlayers().stream()
                .filter(uuid -> !uuid.equals(event.getUniqueId()))
                .filter(manager::isKickingEnabled)
                .filter(uuid -> !manager.getTrustedPlayers(uuid).contains(event.getUniqueId()))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> Utils.sync(() -> player.kick(Utils.getComponentByString(Prefixes.INCOGNITO + "Gracz " + event.getName() + " dołącza do gry. Zostałeś wyrzucony, ponieważ masz włączony tryb incognito."))));
    }


    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        //load player times
        if (!plugin.getTimeManager().join(player)) {
            player.kick(Utils.getComponentByString("&cNapotkano niespodziewany błąd przy ładowaniu twoich czasów. Spróbuj ponownie."));
            return;
        }
        //incognito
        disablingIncognito.remove(uuid);
        if (plugin.getIncognitoManager().isIncognito(uuid)) {
            player.sendMessage(Utils.getComponentByString(Prefixes.INCOGNITO + "Jesteś incognito!"));
        }
        //premium
        long time = plugin.getPremiumManager().getRemainingTime(uuid);
        if (time > 0) {
            player.sendMessage(Utils.getComponentByString(Prefixes.PREMIUM + "Jesteś graczem premium! Twój status premium wygaśnie za &6" + Utils.parseMillisToString(time, false) + "&d."));
        }
        //join messages
        event.joinMessage(Utils.getComponentByString("&eGracz " + player.getName() + " dołączył do gry"));
        plugin.getChatMinecraft().sendJoinMessage(player);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getTellCommand().removeReply(player.getUniqueId());
        positionBossBars.remove(player.getUniqueId());
        plugin.getAfkManager().move(player);
        //quit message
        event.quitMessage(Utils.getComponentByString("&eGracz " + player.getName() + " opuścił grę"));
        plugin.getChatMinecraft().sendQuitMessage(player);
        //save player times
        plugin.getTimeManager().quit(player);
        //incognito
        disablingIncognito.put(
                player.getUniqueId(),
                Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> {
                            plugin.getIncognitoManager().setIncognito(player.getUniqueId(), false);
                            disablingIncognito.remove(player.getUniqueId());
                        },
                        1200
                )
        );
    }

    @EventHandler
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        //TODO: send death message
    }

    @EventHandler
    public void onPlayerChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();
        Block block = player.getLocation().getBlock();
        final Component message = event.message().replaceText(
                TextReplacementConfig
                        .builder()
                        .matchLiteral("[here]")
                        .replacement("[" + Utils.getWorldName(player.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]")
                        .once()
                        .build()
        );
        event.renderer((p1, p2, p3, p4) ->
                Utils.getComponentByString("&a[" + Utils.getWorldPrefix(player.getWorld()) + "] &f")
                .append(player.displayName())
                .append(Utils.getComponentByString("&f: "))
                .append(message)
        );
        plugin.getChatMinecraft().sendChatMessage(PlainTextComponentSerializer.plainText().serialize(message), player);
    }

    @EventHandler
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.SPECTATE) {
            if (!event.getFrom().getWorld().equals(event.getTo().getWorld())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(Utils.getComponentByString("&cNie możesz się teleportować do innych światów!"));
            }
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerCommandPreprocess(@NotNull PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String command = event.getMessage().substring(1).toLowerCase();
        if (command.isBlank()) return;
        if (command.charAt(0) == '/') command = command.substring(1);

        if (player.isOp()) {
            Logs.info("[" + player.getName() + "]: /" + command);
            return;
        }

        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        if (allowedCommands.isEmpty()) {
            Logs.info("[" + player.getName() + "]: /" + command);
            return;
        }

        for (String allowedCommand : allowedCommands) {
            if (command.startsWith(allowedCommand.toLowerCase())) {
                Logs.info("[" + player.getName() + "]: /" + command);
                return;
            }
        }
        event.setCancelled(true);
        player.sendMessage(Utils.getComponentByString("&cNie masz uprawnień, aby użyć tej komendy!"));
    }

    @EventHandler
    public void onPlayerCommandSent(@NotNull PlayerCommandSendEvent event) {
        if (event.getPlayer().isOp()) return;
        List<String> allowedCommands = plugin.getConfig().getStringList("allowed-commands");
        if (allowedCommands.isEmpty()) return;

        Iterator<String> iterator = event.getCommands().iterator();
        while (iterator.hasNext()) {
            String command = iterator.next();
            if (command.isBlank()) return;
            if (command.charAt(0) == '/') command = command.substring(1);
            if (!allowedCommands.contains(command)) iterator.remove();
        }
    }

    @EventHandler (ignoreCancelled = true)
    public void onPlayerBedEnter(@NotNull PlayerBedEnterEvent event) {
        if (event.getBedEnterResult() != PlayerBedEnterEvent.BedEnterResult.OK) return;
        Player player = event.getPlayer();
        UUID uuid = event.getBed().getWorld().getUID();
        if (sleepingPlayers.get(uuid) != null) {
            event.setCancelled(true);
            player.sendMessage(Utils.getComponentByString("&cW tym świecie już ktoś spi"));
            return;
        }
        sleepingPlayers.put(uuid, player);
    }

    @EventHandler (priority = EventPriority.MONITOR)
    public void onPlayerBedLeave(@NotNull PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world = event.getBed().getWorld();
        sleepingPlayers.remove(world.getUID());
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .forEach(p -> p.sendMessage(Utils.getComponentByString("&eGracz &6" + player.getName() + "&eposzedł spać. Słodkich snów!")));
        if (!plugin.getIncognitoManager().isIncognito(player.getUniqueId())) {
            plugin.getChatMinecraft().sendMessage("Przesypianie nocy", "[" + Utils.getWorldName(world) + "] Gracz **" + player + "** poszedł spać. Słodkich snów!");
        }
    }

    @EventHandler (ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (event.hasChangedBlock()) {
            Player player = event.getPlayer();
            if (player.isGliding()) {
                BossBar bar = positionBossBars.get(player.getUniqueId());
                if (bar == null) {
                    bar = BossBar.bossBar(Utils.getComponentByString(""), 1, BossBar.Color.BLUE, BossBar.Overlay.PROGRESS);
                    player.showBossBar(bar);
                    positionBossBars.put(player.getUniqueId(), bar);
                }
                Block block = player.getLocation().getBlock();
                final BossBar finalBar = bar;
                Utils.async(() -> finalBar.name(Utils.getComponentByString("&6X: &e" + block.getX() + " &6Y: &e" + block.getY() + " &6Z: &e" + block.getZ())));
            } else {
                BossBar bar = positionBossBars.remove(player.getUniqueId());
                if (bar != null) {
                    player.hideBossBar(bar);
                }
            }
        }
    }
}
