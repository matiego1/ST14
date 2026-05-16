package me.matiego.st14.listeners;

import com.ranull.graves.type.Grave;
import dev.cwhead.GravesX.event.GravePostTeleportEvent;
import dev.cwhead.GravesX.event.GravePreTeleportEvent;
import me.matiego.st14.Main;
import me.matiego.st14.managers.EconomyManager;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class GraveTeleportListener implements Listener {
    public GraveTeleportListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String PREFIX = "&c☠ &r";

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGravePreTeleport(@NotNull GravePreTeleportEvent event) {
        if (!event.isPlayer()) return;
        Player player = event.getPlayer();
        Grave grave = event.getGrave();
        if (grave == null) return;

        Location l1 = grave.getLocationDeath();
        Location l2 = player.getLocation();

        double cost = plugin.getConfig().getDouble("graves-teleport-cost", 0);
        if (cost <= 0) return;

        if (!l1.getWorld().equals(l2.getWorld())) {
            player.sendMessage(Utils.getComponentByString(PREFIX + "You can't teleport between worlds."));
            event.setCancelled(true);
            return;
        }

        cost = Utils.round(cost * l1.distance(l2) / 16, 2);
        if (cost <= 0) return;

        EconomyManager economy = plugin.getEconomyManager();
        double balance = economy.getBalance(player);
        if (balance < cost) {
            player.sendMessage(Utils.getComponentByString(PREFIX + "You don't have sufficient funds! You need " + economy.format(cost) + " but only have " + economy.format(balance)));
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGravePostTeleport(@NotNull GravePostTeleportEvent event) {
        if (!event.hasPlayer()) return;
        Player player = event.getPlayer();
        Grave grave = event.getGrave();
        if (player == null || grave == null) return;

        Location l1 = grave.getLocationDeath();
        Location l2 = event.getFrom();

        double cost = plugin.getConfig().getDouble("graves-teleport-cost", 0);
        if (cost <= 0) return;

        if (!l1.getWorld().equals(l2.getWorld())) {
            player.sendMessage(Utils.getComponentByString(PREFIX + "You can't teleport between worlds."));
            event.setCancelled(true);
            return;
        }

        cost = Utils.round(cost * l1.distance(l2) / 16, 2);
        if (cost <= 0) return;

        EconomyManager economy = plugin.getEconomyManager();
        EconomyResponse response = economy.withdrawPlayer(player, cost);

        if (response.transactionSuccess()) {
            player.sendMessage(Utils.getComponentByString(PREFIX + "You teleported to your grave for " + economy.format(cost)));
        } else {
            player.sendMessage(Utils.getComponentByString(PREFIX + "You don't have sufficient funds! You need " + economy.format(cost) + " but only have " + economy.format(response.balance)));
            event.setCancelled(true);
        }
    }
}
