package me.matiego.st14.markers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.listeners.ClaimChangeListener;
import me.matiego.st14.listeners.ClaimCreateListener;
import me.matiego.st14.listeners.ClaimDeleteListener;
import me.matiego.st14.objects.dynmap.DynmapMarker;
import me.matiego.st14.utils.Utils;
import net.crashcraft.crashclaim.CrashClaim;
import net.crashcraft.crashclaim.api.CrashClaimAPI;
import net.crashcraft.crashclaim.claimobjects.Claim;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.dynmap.markers.AreaMarker;
import org.dynmap.markers.MarkerAPI;
import org.dynmap.markers.MarkerSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ClaimsMarker extends DynmapMarker {
    public ClaimsMarker(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;

    @Override
    protected @NotNull String getMarkerSetId() {
        return "claims";
    }

    @Override
    protected @NotNull String getMarkerSetName() {
        return "Działki";
    }

    @Override
    protected @NotNull String getMarkerIdPrefix() {
        return "claim_";
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

                    claims.forEach(claim -> Utils.sync(() -> refreshClaim(set, claim)));
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

        MarkerSet set = getMarkerSet(markerAPI);
        if (set == null) return;

        AreaMarker marker = set.findAreaMarker(getClaimMarkerId(claim));
        if (marker == null) return;
        marker.deleteMarker();
    }

    private @NotNull String getClaimMarkerId(@NotNull Claim claim) {
        return getMarkerIdPrefix() + claim.getWorld().hashCode() + "_" +  claim.getId();
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

    public void registerListeners() {
        if (Bukkit.getPluginManager().getPlugin("CrashClaim") == null) return;
        plugin.getListenersManager().registerListeners(
                new ClaimCreateListener(plugin),
                new ClaimChangeListener(plugin),
                new ClaimDeleteListener(plugin)
        );
    }
}
