package me.matiego.st14.objects;

import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.RankingsManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class Ranking {
    private final String ERROR_MSG = "An error occurred while modifying values in \"" + getTableName() + "\" table in the database.";

    protected abstract @NotNull String getTableName();
    protected abstract @NotNull String getColumnName();
    public abstract @NotNull String formatScore(long score);

    protected @NotNull String getXColumnName() {
        return "x." + getColumnName();
    }

    public @Nullable RankingsManager.Data get(@NotNull UUID uuid) {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT " + getColumnName() + " AS ranking_score, (SELECT 1 + COUNT(*) FROM " + getTableName() + " WHERE " + getColumnName() + " > " + getXColumnName() + ") AS ranking_position FROM " + getTableName() + " x WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());

            ResultSet result = stmt.executeQuery();
            if (result.next()) {
                long score = result.getLong("ranking_score");
                int rank = result.getInt("ranking_position");
                if (rank == 0) return null;
                return new RankingsManager.Data(uuid, score, rank);
            }
            return null;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public @NotNull List<RankingsManager.Data> getTop(@Range(from = 1, to = Integer.MAX_VALUE) int amount) {
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, " + getColumnName() + " AS ranking_score, RANK() OVER(ORDER BY " + getColumnName() + " DESC) ranking_position FROM " + getTableName())) {

            ResultSet resultSet = stmt.executeQuery();
            List<RankingsManager.Data> result = new ArrayList<>();
            while (resultSet.next()) {
                int rank = resultSet.getInt("ranking_position");
                if (rank > amount) return result;
                result.add(new RankingsManager.Data(
                        UUID.fromString(resultSet.getString("uuid")),
                        resultSet.getLong("ranking_score"),
                        rank
                ));
            }
            return result;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return new ArrayList<>();
    }
}
