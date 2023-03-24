package me.matiego.st14;

import me.matiego.st14.listeners.ClaimChangeListener;
import me.matiego.st14.listeners.ClaimCreateListener;
import me.matiego.st14.listeners.ClaimDeleteListener;
import me.matiego.st14.utils.Logs;
import net.crashcraft.crashclaim.CrashClaim;
import net.crashcraft.crashclaim.api.CrashClaimAPI;
import net.crashcraft.crashclaim.claimobjects.Claim;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClaimsDynmap {
    public ClaimsDynmap(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String MARKER_SET_ID = "claims";
    private final String MARKER_ID = "claim_";

    public void registerListeners() {
        if (Bukkit.getPluginManager().getPlugin("CrashClaim") == null) return;
        plugin.getListenersManager().registerListeners(
                new ClaimCreateListener(plugin),
                new ClaimChangeListener(plugin),
                new ClaimDeleteListener(plugin)
        );
    }

    public void refreshPlayerClaims(@NotNull Player player) {
        CrashClaimAPI claimAPI = getCrashClaimAPI();
        if (claimAPI == null) return;
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        claimAPI.getClaimsAsync(player)
                .whenCompleteAsync((claims, e) -> {
                    if (e != null) {
                        Logs.warning("An error occurred while loading player's claims", e);
                        return;
                    }
                    if (claims == null) return;

                    MarkerSet set = getMarkerSet(markerAPI);
                    if (set == null) {
                        Logs.warning("An error occurred while creating marker set for claims");
                        return;
                    }

                    claims.forEach(claim -> refreshClaim(set, claim));
                });
    }

    public void refreshClaim(@NotNull Claim claim) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        MarkerSet set = getMarkerSet(markerAPI);
        if (set == null) {
            Logs.warning("An error occurred while creating marker set for claims");
            return;
        }

        refreshClaim(set, claim);
    }

    private void refreshClaim(@NotNull MarkerSet markerSet, @NotNull Claim claim) {
        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;

        AreaMarker marker = markerSet.findAreaMarker(MARKER_ID + claim.getId());
        if (marker == null) {
            marker = markerSet.createAreaMarker(
                    MARKER_ID + claim.getId(),
                    getLabel(claim),
                    true,
                    world.getName(),
                    new double[]{claim.getMinX(), claim.getMaxX() + 1},
                    new double[]{claim.getMinZ(), claim.getMaxZ() + 1},
                    true
            );
            if (marker == null) {
                Logs.warning("An error occurred while creating area marker for claim (" + MARKER_ID + claim.getId() + ")");
                return;
            }
        }

        marker.setLineStyle(1, 1, 0x00ffb3);
        marker.setFillStyle(0.1, 0xd6fff3);
        marker.setLabel(getLabel(claim), true);
        marker.setCornerLocations(new double[]{claim.getMinX(), claim.getMaxX() + 1}, new double[]{claim.getMinZ(), claim.getMaxZ() + 1});
    }

    public void deleteClaim(@NotNull Claim claim) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        MarkerSet set = getMarkerSet(markerAPI);
        if (set == null) {
            Logs.warning("An error occurred while creating marker set for claims");
            return;
        }

        set.findAreaMarker(MARKER_ID + claim.getId()).deleteMarker();
    }

    private @Nullable CrashClaimAPI getCrashClaimAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CrashClaim");
        if (plugin == null || !plugin.isEnabled()) return null;
        if (plugin instanceof CrashClaim crashClaim) {
            return crashClaim.getApi();
        }
        return null;
    }

    private @Nullable MarkerAPI getDynmapMarkerAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("dynmap");
        if (plugin == null || !plugin.isEnabled()) return null;
        if (plugin instanceof DynmapAPI dynmapAPI) {
            return dynmapAPI.getMarkerAPI();
        }
        return null;
    }

    private @Nullable MarkerSet getMarkerSet(@NotNull MarkerAPI api) {
        MarkerSet set = api.getMarkerSet(MARKER_SET_ID);
        if (set != null) return set;

        return api.createMarkerSet(MARKER_SET_ID, "Działki", null, true);
    }

    private @NotNull String getLabel(@NotNull Claim claim) {
        return plugin.getConfig().getString("claims-dynmap-label", "{name}")
                .replace("{owner}", plugin.getOfflinePlayers().getEffectiveNameById(claim.getOwner()))
                .replace("{name}", claim.getName())
                .replace("{minX}", String.valueOf(claim.getMinX()))
                .replace("{maxX}", String.valueOf(claim.getMaxX()))
                .replace("{minZ}", String.valueOf(claim.getMinZ()))
                .replace("{maxZ}", String.valueOf(claim.getMaxZ()));
    }
}
