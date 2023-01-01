package me.matiego.st14;

import me.matiego.st14.utils.GameTime;
import me.matiego.st14.utils.PlayerTime;
import me.matiego.st14.utils.Prefixes;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

public class AfkManager {
    private final Main plugin;
    public AfkManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final int AFK_TIME_SECOND = 180;

    private BukkitTask task;
    private final HashMap<Player, Long> lastMove = new HashMap<>();
    private final Set<Player> afk = new HashSet<>();

    public synchronized boolean isAfk(@NotNull Player player) {
        return afk.contains(player);
    }

    private void setAfk(@NotNull Player player, boolean value) {
        if (isAfk(player) == value) return;
        if (value) {
            broadcastMessage(player, "Jesteś AFK!", "Gracz " + player.getName() + " jest AFK!", "Gracz **" + player.getName() + "** jest AFK!");
            lastMove.remove(player);
            afk.add(player);

            PlayerTime time = plugin.getTimeManager().getTime(player.getUniqueId());
            if (time != null && time.getType() == GameTime.Type.NORMAL) time.setType(GameTime.Type.AFK);
        } else {
            broadcastMessage(player, "Już nie jesteś AFK!", "Gracz " + player.getName() + " już nie jest AFK!", "Gracz **" + player.getName() + "** już nie jest AFK!");
            afk.remove(player);

            PlayerTime time = plugin.getTimeManager().getTime(player.getUniqueId());
            if (time != null && time.getType() == GameTime.Type.AFK) time.setType(GameTime.Type.NORMAL);
        }
    }

    private void broadcastMessage(@NotNull Player player, @NotNull String self, @NotNull String others, @NotNull String discord) {
        Utils.async(() -> {
            player.sendMessage(Utils.getComponentByString(Prefixes.AFK + self));

            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(player))
                    .forEach(p -> p.sendMessage(Utils.getComponentByString(Prefixes.AFK + others)));
            Bukkit.getConsoleSender().sendMessage(Utils.getComponentByString(Prefixes.AFK + others));

            if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) return;
            plugin.getChatMinecraft().sendMessage(discord, Prefixes.AFK.getDiscord());
        });
    }

    public synchronized void move(@NotNull Player player) {
        if (isAfk(player)) setAfk(player, false);
        lastMove.put(player, Utils.now());
    }

    private long getLastMove(@NotNull Player player) {
        return lastMove.getOrDefault(player, Utils.now());
    }

    public synchronized void start() {
        if (task != null) {
            task.cancel();
        }
        task = Bukkit.getScheduler().runTaskTimer(
                plugin,
                () -> Bukkit.getOnlinePlayers().stream()
                        .filter(player -> Utils.now() - getLastMove(player) >= AFK_TIME_SECOND * 1000L)
                        .forEach(player -> setAfk(player, true)),
                1000,
                100
        );
    }

    public synchronized void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }
}
