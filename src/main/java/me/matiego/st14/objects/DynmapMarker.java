package me.matiego.st14.objects;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DynmapMarker {
    protected abstract @NotNull String getMarkerSetId();
    protected abstract @NotNull String getMarkerSetName();
    protected abstract @NotNull String getMarkerIdPrefix();

    protected @Nullable MarkerSet getMarkerSet(@NotNull MarkerAPI api) {
        MarkerSet set = api.getMarkerSet(getMarkerSetId());
        if (set != null) return set;
        return api.createMarkerSet(getMarkerSetId(), getMarkerSetName(), null, true);
    }

    protected @Nullable MarkerAPI getDynmapMarkerAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("dynmap");
        if (plugin == null || !plugin.isEnabled()) return null;
        if (plugin instanceof DynmapAPI dynmapAPI) {
            return dynmapAPI.getMarkerAPI();
        }
        return null;
    }
}
