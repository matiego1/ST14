package me.matiego.st14;

import me.matiego.st14.utils.DiscordUtils;
import me.matiego.st14.utils.Logs;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class Updates {

    public @NotNull HashMap<Plugin, Response> checkSpigotMc(@NotNull List<Plugin> plugins) {
        HashMap<Plugin, Response> result = new HashMap<>();
        for (Plugin plugin : plugins) {
            result.put(plugin, checkSpigotMc(plugin));
        }
        return result;
    }

    public @NotNull Response checkSpigotMc(@NotNull Plugin plugin) {
        int id = Main.getInstance().getConfig().getInt("plugins-resource-id." + plugin.getName(), -1);
        if (id == -1) return Response.UNKNOWN_ID;
        try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + id).openStream();
             Scanner scanner = new Scanner(inputStream)) {
            if (!scanner.hasNext()) return Response.FAILURE;
            return compareVersions(plugin.getDescription().getVersion(), scanner.next());
        } catch (IOException e) {
            return Response.FAILURE;
        }
    }

    private @NotNull Response compareVersions(@NotNull String a, @NotNull String b) {
        List<Integer> aList = null;
        List<Integer> bList = null;

        try {
            aList = Arrays.stream(a.split("\\.")).map(Integer::parseInt).collect(Collectors.toList());
            bList = Arrays.stream(b.split("\\.")).map(Integer::parseInt).collect(Collectors.toList());
        } catch (NumberFormatException ignored) {}

        if (aList == null || bList == null || aList.size() != bList.size()) {
            return a.equalsIgnoreCase(b) ? Response.UP_TO_DATE : Response.CANNOT_PARSE;
        }

        for (int i = 0; i < aList.size(); i++) {
            int ai = aList.get(i);
            int bi = bList.get(i);
            if (ai > bi) return Response.NEWER;
            if (ai < bi) return Response.OLDER;
        }
        return Response.UP_TO_DATE;
    }

    public void log(@NotNull HashMap<Plugin, Response> plugins) {
        for (Map.Entry<Plugin, Response> e : plugins.entrySet()) {
            Logs.info("Plugin " + e.getKey().getName() + " -> " + e.getValue().toString());
        }
    }

    public void logDiscord(@NotNull HashMap<Plugin, Response> plugins) {
        EmbedBuilder eb = new EmbedBuilder();
        eb.setTitle("Informacja o wersjach pluginów:");
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<Plugin, Response> e : plugins.entrySet()) {
            builder.append("`").append(e.getKey().getName()).append("` -> ").append(e.getValue().toString()).append("\n");
        }
        if (builder.toString().isBlank()) return;
        eb.setDescription(DiscordUtils.checkLength(builder.toString(), MessageEmbed.DESCRIPTION_MAX_LENGTH));
        eb.setColor(Color.PINK);
        eb.setTimestamp(Instant.now());
        Logs.discord(eb.build());
    }

    public enum Response {
        UNKNOWN_ID("Brak ID pluginu"),
        CANNOT_PARSE("Błąd przy porównywaniu wersji"),
        OLDER("Dostępna nowsza wersja pluginu"),
        UP_TO_DATE("Plugin aktualny"),
        NEWER("Plugin aktualny"),
        FAILURE("Błąd przy wczytywaniu wersji");

        private final String string;
        Response(@NotNull String string) {
            this.string = string;
        }
        @Override
        public String toString() {
            return string;
        }
    }
}
