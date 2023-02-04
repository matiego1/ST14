package me.matiego.st14.listeners;

import com.ranull.graves.event.GraveCreateEvent;
import com.ranull.graves.type.Grave;
import me.matiego.st14.utils.Utils;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.UUID;

public class GraveCreateListener implements Listener {

    private final HashMap<UUID, Long> graves = new HashMap<>();

    @EventHandler
    public void onGraveCreate(@NotNull GraveCreateEvent event) {
        Grave grave = event.getGrave();
        UUID owner = grave.getOwnerUUID();
        Long time = graves.remove(owner);
        if (time == null) return;
        if (Utils.now() - time > 3000) return;
        grave.setProtection(false);
    }

    public void unprotectNextGrave(@NotNull UUID uuid) {
        graves.put(uuid, Utils.now());
    }
}
