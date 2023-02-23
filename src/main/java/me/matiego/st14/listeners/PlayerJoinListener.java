package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.NonPremiumUtils;
import me.matiego.st14.utils.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerJoinListener implements Listener {
    public PlayerJoinListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        //load player times
        if (!plugin.getTimeManager().join(player)) {
            player.kick(Utils.getComponentByString("&cNapotkano niespodziewany błąd przy ładowaniu twoich czasów. Spróbuj ponownie."));
            return;
        }
        //load rewards
        Utils.async(() -> {
            if (!plugin.getRewardsManager().load(uuid)) {
                player.sendMessage(Utils.getComponentByString("&cNapotkano niespodziewany błąd! Aby dostawać pieniądze za granie, dołącz ponownie."));
            }
        });
        //incognito
        plugin.getPlayerQuitListener().cancelDisableIncognitoTask(player);
        if (plugin.getIncognitoManager().isIncognito(uuid)) {
            player.sendMessage(Utils.getComponentByString(Prefix.INCOGNITO + "Jesteś incognito!"));
        }
        //afk
        plugin.getAfkManager().move(player);
        //premium
        long time = plugin.getPremiumManager().getRemainingTime(uuid);
        if (time > 0) {
            player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Jesteś graczem premium! Twój status premium wygaśnie za &6" + Utils.parseMillisToString(time, false) + "&d."));
        }
        //join messages
        event.joinMessage(Utils.getComponentByString("&eGracz " + player.getName() + " dołączył do gry"));
        plugin.getChatMinecraft().sendJoinMessage(player);
        plugin.getChatMinecraft().sendConsoleJoinMessage(player);
        //handle game
        plugin.getMiniGameManager().onPlayerJoin(player);
        //non-premium warning
        if (NonPremiumUtils.isNonPremiumUuid(uuid)) {
            player.sendMessage(Utils.getComponentByString("&eSystem umożliwiający grę graczom non-premium jest w wersji BETA. Zgłaszaj wszystkie napotkane błędy!"));
        }
    }
}
