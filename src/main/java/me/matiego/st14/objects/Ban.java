package me.matiego.st14.objects;

import lombok.Getter;
import lombok.Setter;
import me.matiego.st14.utils.Utils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public class Ban {
    public Ban(@NotNull UUID uuid) {
        this(uuid, null, 0);
    }
    public Ban(@NotNull UUID uuid, @Nullable String reason, long expiration) {
        this.uuid = uuid;
        setReason(reason);
        if (expiration < Utils.now()) {
            this.expiration = 0;
        } else {
            this.expiration = expiration;
        }
    }

    @Getter
    private final UUID uuid;
    @Getter
    private String reason;
    @Setter
    private long expiration;

    public boolean isActive() {
        return getExpiration() > 0;
    }

    public void setReason(String reason) {
        this.reason = reason == null || reason.isBlank() ? "[Brak powodu]" : reason;
    }

    public long getExpiration() {
        return expiration < Utils.now() ? 0 : expiration;
    }
}
