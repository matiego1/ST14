package me.matiego.st14.listeners;

import me.matiego.counting.ChannelData;
import me.matiego.counting.utils.MessageSendEvent;
import me.matiego.st14.Main;
import me.matiego.st14.RewardsManager;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class CountingListener implements Listener {
    public CountingListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final int REWARD_LIMIT = 30;

    @EventHandler(ignoreCancelled = true)
    public void onMessageSend(@NotNull MessageSendEvent event) {
        UserSnowflake user =  event.getUserId();
        ChannelData channel = event.getChannel();
        long now = Utils.now();
        Utils.async(() -> {
            UUID uuid = plugin.getAccountsManager().getPlayerByUser(user);
            if (uuid == null) return;

            RewardsManager.Data data = plugin.getRewardsManager().getCounting(uuid);
            if (data == null) return;
            if (data.getLimit() >= REWARD_LIMIT) return;
            if (now - data.getLast() <= 15 * 60 * 1000) return;

            if (plugin.getConfig().getLongList("counting-rewards.disabled-channels").contains(channel.getChannelId())) return;
            int amount = plugin.getConfig().getInt("counting-rewards.types." + channel.getType().name().toLowerCase());
            if (amount == 0) return;

            data.setLast(now);
            data.setLimit(data.getLimit() + amount);
            if (!plugin.getRewardsManager().setCounting(uuid, data)) return;

            if (plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess()) {
                Logs.info("[DEBUG] LICZENIE - gracz: " + plugin.getOfflinePlayers().getEffectiveNameById(uuid) + "; kanał: " + channel.getChannelId() + "; typ: " + channel.getType().name() + "; ilość: " + amount);
            }
        });
    }
}
