package me.matiego.st14.listeners;

import me.matiego.counting.ChannelData;
import me.matiego.counting.utils.CountingMessageSendEvent;
import me.matiego.st14.Main;
import me.matiego.st14.managers.RewardsManager;
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

public class CountingMessageSendListener implements Listener {
    public CountingMessageSendListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
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

        RewardsManager.Data data = plugin.getRewardsManager().getRewardForCounting().get(uuid);
        if (data == null) return;
        if (now - data.getLast() <= 15 * 60 * 1000) return;

        double amount = Utils.round(plugin.getConfig().getDouble("counting-rewards.types." + channel.getType().name().toLowerCase()), 2);
        if (isOldMessage(event.getPreviousMessageId())) {
            amount = Utils.round(amount + plugin.getConfig().getDouble("counting-rewards.old-message.bonus"), 2);
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
        if (!plugin.getRewardsManager().getRewardForCounting().set(uuid, data)) return;

        if (plugin.getEconomyManager().depositPlayer(Bukkit.getOfflinePlayer(uuid), amount).transactionSuccess()) {
            event.setDisplayName("[" + plugin.getEconomyManager().format(amount) + "] " + event.getDisplayName());
        }
    }

    private double getMax() {
        return Math.max(0, Utils.round(plugin.getConfig().getDouble("counting-rewards.max"), 2));
    }

    private boolean isOldMessage(@Nullable Long id) {
        if (id == null) return false;
        long different = Calendar.getInstance(TimeZone.getTimeZone("GMT")).getTimeInMillis() - ((id >>> 22) + 1420070400000L);
        return different >= Math.max(0, plugin.getConfig().getInt("counting-rewards.old-message.days",1)) * 3600L * 1000L;
    }
}
