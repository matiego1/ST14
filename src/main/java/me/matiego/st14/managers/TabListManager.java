package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.minigames.MiniGame;
import me.matiego.st14.utils.NonPremiumUtils;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class TabListManager {
    private final Main plugin;
    public TabListManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private BukkitTask task;
    private boolean log15 = false;
    private boolean log10 = false;

    private void refreshTabList() {
        String tps = formatTps(Utils.getTps());
        MiniGame miniGame = plugin.getMiniGamesManager().getActiveMiniGame();
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendPlayerListHeaderAndFooter(Utils.getComponentByString("&a&lSerwer ST14"), Utils.getComponentByString("&aTPS: " + tps + "&a; PING: " + (player.getPing() == 0 ? "&cWczytywanie..." : player.getPing() + " ms")));
            player.playerListName(Utils.getComponentByString(
                    (miniGame != null && miniGame.getPlayersInMiniGame().contains(player) ? "&a✔ " : "") +
                    "&2[" + Utils.getWorldPrefix(player.getWorld()) + "]&f " +
                    (plugin.getAfkManager().isAfk(player) ? "&8[AFK]&f " : "") +
                    (plugin.getIncognitoManager().isIncognito(player.getUniqueId()) ? "&7[INC]&f " : "") +
                    (NonPremiumUtils.isNonPremiumUuid(player.getUniqueId()) ? "&c" : "") +
                    (plugin.getPremiumManager().isPremium(player.getUniqueId()) ? "&e" : "") +
                    (plugin.getPremiumManager().isSuperPremium(player.getUniqueId()) ? "&6" : "") +
                    (plugin.getNonPremiumManager().isLoggedIn(player.getUniqueId()) ? "" : "&0") +
                    player.getName()
            ));
        }
    }

    private synchronized @NotNull String formatTps(double tps) {
        tps = Utils.round(tps, 2);
        if (tps >= 18d) {
            if (log15 || log10) {
                Logs.info("TPS są powyżej 18.");
                log10 = log15 = false;
            }
            return "&a" + tps;
        }
        if (tps >= 15d) {
            if (log15 || log10) {
                Logs.info("TPS są powyżej 15.");
                log10 = log15 = false;
            }
            return "&e" + tps;
        }
        if (tps >= 10d) {
            if (log10) {
                Logs.warning("TPS są powyżej 10, ale spadły poniżej 15!");
                log10 = false;
            }
            if (!log15) {
                log15 = true;
                Logs.warning("TPS spadły poniżej 15!");
            }
            return "&c" + tps;
        }
        Utils.sync(() -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isOp()) {
                    player.kick(Utils.getComponentByString("&cTPS spadły poniżej 10! Spróbuj dołączyć później."));
                }
            }
        });
        if (!log10) {
            log10 = true;
            Logs.error("TPS spadły poniżej 10!");
        }
        return "&4" + tps;
    }

    public synchronized void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, this::refreshTabList, 20, 20);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
