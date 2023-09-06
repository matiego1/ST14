package me.matiego.st14.markers;

import me.matiego.st14.Logs;
import me.matiego.st14.objects.DynmapMarker;
import org.bukkit.Location;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.NotNull;

public class SignsMarker extends DynmapMarker {
    @Override
    protected @NotNull String getMarkerSetId() {
        return "signs";
    }

    @Override
    protected @NotNull String getMarkerSetName() {
        return "Tabliczki";
    }

    @Override
    protected @NotNull String getMarkerIdPrefix() {
        return "sign_";
    }

    public boolean addMarker(@NotNull Location location, @NotNull String text) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return false;

        MarkerSet set = getMarkerSet(markerAPI);
        if (set == null) {
            Logs.warning("An error occurred while creating marker set for signs");
            return false;
        }

        Marker marker = set.findMarker(getMarkerIdPrefix() + location);

        if (marker == null) {
            marker = set.createMarker(
                    getMarkerIdPrefix() + location,
                    "loading...",
                    false,
                    location.getWorld().getName(),
                    location.getBlockX() + 0.5,
                    location.getBlockY() + 0.5,
                    location.getBlockZ() + 0.5,
                    markerAPI.getMarkerIcon("sign"),
                    true
            );
            if (marker == null) {
                Logs.warning("An error occurred while creating marker for sign (" + getMarkerIdPrefix() + location + ")");
                return false;
            }
        }

        marker.setLabel(text);
        return true;
    }

    public void deleteMarker(@NotNull Location location) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        MarkerSet set = getMarkerSet(markerAPI);
        if (set == null) return;

        Marker marker = set.findMarker(getMarkerIdPrefix() + location);
        if (marker == null) return;
        marker.deleteMarker();
    }
}
