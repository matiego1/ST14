package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class PlayerElytraBoostListener implements Listener {
    public PlayerElytraBoostListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler (ignoreCancelled = true)
    public void onPlayerElytraBoost(@NotNull PlayerElytraBoostEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getConfig().getStringList("elytra.worlds").contains(player.getWorld().getName())) return;

        if (Utils.getTps() >= plugin.getConfig().getDouble("elytra.block-below-tps")) {
            player.sendActionBar(Utils.getComponentByString("&cNie możesz teraz latać!"));
            event.setCancelled(true);
            return;
        }

        if (player.getFoodLevel() <= 6) {
            player.sendActionBar(Utils.getComponentByString("&cJesteś zbyt głodny, żeby to zrobić!"));
            event.setCancelled(true);
            return;
        }

        player.setExhaustion(player.getExhaustion() + (float) plugin.getConfig().getDouble("elytra.boost-exhaustion", 2.5d));
        player.setCooldown(Material.FIREWORK_ROCKET, plugin.getConfig().getInt("elytra.boost-cooldown", 2) * 20);
    }
}
