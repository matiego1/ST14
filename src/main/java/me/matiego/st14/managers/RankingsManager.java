package me.matiego.st14.managers;

import lombok.Getter;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.objects.Ranking;
import me.matiego.st14.rankings.EconomyRanking;
import me.matiego.st14.rankings.MiniGameRanking;
import me.matiego.st14.rankings.TimeRanking;
import me.matiego.st14.utils.DiscordUtils;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.awt.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class RankingsManager {
    public RankingsManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }
    private final Main plugin;
    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_ranking_messages\" table in the database.";
    private BukkitTask task;

    public boolean addRankingMessage(@NotNull Type type, long messageId, long channelId) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_ranking_messages(msg, chn, type) VALUES (?, ?, ?)")) {
            stmt.setLong(1, messageId);
            stmt.setLong(2, channelId);
            stmt.setString(3, type.toString());

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    private @NotNull List<Pair<Long, Long>> getRankingMessagesByType(@NotNull Type type) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT msg, chn FROM st14_ranking_messages WHERE type = ?")) {
            stmt.setString(1, type.toString());

            List<Pair<Long, Long>> result = new ArrayList<>();
            ResultSet resultSet = stmt.executeQuery();
            while (resultSet.next()) {
                result.add(new Pair<>(resultSet.getLong("msg"), resultSet.getLong("chn")));
            }
            return result;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return new ArrayList<>();
    }
    
    private void removeRankingMessage(@NotNull Type type, long messageId, long channelId) {
        try (Connection conn = plugin.getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_ranking_messages WHERE msg = ? AND chn = ? AND type = ?")) {
            stmt.setLong(1, messageId);
            stmt.setLong(2, channelId);
            stmt.setString(3, type.toString());

            stmt.execute();
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
    }

    public void start() {
        stop();
        int interval = Math.max(60, plugin.getConfig().getInt("ranking-messages-update-interval", 300));
        task = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            JDA jda = plugin.getJda();
            if (jda == null) return;

            for (Type type : Type.values()) {
                EmbedBuilder eb = getEmbed(type, 50);
                if (eb == null) continue;
                MessageEmbed embed = eb.build();

                for (Pair<Long, Long> data : getRankingMessagesByType(type)) {
                    TextChannel chn = jda.getTextChannelById(data.getSecond());
                    if (chn == null) {
                        removeRankingMessage(type, data.getFirst(), data.getSecond());
                        continue;
                    }
                    try {
                        chn.retrieveMessageById(data.getFirst()).queue(
                                message -> {
                                    List<MessageEmbed> embeds = message.getEmbeds();
                                    String description = String.valueOf(embed.getDescription());
                                    if (embeds.isEmpty() || !String.valueOf(embeds.get(0).getDescription()).equalsIgnoreCase(description.substring(0, description.length() - 1))) {
                                        message.editMessageEmbeds(eb.build()).queue();
                                    }
                                },
                                failure -> {
                                    if (failure instanceof ErrorResponseException e && e.getErrorResponse() == ErrorResponse.UNKNOWN_MESSAGE) {
                                        removeRankingMessage(type, data.getFirst(), data.getSecond());
                                    } else {
                                        Logs.error("An error occurred while updating ranking message", failure);
                                    }
                                }
                        );
                    } catch (Exception ignored) {}
                }
            }
        }, 100L, interval * 20L);
    }

    public @Nullable EmbedBuilder getEmbed(@NotNull Type type, int amount) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("**Ranking " + type.getRankingName() + "**");
        eb.setTimestamp(Instant.now());
        eb.setFooter("Ostatnia aktualizacja");
        eb.setColor(Color.YELLOW);

        List<Data> top = type.getTop(amount);
        if (top.isEmpty()) return null;

        StringBuilder builder = new StringBuilder();
        for (Data data : top) {
            String place = switch (data.getRank()) {
                case 1 -> ":first_place:";
                case 2 -> ":second_place:";
                case 3 -> ":third_place:";
                default -> "**" + data.getRank() + ".**";
            };

            builder
                    .append(place)
                    .append(" ")
                    .append(plugin.getOfflinePlayersManager().getEffectiveNameById(data.getUuid()))
                    .append(" - `")
                    .append(type.formatScore(data.getScore()))
                    .append("`\n");
        }

        String description = DiscordUtils.checkLength(builder.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH);
        if (description.endsWith("...")) {
            description = description.substring(0, description.lastIndexOf("\n") + 1) + "...";
        }
        eb.setDescription(description);

        return eb;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public static class Data {
        public Data(@NotNull UUID uuid, long score, int rank) {
            this.uuid = uuid;
            this.score = score;
            this.rank = rank;
        }
        @Getter
        private final UUID uuid;
        @Getter
        private final long score;
        @Getter
        private final int rank;
    }

    public enum Type {
        ECONOMY(new EconomyRanking(), "ekonomii"),
        MINIGAME(new MiniGameRanking(), "wygranych minigier"),
        TIME(new TimeRanking(), "czasu gry");

        Type(@NotNull Ranking instance, @NotNull String rankingName) {
            this.instance = instance;
            this.rankingName = rankingName;
        }

        private final Ranking instance;
        private final String rankingName;

        public @Nullable RankingsManager.Data get(@NotNull UUID uuid) {
            return instance.get(uuid);
        }

        public @NotNull List<Data> getTop(@Range(from = 1, to = Integer.MAX_VALUE) int amount) {
            return instance.getTop(amount);
        }

        public @NotNull String formatScore(long score) {
            return instance.formatScore(score);
        }

        public @NotNull String getRankingName() {
            return rankingName;
        }

        public static @Nullable Type getByName(@NotNull String name) {
            for (Type type : values()) {
                if (type.toString().equalsIgnoreCase(name)) {
                    return type;
                }
            }
            return null;
        }
    }

    public static boolean createTable() {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS st14_ranking_messages(msg BIGINT NOT NULL, chn BIGINT NOT NULL, type VARCHAR(30) NOT NULL, CONSTRAINT st14_ranking_messages_const UNIQUE (msg, chn))")) {
            stmt.execute();
            return true;
        } catch (SQLException e) {
            Logs.error("An error occurred while creating the database table \"st14_ranking_messages\"", e);
        }
        return false;
    }
}
