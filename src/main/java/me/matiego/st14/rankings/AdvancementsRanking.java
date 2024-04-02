package me.matiego.st14.rankings;

import me.matiego.st14.objects.rankings.Ranking;
import org.jetbrains.annotations.NotNull;

public class AdvancementsRanking extends Ranking {
    @Override
    protected @NotNull String getTableName() {
        return "st14_advancements";
    }

    @Override
    protected @NotNull String getColumnName() {
        return "amount";
    }

    @Override
    public @NotNull String formatScore(long score) {
        return String.valueOf(score);
    }
}
