package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import me.matiego.st14.Main;
import me.matiego.st14.managers.IncognitoManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

public class PaperServerListPingListener implements Listener {
    public PaperServerListPingListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onPaperServerListPing(@NotNull PaperServerListPingEvent event) {
        Iterator<PaperServerListPingEvent.ListedPlayerInfo> iterator = event.getListedPlayers().iterator();
        IncognitoManager manager = plugin.getIncognitoManager();
        while (iterator.hasNext()) {
            if (manager.isIncognito(iterator.next().id())) iterator.remove();
        }
    }
}
