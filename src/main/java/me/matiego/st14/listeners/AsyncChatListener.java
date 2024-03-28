package me.matiego.st14.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.matiego.st14.Main;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextReplacementConfig;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class AsyncChatListener implements Listener {
    public AsyncChatListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onAsyncChat(@NotNull AsyncChatEvent event) {
        Player player = event.getPlayer();

        if (!plugin.getNonPremiumManager().isLoggedIn(player)) {
            event.setCancelled(true);
            player.sendActionBar(Utils.getComponentByString("&cMusisz się zalogować, aby to zrobić!"));
            return;
        }

        plugin.getAfkManager().move(player);

        Block block = player.getLocation().getBlock();
        final Component message = event.message().replaceText(
                TextReplacementConfig
                        .builder()
                        .matchLiteral("[here]")
                        .replacement("[" + Utils.getWorldName(player.getWorld()) + ": " + block.getX() + ", " + block.getY() + ", " + block.getZ() + "]")
                        .once()
                        .build()
        );

        event.renderer((p1, p2, p3, p4) ->
                Utils.getComponentByString("&a[" + Utils.getWorldPrefix(player.getWorld()) + "] &f")
                        .append(player.displayName())
                        .append(Utils.getComponentByString("&f: "))
                        .append(message)
        );
        plugin.getChatMinecraftManager().sendChatMessage(Utils.getPlainTextByComponent(message), player);
    }
}
