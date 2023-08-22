package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.NonPremiumUtils;
import me.matiego.st14.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.Keyed;
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

        //claims
        plugin.getDynmapManager().refreshPlayerClaims(player);
        //load player times
        if (!plugin.getTimeManager().join(player)) {
            player.kick(Utils.getComponentByString("&cNapotkano niespodziewany błąd przy ładowaniu twoich czasów. Spróbuj ponownie."));
            return;
        }
        //load rewards
        Utils.async(() -> {
            if (!plugin.getRewardsManager().getRewardForPlaying().loadToCache(uuid)) {
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
        Utils.async(() -> {
            long time = plugin.getPremiumManager().getRemainingTime(uuid);
            if (plugin.getPremiumManager().isSuperPremium(uuid)) {
                player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Jesteś graczem super premium!"));
            } else if (time > 0) {
                player.sendMessage(Utils.getComponentByString(Prefix.PREMIUM + "Jesteś graczem premium! Twój status premium wygaśnie za &6" + Utils.parseMillisToString(time, false) + "&d."));
            }
        });
        //join messages
        event.joinMessage(Utils.getComponentByString("&eGracz " + player.getName() + " dołączył do gry"));
        plugin.getChatMinecraftManager().sendJoinMessage(player);
        plugin.getChatMinecraftManager().sendConsoleJoinMessage(player);
        //handle minigame
        plugin.getMiniGamesManager().onPlayerJoin(player);
        //non-premium
        if (NonPremiumUtils.isNonPremiumUuid(uuid)) {
            player.sendMessage(Utils.getComponentByString("&e&lSystem umożliwiający grę graczom non-premium jest w wersji BETA. Zgłaszaj wszystkie napotkane błędy!"));
            Bukkit.getScheduler().runTaskLater(plugin, () -> player.kick(Utils.getComponentByString("&cNie zalogowałeś się w przeciągu 30 sekund! Ponownie rozpocznij sesję.")), 20 * 30);
        }
        //unlock recipes
        Bukkit.recipeIterator().forEachRemaining(recipe -> {
            if (recipe instanceof Keyed keyed) {
                if (!player.hasDiscoveredRecipe(keyed.getKey())){
                    player.discoverRecipe(keyed.getKey());
                }
            }
        });
    }
}
