package me.matiego.st14.listeners;

import com.destroystokyo.paper.event.profile.PreFillProfileEvent;
import com.destroystokyo.paper.profile.ProfileProperty;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.NonPremiumUtils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class PreFillProfileListener implements Listener {
    @EventHandler
    public void onPreFillProfile(@NotNull PreFillProfileEvent event) {
        UUID uuid = event.getPlayerProfile().getId();
        if (uuid == null) return;
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return;
        event.getPlayerProfile().setProperty(new ProfileProperty("textures", ""));
        Logs.info("[DEBUG] PreFillProfileEvent fired");
    }
}
