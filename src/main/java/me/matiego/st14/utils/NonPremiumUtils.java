package me.matiego.st14.utils;

import net.dv8tion.jda.api.entities.UserSnowflake;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

public class NonPremiumUtils {
    //TODO floodgate uses the same value
    private static final long MOST_SIGNIFICANT_BITS = 0;
    public static boolean isNonPremiumUuid(@NotNull UUID uuid) {
        return uuid.getMostSignificantBits() == MOST_SIGNIFICANT_BITS;
    }

    public static @NotNull UUID createNonPremiumUuid(@NotNull UserSnowflake id) {
        return new UUID(MOST_SIGNIFICANT_BITS, id.getIdLong());
    }

    public static long getIdByNonPremiumUuid(@NotNull UUID uuid) {
        if (!isNonPremiumUuid(uuid)) throw new IllegalArgumentException("this uuid is not non-premium uuid");
        return uuid.getLeastSignificantBits();
    }
}
