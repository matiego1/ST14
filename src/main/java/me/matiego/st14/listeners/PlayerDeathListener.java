package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerDeathListener implements Listener {
    public PlayerDeathListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(@NotNull PlayerDeathEvent event) {
        Player player = event.getPlayer();

        plugin.getAntyLogoutManager().cancelAntyLogout(player);
        plugin.getMiniGamesManager().onPlayerDeath(player);

        Component component = event.deathMessage();
        String msg = player.getName() + " died";
        if (component != null) {
            msg = PlainTextComponentSerializer.plainText().serialize(component);
        }
        event.deathMessage(Utils.getComponentByString("&4[" + Utils.getWorldPrefix(player.getWorld()) + "]&c " + msg));
        plugin.getChatMinecraft().sendDeathMessage("**[" + Utils.getWorldPrefix(player.getWorld()) + "]** " + msg, event.getPlayer());
    }
}
