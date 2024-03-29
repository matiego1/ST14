package me.matiego.st14.listeners;

import io.papermc.paper.advancement.AdvancementDisplay;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerAdvancementDoneListener implements Listener {
    public PlayerAdvancementDoneListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler(ignoreCancelled = true)
    public void onPlayerAdvancementDone(@NotNull PlayerAdvancementDoneEvent event) {
        Component message = event.message();
        if (message == null) return;
        AdvancementDisplay advancementDisplay = event.getAdvancement().getDisplay();
        plugin.getChatMinecraftManager().sendAdvancementMessage(
                Utils.getPlainTextByComponent(message),
                advancementDisplay == null ? "" : Utils.getPlainTextByComponent(advancementDisplay.description()),
                event.getPlayer()
        );
    }
}
