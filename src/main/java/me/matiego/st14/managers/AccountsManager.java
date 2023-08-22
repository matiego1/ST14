package me.matiego.st14.managers;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.Prefix;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.utils.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.HierarchyException;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.UUID;

public class AccountsManager {
    private final Main plugin;
    public AccountsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_accounts\" table in the database.";
    @SuppressWarnings("SpellCheckingInspection")
    private final String CODE_CHARS = "ABCDEFGHIJKLMNPQRSTUVWXYZ123456789";
    private final HashMap<String, Pair<Pair<UUID, String>, Long>> verificationCodes = new HashMap<>();

    public synchronized @NotNull String getNewVerificationCode(@NotNull UUID uuid, @NotNull String name) {
        String code = RandomStringUtils.random(6, CODE_CHARS);
        int x = 0;
        while (verificationCodes.get(code) != null) {
            code = RandomStringUtils.random(6, CODE_CHARS);
            if (x++ > 5000) throw new RuntimeException("infinite loop");
        }
        verificationCodes.entrySet().removeIf(e -> e.getValue().getFirst().getFirst().equals(uuid));
        verificationCodes.put(code, new Pair<>(new Pair<>(uuid, name), Utils.now()));
        return code;
    }

    public synchronized @Nullable Pair<UUID, String> checkVerificationCode(@NotNull String code) {
        Pair<Pair<UUID, String>, Long> pair = verificationCodes.remove(code);
        if (pair == null) return null;
        if (Utils.now() - pair.getSecond() > 300_000) return null;
        return pair.getFirst();
    }

    public boolean isRequired(@NotNull UUID uuid) {
        if (NonPremiumUtils.isNonPremiumUuid(uuid)) return false;
        return plugin.getConfig().getBoolean("discord.linking-required") && !plugin.getConfig().getStringList("discord.linking-required-bypass").contains(uuid.toString());
    }

    public @Nullable UserSnowflake getUserByPlayer(@NotNull UUID uuid) {
        if (NonPremiumUtils.isNonPremiumUuid(uuid)) {
            return UserSnowflake.fromId(NonPremiumUtils.getIdByNonPremiumUuid(uuid));
        }
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
        if (NonPremiumUtils.isNonPremiumUuid(uuid)) return true;
        return getUserByPlayer(uuid) != null;
    }

    public boolean isLinked(@NotNull UserSnowflake id) {
        return getPlayerByUser(id) != null;
    }

    public boolean link(@NotNull UUID uuid, @NotNull UserSnowflake id) {
        if (NonPremiumUtils.isNonPremiumUuid(uuid) && NonPremiumUtils.getIdByNonPremiumUuid(uuid) != id.getIdLong()) {
            throw new IllegalArgumentException("tried to link a non-premium uuid to another Discord account");
        }
        if (!checkRoles(id)) return false;
        if (!modifyRole(id, true)) return false;
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_accounts(uuid, id) VALUES (?, ?) ON DUPLICATE KEY UPDATE uuid = ?, id = ?")) {
            stmt.setString(1, uuid.toString());
            stmt.setString(2, id.getId());
            stmt.setString(3, uuid.toString());
            stmt.setString(4, id.getId());
            if (stmt.executeUpdate() == 0) return false;

            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                player.sendMessage(Utils.getComponentByString(Prefix.DISCORD + "Pomyślnie połączono to konto z kontem Discord!"));
            }

            String playerName = plugin.getOfflinePlayersManager().getEffectiveNameById(uuid);
            JDA jda = plugin.getJda();
            if (jda == null) {
                Logs.info(playerName + " has linked his account with Discord account " + id.getId());
            } else {
                jda.retrieveUserById(id.getId()).queue(
                        user -> Logs.info(playerName + " has linked his account with Discord account " + DiscordUtils.getAsTag(user)),
                        failure -> Logs.info(playerName + " has linked his account with Discord account " + id.getId())
                );
            }
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    public boolean unlink(@NotNull UUID uuid) {
        if (NonPremiumUtils.isNonPremiumUuid(uuid)) return false;
        UserSnowflake id = getUserByPlayer(uuid);
        if (id != null) {
            modifyRole(id, false);
            modifyNickname(id, null);
        }
        try (Connection conn = plugin.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_accounts WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            if (stmt.executeUpdate() > 0) {
                Player player = Bukkit.getPlayer(uuid);
                if (player != null && isRequired(uuid)) Utils.sync(() -> player.kick(Utils.getComponentByString(Prefix.DISCORD + "Twoje konto zostało rozłączone z kontem Discord!")));
                Logs.info(plugin.getOfflinePlayersManager().getEffectiveNameById(uuid) + " has unlinked his account.");
                return true;
            }
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
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

    private boolean checkRoles(@NotNull UserSnowflake id) {
        JDA jda = plugin.getJda();
        if (jda == null) return false;

        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) return false;

        Member member = DiscordUtils.retrieveMember(guild, id);
        if (member == null) return false;

        if (DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.banned"))) return false;
        return DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.verified"));
    }

    private boolean modifyRole(@NotNull UserSnowflake id, boolean add) {
        JDA jda = plugin.getJda();
        if (jda == null) return false;

        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) {
            Logs.warning("A guild id in the config file is not correct.");
            return false;
        }

        Role role = guild.getRoleById(plugin.getConfig().getLong("discord.role-ids.player"));
        if (role == null) {
            Logs.warning("A player-role-id in the config file is not correct.");
            return false;
        }

        try {
            if (add) {
                guild.addRoleToMember(id, role).queue();
            } else {
                guild.removeRoleFromMember(id, role).queue();
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    public void modifyNickname(@NotNull UserSnowflake id, @Nullable String nickname) {
        JDA jda = plugin.getJda();
        if (jda == null) return;
        Guild guild = jda.getGuildById(plugin.getConfig().getLong("discord.guild-id"));
        if (guild == null) return;
        guild.retrieveMember(id).queue(member -> {
            try {
                member.modifyNickname(nickname).queue(
                        success -> {},
                        failure -> Logs.warning("An error occurred while modifying the nickname of user " + DiscordUtils.getAsTag(member))
                );
            } catch (HierarchyException ignored) {
            } catch (Exception e) {
                Logs.warning("An error occurred while modifying the nickname of user " + DiscordUtils.getAsTag(member));
            }
        }, failure -> {});
    }
}
