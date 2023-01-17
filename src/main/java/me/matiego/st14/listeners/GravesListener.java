package me.matiego.st14.listeners;

import com.ranull.graves.event.GraveCreateEvent;
import com.ranull.graves.type.Grave;
import me.matiego.st14.Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class GravesListener implements Listener {
    @EventHandler
    public void onGraveCreate(@NotNull GraveCreateEvent event) {
        Grave grave = event.getGrave();
        if (Main.getInstance().getSuicideCommand().isSuicide(grave.getOwnerUUID())) {
            grave.setProtection(false);
        }
    }
}
