package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerBedLeaveListener implements Listener {
    public PlayerBedLeaveListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerBedLeave(@NotNull PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        World world = event.getBed().getWorld();
        plugin.getPlayerBedEnterListener().clearSleepingInWorld(world);
        if (world.getTime() != 0) return;
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getWorld().equals(world))
                .forEach(p -> p.sendMessage(Utils.getComponentByString("&eGracz &6" + player.getName() + "&e poszedł spać. Słodkich snów!")));
        if (!plugin.getIncognitoManager().isIncognito(player.getUniqueId())) {
            plugin.getChatMinecraft().sendMessage("**[" + Utils.getWorldName(world) + "]** Gracz **" + player.getName() + "** poszedł spać. Słodkich snów!", "Przesypianie nocy");
        }
    }
}
