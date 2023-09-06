package me.matiego.st14.managers;

import lombok.Getter;
import me.matiego.st14.Main;
import me.matiego.st14.markers.ClaimsMarker;
import me.matiego.st14.markers.SignsMarker;
import org.jetbrains.annotations.NotNull;

public class DynmapManager {
    public DynmapManager(@NotNull Main plugin) {
        this.signsMarker = new SignsMarker();
        this.claimsMarker = new ClaimsMarker(plugin);
    }

    @Getter private final SignsMarker signsMarker;
    @Getter private final ClaimsMarker claimsMarker;
}
