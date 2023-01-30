package me.matiego.st14.listeners;

import me.matiego.counting.ChannelData;
import me.matiego.counting.utils.CountingMessageSendEvent;
import me.matiego.st14.Main;
import me.matiego.st14.RewardsManager;
import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Calendar;
import java.util.TimeZone;
import java.util.UUID;

public class CountingListener implements Listener {
    public CountingListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;

    @EventHandler (ignoreCancelled = true)
    public void onCountingMessageSend(@NotNull CountingMessageSendEvent event) {
        long now = Utils.now();
        long user = event.getUserId();
        ChannelData channel = event.getChannel();

        UUID uuid = plugin.getAccountsManager().getPlayerByUser(UserSnowflake.fromId(user));
        if (uuid == null) return;

        if (!plugin.getConfig().getBoolean("counting-rewards.enabled", true)) return;
        if (plugin.getConfig().getLongList("counting-rewards.disabled-ids").contains(channel.getGuildId())) return;
        if (plugin.getConfig().getLongList("counting-rewards.disabled-ids").contains(channel.getChannelId())) return;
        if (plugin.getConfig().getLongList("counting-rewards.disabled-ids").contains(user)) return;

        RewardsManager.Data data = plugin.getRewardsManager().getCounting(uuid);
        if (data == null) return;
        if (now - data.getLast() <= 15 * 60 * 1000) return;

        double amount = Utils.round(plugin.getConfig().getDouble("counting-rewards.types." + channel.getType().name().toLowerCase()), 2);
        if (isOldMessage(event.getPreviousMessageId())) {
            amount = Utils.round(amount + plugin.getConfig().getDouble("counting-rewards.old-message-bonus"), 2);
        }
        if (amount <= 0) return;

        double limit = data.getLimit();
        final double max = getMax();
        if (limit >= max) return;
        limit += amount;
        if (limit >= max) {
            amount -= Math.max(0, limit - max);
            limit = max;
        }

        data.setLast(now);
        data.setLimit(limit);
        if (!plugin.getRewardsManager().setCounting(uuid, data)) return;

        if (plugin.getEconomy().depositPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess()) {
            event.setDisplayName("[" + amount + "$] " + event.getDisplayName());
            Logs.info("[DEBUG] LICZENIE - gracz: " + plugin.getOfflinePlayers().getEffectiveNameById(uuid) + "; kanał: " + channel.getChannelId() + "; typ: " + channel.getType().name() + "; ilość: " + amount);
        }
    }

    private double getMax() {
        double max = Utils.round(plugin.getConfig().getDouble("counting-rewards.max"), 2);
        return max <= 0 ? 30 : max;
    }

    private boolean isOldMessage(@Nullable Long id) {
        if (id == null) return false;
        long timestamp = (id >>> 22) + 1420070400000L;
        long now = Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTimeInMillis();
        return now - timestamp >= 24L * 3600L * 1000L;
    }
}
