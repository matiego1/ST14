package me.matiego.st14;

import me.matiego.st14.utils.Logs;
import me.matiego.st14.utils.Pair;
import me.matiego.st14.utils.Prefixes;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HashMap;
import java.util.UUID;

public class AccountsManager {
    private final Main plugin;
    public AccountsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_accounts\" table in the database.";
    private final String CODE_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final HashMap<String, Pair<UUID, Long>> verificationCodes = new HashMap<>();

    public synchronized @NotNull String getNewVerificationCode(@NotNull UUID uuid) {
        String code = RandomStringUtils.random(6, CODE_CHARS);
        int x = 0;
        while (verificationCodes.get(code) != null) {
            code = RandomStringUtils.random(6, CODE_CHARS);
            if (x++ > 100) throw new RuntimeException("infinite loop");
        }
        verificationCodes.entrySet().removeIf(e -> e.getValue().getFirst().equals(uuid));
        verificationCodes.put(code, new Pair<>(uuid, Utils.now()));
        return code;
    }

    public synchronized @Nullable UUID checkVerificationCode(@NotNull String code) {
        Pair<UUID, Long> pair = verificationCodes.remove(code);
        if (pair == null) return null;
        if (Utils.now() - pair.getSecond() > 300_000) return null;
        return pair.getFirst();
    }

    public boolean isRequired(@NotNull UUID uuid) {
        return plugin.getConfig().getBoolean("discord.linking-required") && !plugin.getConfig().getStringList("discord.linking-required-bypass").contains(uuid.toString());
    }

    public @Nullable UserSnowflake getUserByPlayer(@NotNull UUID uuid) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT id FROM st14_accounts WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            ResultSet result = stmt.executeQuery();
            if (result.next()) return UserSnowflake.fromId(result.getString("id"));
        } catch (SQLException | IllegalArgumentException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public @Nullable UUID getPlayerByUser(@NotNull UserSnowflake id) {
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM st14_accounts WHERE id = ?")) {
            stmt.setString(1, id.getId());
            ResultSet result = stmt.executeQuery();
            if (result.next()) return UUID.fromString(result.getString("uuid"));
        } catch (SQLException | IllegalArgumentException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public boolean isLinked(@NotNull UUID uuid) {
        return getUserByPlayer(uuid) != null;
    }

    public boolean isLinked(@NotNull UserSnowflake id) {
        return getPlayerByUser(id) != null;
    }

    public boolean link(@NotNull UUID uuid, @NotNull UserSnowflake id) {
        if (!modifyRole(id, true)) return false;
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_accounts(uuid, id) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = ?, id = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, id.getId());
            stmt.setString(3, uuid.toString());
            stmt.setString(4, id.getId());
            if (!(stmt.executeUpdate() > 0)) return false;

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Utils.getComponentByString(Prefixes.DISCORD + "Pomyślnie połączono to konto z kontem Discord!"));
            }

            String playerName = plugin.getOfflinePlayers().getEffectiveNameById(uuid);
            JDA jda = plugin.getJda();
            if (jda == null) {
                logLink(playerName, id.getId());
            } else {
                jda.retrieveUserById(id.getId()).queue(user -> logLink(playerName, user == null ? id.getId() : user.getAsTag()));
            }
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    private void logLink(@NotNull String player, @NotNull String user) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTimestamp(Instant.now());
        eb.setColor(Color.BLUE);
        eb.setFooter("Information");
        eb.setDescription("`" + player + "` has linked his account with Discord account `" + user + "`");
        Logs.discord(eb.build());
        Logs.info(player + " has linked his account with Discord account " + user);
    }

    public boolean unlink(@NotNull UUID uuid) {
        UserSnowflake id = getUserByPlayer(uuid);
        if (id != null && !modifyRole(id, false)) return false;
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_accounts WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            if (stmt.executeUpdate() > 0) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && isRequired(uuid)) Utils.sync(() -> player.kick(Utils.getComponentByString(Prefixes.DISCORD + "Twoje konto zostało rozłączone z kontem Discord!")));
                logUnlink(plugin.getOfflinePlayers().getEffectiveNameById(uuid));
                return true;
            }
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    private void logUnlink(@NotNull String player) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTimestamp(Instant.now());
        eb.setColor(Color.BLUE);
        eb.setFooter("Information");
        eb.setDescription("`" + player + "` has unlinked his account.");
        Logs.discord(eb.build());
        Logs.info(player + " has unlinked his account.");
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_accounts(uuid VARCHAR(36) NOT NULL, id BIGINT NOT NULL, PRIMARY KEY (uuid), UNIQUE KEY (id))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_offline_players\"", e);
        }
        return false;
    }

    private boolean modifyRole(@NotNull UserSnowflake id, boolean add) {
        JDA jda = plugin.getJda();
        if (jda == null) return false;

        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) {
            Logs.warning("A guild id in the config file is not correct.");
            return false;
        }

        Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-id"));
        if (role == null) {
            Logs.warning("A role id in the config file is not correct.");
            return false;
        }

        if (add) {
            guild.addRoleToMember(id, role).queue();
        } else {
            guild.removeRoleFromMember(id, role).queue();
        }
        return true;
    }
}
