package me.matiego.st14.listeners;

import me.matiego.st14.IncognitoManager;
import me.matiego.st14.Main;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

public class AsyncPlayerPreLoginListener implements Listener {
    public AsyncPlayerPreLoginListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onAsyncPlayerPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        UUID uuid = event.getUniqueId();
        //check whitelist & bans
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        if (Bukkit.hasWhitelist() && Bukkit.getWhitelistedPlayers().stream()
                .map(OfflinePlayer::getUniqueId)
                .noneMatch(u -> u.equals(uuid))) {
            return;
        }
        if (Bukkit.getBannedPlayers().stream()
                .map(OfflinePlayer::getUniqueId)
                .anyMatch(uuid::equals)) {
            return;
        }
        //check (real) time
        int seconds = LocalDateTime.now().toLocalTime().toSecondOfDay();
        if (seconds >= 24 * 60 * 60 - 7 || seconds <= 3) {
            disallow(event, "&cNa serwer możesz ponownie dołączyć 3 sekundy po północy. Przepraszamy.");
        }

        //check player's nick length
        if (event.getName().length() > 36) {
            disallow(event, "&cTwój nick jest za długi! Może mieć maksymalnie 36 znaków.");
            return;
        }
        //check if Discord bot is online
        JDA jda = plugin.getJda();
        if (jda == null) {
            disallow(event, Prefix.DISCORD + "&cBot na Discord jest offline! Nie możesz dołączyć do serwera.");
            return;
        }
        //check tps
        if (Utils.getTps() < 15.0 && !plugin.getPremiumManager().isSuperPremium(uuid)) {
            disallow(event, "&cSerwer jest przeciążony - TPS spadły poniżej 15\nSpróbuj dołączyć później\n&7Przepraszamy");
            return;
        }
        //check if player has linked account
        if (!plugin.getAccountsManager().isRequired(uuid)) return;
        UserSnowflake id = plugin.getAccountsManager().getUserByPlayer(uuid);
        if (id == null) {
            String code = plugin.getAccountsManager().getNewVerificationCode(uuid);
            disallow(event, Prefix.DISCORD + "\n" +
                    "Nie połączyłeś konta Discord z twoim kontem minecraft!\n\n" +
                    "Użyj komendy &9/accounts &bna Discord\n" +
                    "z kodem &9" + code + "&b\n\n" +
                    "&cUWAGA! &bKod będzie ważny tylko 5 minut.");
            return;
        }
        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) {
            Logs.warning("A guild id in the config file is not correct.");
            return;
        }
        Member member = DiscordUtils.retrieveMember(guild, id);
        if (member == null) {
            disallow(event, Prefix.DISCORD + "Wygląda na to, że nie ma cię na naszym serwerze Discord! Dołącz do niego, aby grać na tym serwerze.");
            return;
        }
        if (!DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.player")) ||
                !DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.verified"))) {
            plugin.getAccountsManager().unlink(uuid);
            disallow(event, Prefix.DISCORD + "Twoje konto zostało rozłączone przed administratora. Dołącz ponownie, aby je połączyć.");
        }
        //refresh player name
        plugin.getOfflinePlayers().refresh(uuid, event.getName());
    }
    private void disallow(@NotNull AsyncPlayerPreLoginEvent event, @NotNull String msg) {
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Utils.getComponentByString(msg));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLoginMonitor(@NotNull AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) return;
        IncognitoManager manager = plugin.getIncognitoManager();
        manager.getIncognitoPlayers().stream()
                .filter(uuid -> !uuid.equals(event.getUniqueId()))
                .filter(manager::isKickingEnabled)
                .filter(uuid -> !manager.getTrustedPlayers(uuid).contains(event.getUniqueId()))
                .map(Bukkit::getPlayer)
                .filter(Objects::nonNull)
                .forEach(player -> Utils.sync(() -> player.kick(Utils.getComponentByString(Prefix.INCOGNITO + "Gracz " + event.getName() + " dołącza do gry. Zostałeś wyrzucony, ponieważ masz włączony tryb incognito."))));
    }
}
