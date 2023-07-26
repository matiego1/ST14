package me.matiego.st14.listeners;

import me.matiego.st14.managers.IncognitoManager;
import me.matiego.st14.Main;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class ServerListPingListener implements Listener {
    public ServerListPingListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onServerListPing(@NotNull ServerListPingEvent event) {
        Iterator<Player> iterator = event.iterator();
        IncognitoManager manager = plugin.getIncognitoManager();
        while (iterator.hasNext()) {
            if (manager.isIncognito(iterator.next().getUniqueId())) iterator.remove();
        }
    }
}
