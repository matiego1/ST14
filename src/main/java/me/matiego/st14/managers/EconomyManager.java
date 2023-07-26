package me.matiego.st14.managers;

import lombok.Getter;
import lombok.Setter;
import me.matiego.st14.Main;
import me.matiego.st14.Logs;
import me.matiego.st14.utils.Utils;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class EconomyManager implements net.milkbowl.vault.economy.Economy {

    private final Main plugin;
    public EconomyManager(@NotNull Main plugin, boolean enabled) {
        this.plugin = plugin;
        this.enabled = enabled;
    }

    @Getter @Setter private boolean enabled;

    /**
     * Gets name of economy method
     *
     * @return Name of Economy Method
     */
    @Override
    public @NotNull String getName() {
        return "ST14_economy";
    }

    /**
     * Returns true if the given implementation supports banks.
     *
     * @return true if the implementation supports banks
     */
    @Override
    public boolean hasBankSupport() {
        return false;
    }

    /**
     * Some economy plugins round off after a certain number of digits.
     * This function returns the number of digits the plugin keeps
     * or -1 if no rounding occurs.
     *
     * @return number of digits after the decimal point kept
     */
    @Override
    public int fractionalDigits() {
        return 2;
    }

    /**
     * Format amount into a human-readable String This provides translation into
     * economy specific formatting to improve consistency between plugins.
     *
     * @param amount to format
     * @return Human-readable string describing amount
     */
    @Override
    public @NotNull String format(double amount) {
        return Utils.formatDouble(Utils.round(amount, 2)) + "$";
    }

    /**
     * Returns the name of the currency in plural form.
     * If the economy being used does not support currency names then an empty string will be returned.
     *
     * @return name of the currency (plural)
     */
    @Override
    public @NotNull String currencyNamePlural() {
        return "";
    }

    /**
     * Returns the name of the currency in singular form.
     * If the economy being used does not support currency names then an empty string will be returned.
     *
     * @return name of the currency (singular)
     */
    @Override
    public @NotNull String currencyNameSingular() {
        return "";
    }

    /**
     * @param playerName player name
     * @deprecated As of VaultAPI 1.4 use {@link #hasAccount(OfflinePlayer)} instead.
     */
    @Deprecated
    @Override
    public boolean hasAccount(@NotNull String playerName) {
        return true;
    }

    /**
     * Checks if this player has an account on the server yet
     * This will always return true if the player has joined the server at least once
     * as all major economy plugins auto-generate a player account when the player joins the server
     *
     * @param player to check
     * @return if the player has an account
     */
    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player) {
        return true;
    }

    /**
     * @param playerName player name
     * @param worldName world name
     * @deprecated As of VaultAPI 1.4 use {@link #hasAccount(OfflinePlayer, String)} instead.
     */
    @Deprecated
    @Override
    public boolean hasAccount(@NotNull String playerName, @NotNull String worldName) {
        return true;
    }

    /**
     * Checks if this player has an account on the server yet on the given world
     * This will always return true if the player has joined the server at least once
     * as all major economy plugins auto-generate a player account when the player joins the server
     *
     * @param player    to check in the world
     * @param worldName world-specific account
     * @return if the player has an account
     */
    @Override
    public boolean hasAccount(@NotNull OfflinePlayer player, @NotNull String worldName) {
        return true;
    }

    /**
     * @param playerName player name
     * @deprecated As of VaultAPI 1.4 use {@link #getBalance(OfflinePlayer)} instead.
     */
    @Deprecated
    @Override
    public double getBalance(@NotNull String playerName) {
        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
        if (uuid == null) return 0;
        return getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    /**
     * Gets balance of a player
     *
     * @param player of the player
     * @return Amount currently held in players account
     */
    @Override
    public double getBalance(@NotNull OfflinePlayer player) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT money FROM st14_economy WHERE uuid = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            ResultSet resultSet = stmt.executeQuery();
            if (!resultSet.next()) return 0;
            return resultSet.getDouble("money");
        } catch (SQLException e) {
            Logs.error("Could not retrieve player money.", e);
        }
        return 0;
    }

    /**
     * @param playerName player name
     * @param world world
     * @deprecated As of VaultAPI 1.4 use {@link #getBalance(OfflinePlayer, String)} instead.
     */
    @Deprecated
    @Override
    public double getBalance(@NotNull String playerName, @NotNull String world) {
        return getBalance(playerName);
    }

    /**
     * Gets balance of a player on the specified world.
     * IMPLEMENTATION SPECIFIC - if an economy plugin does not support this the global balance will be returned.
     *
     * @param player to check
     * @param world  name of the world
     * @return Amount currently held in players account
     */
    @Override
    public double getBalance(@NotNull OfflinePlayer player, @NotNull String world) {
        return getBalance(player);
    }

    public @NotNull EconomyResponse setBalance(@NotNull OfflinePlayer player, double amount) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_economy(uuid, money) VALUES(?, ?) ON DUPLICATE KEY UPDATE money = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, amount);
            stmt.setDouble(3, amount);
            if (stmt.executeUpdate() == 0) {
                return new EconomyResponse(0d, getBalance(player), EconomyResponse.ResponseType.FAILURE, "No funds on the account");
            }
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            Logs.error("Could not retrieve player money.", e);
        }
        return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, null);
    }

    /**
     * @param playerName player name
     * @param amount amount
     * @deprecated As of VaultAPI 1.4 use {@link #has(OfflinePlayer, double)} instead.
     */
    @Deprecated
    @Override
    public boolean has(@NotNull String playerName, double amount) {
        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
        if (uuid == null) return false;
        return has(Bukkit.getOfflinePlayer(uuid), amount);
    }

    /**
     * Checks if the player account has the amount - DO NOT USE NEGATIVE AMOUNTS
     *
     * @param player to check
     * @param amount to check for
     * @return True if <b>player</b> has <b>amount</b>, False else wise
     */
    @Override
    public boolean has(@NotNull OfflinePlayer player, double amount) {
        return amount <= getBalance(player);
    }

    /**
     * @param playerName player name
     * @param worldName world name
     * @param amount amount
     * @deprecated As of VaultAPI 1.4 use {@link #has(OfflinePlayer, String, double)} instead.
     */
    @Deprecated
    @Override
    public boolean has(@NotNull String playerName, @NotNull String worldName, double amount) {
        return has(playerName, amount);
    }

    /**
     * Checks if the player account has the amount in a given world - DO NOT USE NEGATIVE AMOUNTS
     * IMPLEMENTATION SPECIFIC - if an economy plugin does not support this the global balance will be returned.
     *
     * @param player    to check
     * @param worldName to check with
     * @param amount    to check for
     * @return True if <b>player</b> has <b>amount</b>, False else wise
     */
    @Override
    public boolean has(@NotNull OfflinePlayer player, @NotNull String worldName, double amount) {
        return has(player, amount);
    }

    /**
     * @param playerName player name
     * @param amount amount
     * @deprecated As of VaultAPI 1.4 use {@link #withdrawPlayer(OfflinePlayer, double)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse withdrawPlayer(@NotNull String playerName, double amount) {
        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
        if (uuid == null) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, null);
        return withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
    }

    /**
     * Withdraw an amount from a player - DO NOT USE NEGATIVE AMOUNTS
     *
     * @param player to withdraw from
     * @param amount Amount to withdraw
     * @return Detailed response of transaction
     */
    @Override
    public @NotNull EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, double amount) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE st14_economy SET money = money - ? WHERE uuid = ? AND money >= ?")) {
            stmt.setDouble(1, amount);
            stmt.setString(2, player.getUniqueId().toString());
            stmt.setDouble(3, amount);
            if (stmt.executeUpdate() == 0) {
                return new EconomyResponse(0d, getBalance(player), EconomyResponse.ResponseType.FAILURE, "No funds on the account");
            }
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            Logs.error("Could not retrieve player money.", e);
        }
        return new EconomyResponse(0, getBalance(player), EconomyResponse.ResponseType.FAILURE, null);
    }

    /**
     * @param playerName player name
     * @param worldName world name
     * @param amount amount
     * @deprecated As of VaultAPI 1.4 use {@link #withdrawPlayer(OfflinePlayer, String, double)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse withdrawPlayer(@NotNull String playerName, @NotNull String worldName, double amount) {
        return withdrawPlayer(playerName, amount);
    }

    /**
     * Withdraw an amount from a player on a given world - DO NOT USE NEGATIVE AMOUNTS
     * IMPLEMENTATION SPECIFIC - if an economy plugin does not support this the global balance will be returned.
     *
     * @param player    to withdraw from
     * @param worldName - name of the world
     * @param amount    Amount to withdraw
     * @return Detailed response of transaction
     */
    @Override
    public @NotNull EconomyResponse withdrawPlayer(@NotNull OfflinePlayer player, @NotNull String worldName, double amount) {
        return withdrawPlayer(player, amount);
    }

    /**
     * @param playerName player name
     * @param amount amount
     * @deprecated As of VaultAPI 1.4 use {@link #depositPlayer(OfflinePlayer, double)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse depositPlayer(@NotNull String playerName, double amount) {
        UUID uuid = plugin.getOfflinePlayersManager().getIdByName(playerName);
        if (uuid == null) return new EconomyResponse(0, 0, EconomyResponse.ResponseType.FAILURE, null);
        return depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
    }

    /**
     * Deposit an amount to a player - DO NOT USE NEGATIVE AMOUNTS
     *
     * @param player to deposit to
     * @param amount Amount to deposit
     * @return Detailed response of transaction
     */
    @Override
    public @NotNull EconomyResponse depositPlayer(@NotNull OfflinePlayer player, double amount) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_economy(uuid, money) VALUES(?, ?) ON DUPLICATE KEY UPDATE money = money + ?;")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setDouble(2, amount);
            stmt.setDouble(3, amount);
            stmt.execute();
            return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.SUCCESS, null);
        } catch (SQLException e) {
            Logs.error("Could not retrieve player money.", e);
        }
        return new EconomyResponse(amount, getBalance(player), EconomyResponse.ResponseType.FAILURE, null);
    }

    /**
     * @param playerName player name
     * @param worldName world name
     * @param amount amount
     * @deprecated As of VaultAPI 1.4 use {@link #depositPlayer(OfflinePlayer, String, double)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse depositPlayer(@NotNull String playerName, @NotNull String worldName, double amount) {
        return depositPlayer(playerName, amount);
    }

    /**
     * Deposit an amount to a player - DO NOT USE NEGATIVE AMOUNTS
     * IMPLEMENTATION SPECIFIC - if an economy plugin does not support this the global balance will be returned.
     *
     * @param player    to deposit to
     * @param worldName name of the world
     * @param amount    Amount to deposit
     * @return Detailed response of transaction
     */
    @Override
    public @NotNull EconomyResponse depositPlayer(@NotNull OfflinePlayer player, @NotNull String worldName, double amount) {
        return depositPlayer(player, amount);
    }

    /**
     * @param name name
     * @param player player
     * @deprecated As of VaultAPI 1.4 use {{@link #createBank(String, OfflinePlayer)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse createBank(@NotNull String name, @NotNull String player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Creates a bank account with the specified name and the player as the owner
     *
     * @param name   of account
     * @param player the account should be linked to
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse createBank(@NotNull String name, @NotNull OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Deletes a bank account with the specified name.
     *
     * @param name of the back to delete
     * @return if the operation completed successfully
     */
    @Override
    public @NotNull EconomyResponse deleteBank(@NotNull String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Returns the amount the bank has
     *
     * @param name of the account
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse bankBalance(@NotNull String name) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Returns true or false whether the bank has the amount specified - DO NOT USE NEGATIVE AMOUNTS
     *
     * @param name   of the account
     * @param amount to check for
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse bankHas(@NotNull String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Withdraw an amount from a bank account - DO NOT USE NEGATIVE AMOUNTS
     *
     * @param name   of the account
     * @param amount to withdraw
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse bankWithdraw(@NotNull String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Deposit an amount into a bank account - DO NOT USE NEGATIVE AMOUNTS
     *
     * @param name   of the account
     * @param amount to deposit
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse bankDeposit(@NotNull String name, double amount) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * @param name name
     * @param playerName player name
     * @deprecated As of VaultAPI 1.4 use {{@link #isBankOwner(String, OfflinePlayer)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse isBankOwner(@NotNull String name, @NotNull String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Check if a player is the owner of a bank account
     *
     * @param name   of the account
     * @param player to check for ownership
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse isBankOwner(@NotNull String name, @NotNull OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * @param name name
     * @param playerName player name
     * @deprecated As of VaultAPI 1.4 use {{@link #isBankMember(String, OfflinePlayer)} instead.
     */
    @Deprecated
    @Override
    public @NotNull EconomyResponse isBankMember(@NotNull String name, @NotNull String playerName) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Check if the player is a member of the bank account
     *
     * @param name   of the account
     * @param player to check membership
     * @return EconomyResponse Object
     */
    @Override
    public @NotNull EconomyResponse isBankMember(@NotNull String name, @NotNull OfflinePlayer player) {
        return new EconomyResponse(0, 0, EconomyResponse.ResponseType.NOT_IMPLEMENTED, null);
    }

    /**
     * Gets the list of banks
     *
     * @return the List of Banks
     */
    @Override
    public @NotNull List<String> getBanks() {
        return new ArrayList<>();
    }

    /**
     * @param playerName player name
     * @deprecated As of VaultAPI 1.4 use {{@link #createPlayerAccount(OfflinePlayer)} instead.
     */
    @Deprecated
    @Override
    public boolean createPlayerAccount(@NotNull String playerName) {
        return true;
    }

    /**
     * Attempts to create a player account for the given player
     *
     * @param player OfflinePlayer
     * @return if the account creation was successful
     */
    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player) {
        return true;
    }

    /**
     * @param playerName player name
     * @param worldName world name
     * @deprecated As of VaultAPI 1.4 use {{@link #createPlayerAccount(OfflinePlayer, String)} instead.
     */
    @Deprecated
    @Override
    public boolean createPlayerAccount(@NotNull String playerName, @NotNull String worldName) {
        return true;
    }

    /**
     * Attempts to create a player account for the given player on the specified world
     * IMPLEMENTATION SPECIFIC - if an economy plugin does not support this then false will always be returned.
     *
     * @param player    OfflinePlayer
     * @param worldName String name of the world
     * @return if the account creation was successful
     */
    @Override
    public boolean createPlayerAccount(@NotNull OfflinePlayer player, @NotNull String worldName) {
        return true;
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_economy(uuid VARCHAR(36) NOT NULL, money DECIMAL(12, 2) NOT NULL, PRIMARY KEY (uuid))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_economy\"", e);
        }
        return false;
    }
}
