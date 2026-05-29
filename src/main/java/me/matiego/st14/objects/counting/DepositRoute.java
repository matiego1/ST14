package me.matiego.st14.objects.counting;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.EconomyManager;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

import static me.matiego.st14.objects.counting.CountingRewardsHandler.Response;

public class DepositRoute {
    public DepositRoute(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final int MAX_DEPOSIT_AMOUNT = 1000 * 1000 * 1000;
    private final Main plugin;

    public @NotNull Response handle(@NotNull JSONObject params) {
        String userIdParam = getString(params, "user-id");
        String amountParam = getString(params, "amount");

        if (userIdParam == null || amountParam == null) {
            return new Response(400, "Missing user-id and/or amount");
        }

        UserSnowflake user;
        try {
            user = UserSnowflake.fromId(userIdParam);
        } catch (Exception e) {
            return new Response(400, "Invalid user-id");
        }

        UUID uuid = plugin.getAccountsManager().getPlayerByUser(user);
        if (uuid == null) {
            return new Response(404, "Unknown player");
        }

        double amount;
        try {
            amount = Double.parseDouble(amountParam);
        } catch (NumberFormatException e) {
            return new Response(400, "Invalid amount");
        }
        if (amount <= 0) {
            return new Response(400, "Amount must be a value greater than zero");
        }
        if (amount > MAX_DEPOSIT_AMOUNT) {
            return new Response(400, "Amount must be a value smaller than " + MAX_DEPOSIT_AMOUNT);
        }

        FileConfiguration config = plugin.getConfig();
        if (!config.getBoolean("counting-rewards.enabled")) {
            return new Response(403, "Deposits are currently disabled");
        }

        if (config.getStringList("counting-rewards.blocked-uuids").contains(uuid.toString())) {
            return new Response(403, "This uuid is blocked");
        }

        EconomyManager economy = plugin.getEconomyManager();
        EconomyResponse response = economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        if (response.type != EconomyResponse.ResponseType.SUCCESS) {
            return new Response(500, response.errorMessage);
        }

        if (config.getBoolean("counting-rewards.log-every-deposit", true)) {
            String name = plugin.getOfflinePlayersManager().getEffectiveNameById(uuid);
            Logs.info("[CountingRewards] Deposited " + economy.format(amount) + " into " + name + "'s account");
        }

        return new Response(200, "success");
    }

    private @Nullable String getString(@NotNull JSONObject json, @NotNull String path) {
        try {
            return json.getString(path);
        } catch (JSONException | NullPointerException ignored) {}
        return null;
    }
}
