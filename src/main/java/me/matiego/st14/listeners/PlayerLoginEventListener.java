package me.matiego.st14.listeners;

import me.matiego.st14.Main;
import me.matiego.st14.PremiumManager;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PlayerLoginEventListener implements Listener {
    public PlayerLoginEventListener(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;

    @EventHandler
    public void onPlayerLoginEvent(@NotNull PlayerLoginEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        if (event.getResult() == PlayerLoginEvent.Result.ALLOWED) {
            Utils.async(() -> {
                UserSnowflake id = plugin.getAccountsManager().getUserByPlayer(uuid);
                if (id == null) return;
                plugin.getAccountsManager().modifyNickname(id, event.getPlayer().getName());
            });
        }
        if (event.getResult() != PlayerLoginEvent.Result.KICK_FULL) return;

        PremiumManager manager = plugin.getPremiumManager();
        if (manager.isPremium(uuid) && manager.makeSpaceForPlayer(uuid)) {
            event.allow();
        }
    }
}
