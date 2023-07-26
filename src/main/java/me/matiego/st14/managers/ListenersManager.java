package me.matiego.st14.managers;

import me.matiego.st14.Main;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

public class ListenersManager {
    public ListenersManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    public void registerListeners(@NotNull Listener... listeners) {
        PluginManager manager = Bukkit.getPluginManager();
        for (Listener listener : listeners) {
            manager.registerEvents(listener, plugin);
        }
    }

    public void registerListener(@NotNull Listener listener) {
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    public void registerListener(@NotNull String channel, @NotNull PluginMessageListener listener) {
        Bukkit.getMessenger().registerIncomingPluginChannel(plugin, channel, listener);
    }
}
