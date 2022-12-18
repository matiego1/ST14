package me.matiego.st14.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefixes;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.Bukkit;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class PlayerListener implements Listener {

    private final Main plugin;
    public PlayerListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    @SuppressWarnings("MismatchedQueryAndUpdateOfCollection")
    private final HashMap<UUID, BukkitTask> disablingIncognito = new HashMap<>();

    @EventHandler (priority = EventPriority.LOWEST)
    public void onAsyncPlayerPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        UUID uuid = event.getUniqueId();
        //refresh offline players names
        if (event.getName().length() > 36) {
            disallow(event, "&cTwój nick jest za długi! Może mieć maksymalnie 36 znaków.");
            return;
        }
        plugin.getOfflinePlayers().refresh(uuid, event.getName());
        //check if Discord bot is online
        JDA jda = plugin.getJda();
        if (jda == null) {
            disallow(event, Prefixes.DISCORD + "&cBot na Discord jest offline! Nie możesz dołączyć do serwera.");
            return;
        }
        //check if player has linked account
        if (!plugin.getAccountsManager().isRequired(uuid)) return;
        UserSnowflake id = plugin.getAccountsManager().getUserByPlayer(uuid);
        if (id == null) {
            String code = plugin.getAccountsManager().getNewVerificationCode(uuid);
            disallow(event,
                    Prefixes.DISCORD + "\n" +
                    "Nie połączyłeś konta Discord z twoim kontem minecraft!\n\n" +
                    "Użyj komendy &9/accounts &bna Discord\n" +
                    "z kodem &9" + code + "&b\n\n" +
                    "&cUWAGA! &bKod będzie ważny tylko 5 minut."
            );
            return;
        }
        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) {
            Logs.warning("A guild id in the config file is not correct.");
            return;
        }
        Member member = guild.getMember(id);
        if (member == null) {
            disallow(event, Prefixes.DISCORD + "Wygląda na to, że nie ma cię na naszym serwerze Discord! Dołącz do niego, aby grać na tym serwerze.");
            return;
        }
        Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-id"));
        if (role == null) {
            Logs.warning("A role id in the config file is not correct.");
            return;
        }
        if (!member.getRoles().contains(role)) {
            disallow(event, Prefixes.DISCORD + "Twoje konto zostało rozłączone przed administratora. Dołącz ponownie, aby je połączyć.");
            plugin.getAccountsManager().unlink(uuid);
        }
    }
    private void disallow(@NotNull AsyncPlayerPreLoginEvent event, @NotNull String msg) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.getComponentByString(msg));
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
        //load player times
        plugin.getTimeManager().join(player);
        //incognito
        disablingIncognito.remove(player.getUniqueId());
        if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) {
            player.sendMessage(Utils.getComponentByString(Prefixes.INCOGNITO + "Jesteś incognito!"));
        }
        //join messages
        event.joinMessage(Utils.getComponentByString("&eGracz " + player.getName() + " dołączył do gry"));
        plugin.getChatMinecraft().sendJoinMessage(player);
    }

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        //save player times
        plugin.getTimeManager().quit(player);
        //quit message
        event.quitMessage(Utils.getComponentByString("&eGracz " + player.getName() + " opuścił grę"));
        plugin.getChatMinecraft().sendQuitMessage(player);
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
}
