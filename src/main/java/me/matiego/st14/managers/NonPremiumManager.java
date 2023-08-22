package me.matiego.st14.managers;

import me.matiego.st14.Main;
import me.matiego.st14.objects.Pair;
import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.NonPremiumUtils;
import me.matiego.st14.utils.Utils;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import org.apache.commons.lang3.RandomStringUtils;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class NonPremiumManager {
    public NonPremiumManager(@NotNull Main plugin) {
        this.plugin = plugin;
    }

    private final Main plugin;
    private final HashMap<String, Pair<UUID, Long>> verificationCode = new HashMap<>();
    private final HashMap<String, Pair<UUID, String>> playerName = new HashMap<>();
    private final Set<UUID> loggedIn = new HashSet<>();

    public synchronized @Nullable String startLogin(@NotNull Member member, @NotNull String name) {
        if (!DiscordUtils.hasRole(member, plugin.getConfig().getLong("discord.role-ids.non-premium"))) return null;
        UUID uuid = NonPremiumUtils.createNonPremiumUuid(member);

        User user = member.getUser();
        //noinspection deprecation
        if (!user.getDiscriminator().equals("0000")) return null;

        for (Map.Entry<String, Pair<UUID, String>> stringPairEntry : playerName.entrySet()) {
            Pair<UUID, String> pair = stringPairEntry.getValue();
            if (pair.getSecond().equals(user.getName())) {
                endSession(pair.getFirst(), "Zmiana nicku na Discord");
            }
        }

        if (playerName.containsKey(name)) return null;
        playerName.put(name, new Pair<>(uuid, user.getName()));

        String code = RandomStringUtils.randomAlphanumeric(10);
        int x = 0;
        while (verificationCode.get(code) != null) {
            code = RandomStringUtils.randomAlphanumeric(10);
            if (x++ > 5000) return null; //infinite loop
        }

        verificationCode.put(code, new Pair<>(uuid, Utils.now()));
        return code;
    }

    public synchronized boolean isNameUnique(@NotNull String name) {
        return playerName.containsKey(name);
    }

    public synchronized boolean checkVerificationCode(@NotNull Player player, @NotNull String code) {
        Pair<UUID, Long> pair = verificationCode.get(code);
        if (pair == null) return false;
        if (!pair.getFirst().equals(player.getUniqueId())) return false;
        return Utils.now() - pair.getSecond() <= 5 * 60 * 1000;
    }

    public synchronized boolean isLoggedIn(@NotNull UUID uuid) {
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return true; //other players are logged in by default
        return loggedIn.contains(uuid);
    }

    public synchronized void logIn(@NotNull Player player) {
        if (!NonPremiumUtils.isNonPremiumUuid(player.getUniqueId())) return;
        loggedIn.add(player.getUniqueId());
        verificationCode.entrySet().removeIf(pair -> pair.getValue().getFirst().equals(player.getUniqueId()));
    }

    public synchronized void endSession(@NotNull UUID uuid, @NotNull String reason) {
        if (!NonPremiumUtils.isNonPremiumUuid(uuid)) return;
        clearDataAfterSession(uuid);
        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            player.kick(Utils.getComponentByString("&cTwoja sesja została zakończona!\nPowód: " + reason));
        }
    }

    public synchronized void clearDataAfterSession(@NotNull UUID uuid) {
        loggedIn.remove(uuid);
        playerName.entrySet().removeIf(e -> e.getValue().getFirst().equals(uuid));
        verificationCode.entrySet().removeIf(pair -> pair.getValue().getFirst().equals(uuid));
    }
}
