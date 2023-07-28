package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.Prefix;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class AntyLogoutManager {
    public AntyLogoutManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private BukkitTask task;
    //ANTY-LOGOUT XXs (https://rgb.birdflop.com/)
    private final String ACTION_BAR_MSG = "&x&f&b&0&0&0&0A&x&f&b&1&2&0&0N&x&f&b&2&4&0&0T&x&f&b&3&7&0&0Y&x&f&c&4&9&0&0-&x&f&c&5&b&0&0L&x&f&c&6&d&0&0O&x&f&c&8&0&0&0G&x&f&c&9&2&0&0O&x&f&c&a&4&0&0U&x&f&d&b&6&0&0T &x&f&d&c&9&0&0X&x&f&d&d&b&0&0X&x&f&d&e&d&0&0s";
    private final int TIME_SECONDS = 15;
    private final HashMap<UUID, Pair<UUID, Integer>> logout = new HashMap<>();

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, Pair<UUID, Integer>>> it = logout.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<UUID, Pair<UUID, Integer>> e = it.next();

                Player player = Bukkit.getPlayer(e.getKey());
                if (player == null) continue;

                Pair<UUID, Integer> pair = e.getValue();
                if (pair.getSecond() <= 0) {
                    player.sendActionBar(Utils.getComponentByString("&cJuż możesz się wylogować."));
                    it.remove();
                    continue;
                }

                pair.setSecond(pair.getSecond() - 1);
                e.setValue(pair);
                player.sendActionBar(Utils.getComponentByString(getActionBar(pair.getSecond())));
            }
        }, 20, 20);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        logout.clear();
    }

    public boolean isInAntyLogout(@NotNull Player player) {
        return logout.containsKey(player.getUniqueId());
    }

    public void quit(@NotNull Player player) {
        if (!isInAntyLogout(player)) return;
        plugin.getGraveCreateListener().unprotectNextGrave(player.getUniqueId());
        player.setHealth(0);
        Utils.broadcastMessage(
                player,
                Prefix.ANTY_LOGOUT,
                "Twój grób jest odblokowany!",
                "Gracz " + player.getName() + " wyszedł z gry z aktywnym anty-logout'em.",
                "Gracz **" + player.getName() + "** wyszedł z gry z aktywnym anty-logout'em"
        );
    }

    public void cancelAntyLogout(@NotNull Player player) {
        Pair<UUID, Integer> pair = logout.remove(player.getUniqueId());
        if (pair == null) return;
        player.sendActionBar(Utils.getComponentByString("&cJuż możesz się wylogować."));
    }

    public void putAntyLogout(@NotNull Player player, @NotNull Entity entity) {
        if (!plugin.getConfig().getStringList("anty-logout.worlds").contains(player.getWorld().getName())) return;
        if (player.getUniqueId().equals(entity.getUniqueId())) return;
        List<String> entities = plugin.getConfig().getStringList("anty-logout.entities");
        if (!entities.isEmpty() && !entities.contains(entity.getType().toString())) return;
        if (entity instanceof Projectile projectile) {
            if (!(projectile.getShooter() instanceof LivingEntity livingEntity)) return;
            entity = livingEntity;
        }
        logout.put(player.getUniqueId(), new Pair<>(entity.getUniqueId(), TIME_SECONDS));
        player.sendActionBar(Utils.getComponentByString(getActionBar(TIME_SECONDS)));
        if (entity instanceof Player attacker) {
            logout.put(attacker.getUniqueId(), new Pair<>(player.getUniqueId(), TIME_SECONDS));
            attacker.sendActionBar(Utils.getComponentByString(getActionBar(TIME_SECONDS)));
        }
    }

    public @Nullable Player getPlayerByEntity(@NotNull Entity entity) {
        for (Map.Entry<UUID, Pair<UUID, Integer>> e : logout.entrySet()) {
            if (e.getValue().getFirst().equals(entity.getUniqueId())) return Bukkit.getPlayer(e.getKey());
        }
        return null;
    }

    private @NotNull String getActionBar(int time) {
        return ACTION_BAR_MSG
                .replaceFirst("X", time >= 10 ? String.valueOf(time / 10) : "")
                .replaceFirst("X", String.valueOf(time % 10));
    }
}
