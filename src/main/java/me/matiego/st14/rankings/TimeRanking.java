package me.matiego.st14.rankings;

import me.matiego.st14.objects.rankings.Ranking;
import me.matiego.st14.utils.Utils;
import org.jetbrains.annotations.NotNull;

public class TimeRanking extends Ranking {
    @Override
    protected @NotNull String getTableName() {
        return "st14_time";
    }

    @Override
    protected @NotNull String getColumnName() {
        return "(t_normal + t_afk)";
    }

    @Override
    protected @NotNull String getXColumnName() {
        return "(x.t_normal + x.t_afk)";
    }

    @Override
    public @NotNull String formatScore(long score) {
        return Utils.parseMillisToString(score, false);
    }
}
