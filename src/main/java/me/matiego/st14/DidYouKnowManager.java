package me.matiego.st14;

import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DidYouKnowManager {
    public DidYouKnowManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    private BukkitTask task;
    private List<String> messages = new ArrayList<>();
    private int lastIndex = 0;

    public synchronized void start() {
        stop();

        messages = plugin.getConfig().getStringList("did-you-know.messages");
        if (messages.isEmpty()) return;

        if (plugin.getConfig().getBoolean("did-you-know.shuffle", true)) {
            Collections.shuffle(messages);
        }

        long period = Math.max(60L, plugin.getConfig().getLong("did-you-know.period-seconds")) * 20;

        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (shouldNotBroadcastMessage()) return;

            if (lastIndex >= messages.size()) lastIndex = 0;

            broadcast(messages.get(lastIndex));

            if (++lastIndex >= messages.size()) {
                lastIndex = 0;
            }

        }, 20, period);
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private boolean shouldNotBroadcastMessage() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return false;
        }
        return true;
    }

    private void broadcast(@NotNull String message) {
        Bukkit.broadcast(Utils.getComponentByString("&3&l=== Czy wiesz, że... ===\n&b" + message));

        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Czy wiesz, że...");
        eb.setDescription(message);
        eb.setColor(Color.PINK);
        plugin.getChatMinecraft().sendMessageEmbed(eb.build());
    }
}
