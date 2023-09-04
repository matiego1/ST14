package me.matiego.st14.managers;

import lombok.Getter;
import me.matiego.st14.objects.Ranking;
import me.matiego.st14.rankings.EconomyRanking;
import me.matiego.st14.rankings.MiniGameRanking;
import me.matiego.st14.rankings.TimeRanking;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

import java.util.List;
import java.util.UUID;

public class RankingsManager {
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
}
