package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class PlayerQuitListener implements Listener {
    public PlayerQuitListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    private final HashMap<UUID, BukkitTask> disableIncognitoTasks = new HashMap<>();

    @EventHandler
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();

        plugin.getAntyLogoutManager().quit(player);
        plugin.getTellCommand().removeReply(player.getUniqueId());
        plugin.getTpaCommand().cancel(player);
        plugin.getPlayerMoveListener().removeBossBarForPlayer(player);
        plugin.getAfkManager().move(player);
        plugin.getBackpackManager().clearCache(player.getUniqueId());
        plugin.getMiniGamesManager().onPlayerQuit(player);
        plugin.getTeleportsManager().onPlayerQuit(player);
        plugin.getClaimsDynmap().refreshPlayerClaims(player);

        event.quitMessage(Utils.getComponentByString("&eGracz " + player.getName() + " opuścił grę"));
        plugin.getChatMinecraft().sendConsoleQuitMessage(player);
        plugin.getChatMinecraft().sendQuitMessage(player);

        Utils.async(() -> {
            plugin.getTimeManager().quit(player);
            plugin.getRewardsManager().unload(player.getUniqueId());
        });

        disableIncognitoTasks.put(
                player.getUniqueId(),
                Bukkit.getScheduler().runTaskLater(
                        plugin,
                        () -> {
                            plugin.getIncognitoManager().setIncognito(player.getUniqueId(), false);
                            disableIncognitoTasks.remove(player.getUniqueId());
                        },
                        1200
                )
        );
    }

    public void cancelDisableIncognitoTask(@NotNull Player player) {
        BukkitTask task = disableIncognitoTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }
}
