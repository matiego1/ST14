package me.matiego.st14.rankings;

import me.matiego.st14.objects.rankings.Ranking;
import org.jetbrains.annotations.NotNull;

public class DeathsRanking extends Ranking {
    @Override
    protected @NotNull String getTableName() {
        return "st14_deaths";
    }

    @Override
    protected @NotNull String getColumnName() {
        return "deaths";
    }

    @Override
    public @NotNull String formatScore(long score) {
        return String.valueOf(score);
    }
}
