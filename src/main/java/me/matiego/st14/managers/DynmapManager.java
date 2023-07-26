package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.listeners.ClaimChangeListener;
import me.matiego.st14.listeners.ClaimCreateListener;
import me.matiego.st14.listeners.ClaimDeleteListener;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import net.crashcraft.crashclaim.CrashClaim;
import net.crashcraft.crashclaim.api.CrashClaimAPI;
import net.crashcraft.crashclaim.claimobjects.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.DynmapAPI;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.Marker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class DynmapManager {
    public DynmapManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final String CLAIMS_MARKER_SET_ID = "claims";
    private final String CLAIM_MARKER_ID = "claim_";
    private final String SIGNS_MARKER_SET_ID = "signs";
    private final String SIGN_MARKER_ID = "sign_";


    public void registerListeners() {
        if (Bukkit.getPluginManager().getPlugin("CrashClaim") == null) return;
        plugin.getListenersManager().registerListeners(
                new ClaimCreateListener(plugin),
                new ClaimChangeListener(plugin),
                new ClaimDeleteListener(plugin)
        );
    }

    //TODO: split to separate classes

    // ---- SIGNS ----

    public boolean addSignMarker(@NotNull Location location, @NotNull String text) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return false;

        MarkerSet set = getSignsMarkerSet(markerAPI);
        if (set == null) {
            Logs.warning("An error occurred while creating marker set for signs");
            return false;
        }

        Marker marker = set.findMarker(SIGN_MARKER_ID + location);

        if (marker == null) {
            marker = set.createMarker(
                    SIGN_MARKER_ID + location,
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
                Logs.warning("An error occurred while creating marker for sign (" + SIGN_MARKER_ID + location + ")");
                return false;
            }
        }

        marker.setLabel(text);
        return true;
    }

    public void deleteSignMarker(@NotNull Location location) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        MarkerSet set = getSignsMarkerSet(markerAPI);
        if (set == null) return;

        Marker marker = set.findMarker(SIGN_MARKER_ID + location);
        if (marker == null) return;
        marker.deleteMarker();
    }

    private @Nullable MarkerSet getSignsMarkerSet(@NotNull MarkerAPI api) {
        MarkerSet set = api.getMarkerSet(SIGNS_MARKER_SET_ID);
        if (set != null) return set;

        return api.createMarkerSet(SIGNS_MARKER_SET_ID, "Tabliczki", null, true);
    }

    // ---- CLAIMS ----

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

                    MarkerSet set = getClaimsMarkerSet(markerAPI);
                    if (set == null) {
                        Logs.warning("An error occurred while creating marker set for claims");
                        return;
                    }

                    claims.forEach(claim -> Utils.sync(() -> refreshClaim(set, claim)));
                });
    }

    public void refreshClaim(@NotNull Claim claim) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        MarkerSet set = getClaimsMarkerSet(markerAPI);
        if (set == null) {
            Logs.warning("An error occurred while creating marker set for claims");
            return;
        }

        Utils.sync(() -> refreshClaim(set, claim));
    }

    private void refreshClaim(@NotNull MarkerSet markerSet, @NotNull Claim claim) {
        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;

        AreaMarker marker = markerSet.findAreaMarker(getClaimMarkerId(claim));
        if (marker == null) {
            marker = markerSet.createAreaMarker(
                    getClaimMarkerId(claim),
                    getClaimLabel(claim),
                    true,
                    world.getName(),
                    new double[]{claim.getMinX(), claim.getMaxX() + 1},
                    new double[]{claim.getMinZ(), claim.getMaxZ() + 1},
                    true
            );
            if (marker == null) {
                Logs.warning("An error occurred while creating area marker for claim (" + getClaimMarkerId(claim) + ")");
                return;
            }
        }

        marker.setLineStyle(1, 1, 0x00ffb3);
        marker.setFillStyle(0.1, 0xd6fff3);
        marker.setLabel(getClaimLabel(claim), true);
        marker.setCornerLocations(new double[]{claim.getMinX(), claim.getMaxX() + 1}, new double[]{claim.getMinZ(), claim.getMaxZ() + 1});
    }

    public void deleteClaim(@NotNull Claim claim) {
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        MarkerSet set = getClaimsMarkerSet(markerAPI);
        if (set == null) return;

        AreaMarker marker = set.findAreaMarker(getClaimMarkerId(claim));
        if (marker == null) return;
        marker.deleteMarker();
    }

    private @NotNull String getClaimMarkerId(@NotNull Claim claim) {
        return CLAIM_MARKER_ID + claim.getWorld().hashCode() + "_" +  claim.getId();
    }

    private @Nullable MarkerSet getClaimsMarkerSet(@NotNull MarkerAPI api) {
        MarkerSet set = api.getMarkerSet(CLAIMS_MARKER_SET_ID);
        if (set != null) return set;

        return api.createMarkerSet(CLAIMS_MARKER_SET_ID, "Działki", null, true);
    }

    private @NotNull String getClaimLabel(@NotNull Claim claim) {
        return plugin.getConfig().getString("claims-dynmap-label", "{name}")
                .replace("{owner}", plugin.getOfflinePlayersManager().getEffectiveNameById(claim.getOwner()))
                .replace("{name}", claim.getName())
                .replace("{minX}", String.valueOf(claim.getMinX()))
                .replace("{maxX}", String.valueOf(claim.getMaxX()))
                .replace("{minZ}", String.valueOf(claim.getMinZ()))
                .replace("{maxZ}", String.valueOf(claim.getMaxZ()));
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
}
