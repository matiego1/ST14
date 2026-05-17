package me.matiego.st14.managers;

import lombok.Getter;
import me.matiego.st14.Main;
import me.matiego.st14.markers.ClaimsMarker;
import org.jetbrains.annotations.NotNull;

public class DynmapManager {
    public DynmapManager(@NotNull Main plugin) {
        this.claimsMarker = new ClaimsMarker(plugin);
    }

    @Getter private final ClaimsMarker claimsMarker;
}
