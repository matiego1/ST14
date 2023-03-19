package me.matiego.st14;

import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Utils;
import net.crashcraft.crashclaim.CrashClaim;
import net.crashcraft.crashclaim.api.CrashClaimAPI;
import net.crashcraft.crashclaim.claimobjects.Claim;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
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
    private final int FAILED_ATTEMPT_COOLDOWN_IN_SECONDS = 900;
    private long lastFailedAttempt = -1;

    public void refreshClaims(@NotNull Chunk chunk) {
        if (hasPreviousAttemptFailed()) return;

        CrashClaimAPI claimAPI = getCrashClaimAPI();
        if (claimAPI == null) return;
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        claimAPI.getClaimsAsync(chunk.getX(), chunk.getZ(), chunk.getWorld().getUID())
                .whenCompleteAsync((claims, e) -> {
                    if (claims == null) {
                        Logs.warning("An error occurred while loading claims in chunk", e);
                        markAttemptAsFailed();
                        return;
                    }

                    MarkerSet set = getMarkerSet(markerAPI);
                    if (set == null) {
                        Logs.warning("An error occurred while creating marker set for claims");
                        markAttemptAsFailed();
                        return;
                    }

                    claims.forEach(claim -> refreshClaim(set, claim));
                });
    }
    public void refreshClaims(@NotNull Player player) {
        if (hasPreviousAttemptFailed()) return;

        CrashClaimAPI claimAPI = getCrashClaimAPI();
        if (claimAPI == null) return;
        MarkerAPI markerAPI = getDynmapMarkerAPI();
        if (markerAPI == null) return;

        claimAPI.getClaimsAsync(player)
                .whenCompleteAsync((claims, e) -> {
                    if (claims == null) {
                        Logs.warning("An error occurred while loading player's claims", e);
                        markAttemptAsFailed();
                        return;
                    }

                    MarkerSet set = getMarkerSet(markerAPI);
                    if (set == null) {
                        Logs.warning("An error occurred while creating marker set for claims");
                        markAttemptAsFailed();
                        return;
                    }

                    claims.forEach(claim -> refreshClaim(set, claim));
                });
    }

    private synchronized boolean hasPreviousAttemptFailed() {
        if (lastFailedAttempt == -1) return false;
        return (Utils.now() - lastFailedAttempt) / 1000 <= FAILED_ATTEMPT_COOLDOWN_IN_SECONDS;
    }

    private synchronized void markAttemptAsFailed() {
        lastFailedAttempt = Utils.now();
    }

    private @Nullable MarkerSet getMarkerSet(@NotNull MarkerAPI api) {
        MarkerSet set = api.getMarkerSet(MARKER_SET_ID);
        if (set != null) return set;

        return api.createMarkerSet(MARKER_SET_ID, "Działki", null, true);
    }

    private void refreshClaim(@NotNull MarkerSet markerSet, @NotNull Claim claim) {
        World world = Bukkit.getWorld(claim.getWorld());
        if (world == null) return;

        AreaMarker marker = markerSet.findAreaMarker("claim_" + claim.getId());
        if (marker == null) {
            marker = markerSet.createAreaMarker(
                    "claim_" + claim.getId(),
                    getLabel(claim),
                    true,
                    world.getName(),
                    new double[]{claim.getMinX(), claim.getMaxX()},
                    new double[]{claim.getMinZ(), claim.getMaxZ()},
                    true
            );
            if (marker == null) {
                Logs.warning("An error occurred while creating area marker for claim");
                markAttemptAsFailed();
                return;
            }
        }
        marker.setLineStyle(1, 1, 0x00ffb3);
        marker.setFillStyle(0.1, 0xd6fff3);
        marker.setLabel(getLabel(claim), true);
        marker.setCornerLocations(new double[]{claim.getMinX(), claim.getMaxX()}, new double[]{claim.getMinZ(), claim.getMaxZ()});
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

    private @Nullable CrashClaimAPI getCrashClaimAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("CrashClaim");
        if (plugin == null) return null;
        if (plugin instanceof CrashClaim crashClaim) {
            return crashClaim.getApi();
        }
        return null;
    }

    private @Nullable MarkerAPI getDynmapMarkerAPI() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("dynmap");
        if (plugin == null) return null;
        if (plugin instanceof DynmapAPI dynmapAPI) {
            return dynmapAPI.getMarkerAPI();
        }
//        System.out.println("BBBBB" + plugin.getClass() + " " + Arrays.toString(plugin.getClass().getInterfaces()));
        return null;
    }
}
