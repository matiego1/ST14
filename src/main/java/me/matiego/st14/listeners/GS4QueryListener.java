package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.server.GS4QueryEvent;
import me.matiego.st14.Main;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GS4QueryListener implements Listener {
    public GS4QueryListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onGS4Query(@NotNull GS4QueryEvent event){
        GS4QueryEvent.QueryResponse response = event.getResponse();
        List<String> list = new ArrayList<>(response.getPlayers());
        Iterator<String> it = list.iterator();
        while (it.hasNext()) {
            Player player = Bukkit.getPlayer(it.next());
            if (player == null) return;
            if (plugin.getIncognitoManager().isIncognito(player.getUniqueId())) it.remove();
        }
        list.add("do-not-restart-my-server!"); // prevent the hosting from restarting the server when no one is online
        event.setResponse(response.toBuilder()
                .clearPlayers()
                .clearPlugins()
                .currentPlayers(list.size())
                .players(list)
                .build()
        );
    }
}
