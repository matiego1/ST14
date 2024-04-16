package me.matiego.st14.objects.heads;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.matiego.st14.Logs;
import me.matiego.st14.Main;
import me.matiego.st14.managers.HeadsManager;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public enum HeadsCategory {
    ALPHABET("alphabet", "Alfabet"),
    ANIMALS("animals", "Zwierzęta"),
    BLOCKS("blocks", "Bloki"),
    DECORATION("decoration", "Dekoracyjne"),
    FOOD_DRINKS("food-drinks", "Jedzenie"),
    HUMANS("humans", "Człowiek"),
    HUMANOID("humanoid", "Humanoid"),
    MISCELLANEOUS("miscellaneous", "Rożne"),
    MONSTERS("monsters", "Potwory"),
    PLANTS("plants", "Rośliny");

    HeadsCategory(@NotNull String apiName, @NotNull String name) {
        this.apiName = apiName;
        this.name = name;
    }

    private final String ERROR_MSG = "An error occurred while modifying values in \"st14_heads\" table in the database.";
    private final String apiName;
    private final String name;
    private ItemStack guiHead = null;

    public @Nullable List<Head> getHeads() {
        HeadsManager manager = Main.getInstance().getHeadsManager();
        if (manager == null || !manager.isAvailable()) return null;
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, name, value, tags FROM st14_heads WHERE category = ?")) {
            stmt.setString(1, name());

            ResultSet result = stmt.executeQuery();
            List<Head> heads = new ArrayList<>();
            while (result.next()) {
                try {
                    heads.add(new Head(
                            UUID.fromString(result.getString("uuid")),
                            result.getString("name"),
                            result.getString("value"),
                            Arrays.asList(result.getString("tags").split(",")),
                            this
                    ));
                } catch (Exception ignored) {}
            }
            return heads;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return null;
    }

    public @NotNull ItemStack getGuiHead() {
        if (guiHead != null) return guiHead.clone();

        HeadsManager manager = Main.getInstance().getHeadsManager();
        if (manager == null || !manager.isAvailable()) return GUI.createGuiItem(Material.PLAYER_HEAD, "&5" + this);

        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, name, value, tags FROM st14_heads WHERE category = ? LIMIT 1")) {
            stmt.setString(1, name());

            ResultSet result = stmt.executeQuery();
            if (!result.next()) return GUI.createGuiItem(Material.PLAYER_HEAD, "&5" + this);

            ItemStack item = new Head(
                    UUID.fromString(result.getString("uuid")),
                    result.getString("name"),
                    result.getString("value"),
                    Arrays.asList(result.getString("tags").split(",")),
                    this
            ).getItem();

            ItemMeta meta = item.getItemMeta();
            meta.displayName(Utils.getComponentByString("&5" + this));
            meta.lore(List.of(Utils.getComponentByString("&bKliknij, aby wyświetlić!")));
            item.setItemMeta(meta);

            guiHead = item;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return GUI.createGuiItem(Material.PLAYER_HEAD, "&5" + this);
    }



    public boolean downloadCategory() {
        try {
            String json = getCategoryJson();
            if (json == null) return false;
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            return saveHeads(parseCategoryJson(jsonArray));
        } catch (Exception e) {
            Logs.error("An error occurred while refreshing heads from " + name().toLowerCase() + " category.", e);
        }
        return false;
    }

    private @Nullable String getCategoryJson() throws IOException {
        URL url = new URL("https://minecraft-heads.com/scripts/api.php?cat=" + apiName + "&tags=true");
        HttpURLConnection httpConnection = (HttpURLConnection) url.openConnection();
        httpConnection.setConnectTimeout(5000);
        httpConnection.setRequestMethod("GET");
        httpConnection.setRequestProperty("Accept", "application/json");

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(httpConnection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            int responseCode = httpConnection.getResponseCode();
            httpConnection.disconnect();

            if (responseCode < 200 || responseCode > 299) {
                Logs.error("An error occurred while refreshing heads from " + name().toLowerCase() + " category. Response code: " + responseCode);
                return null;
            }

            return builder.toString();
        }
    }

    private @NotNull List<Head> parseCategoryJson(@NotNull JsonArray json) {
        List<Head> heads = new ArrayList<>();
        for (JsonElement element : json) {
            JsonObject object = element.getAsJsonObject();

            UUID uuid = UUID.randomUUID();
            try {
                uuid = UUID.fromString(object.get("uuid").getAsString());
            } catch (IllegalArgumentException ignored) {}

            heads.add(new Head(
                    uuid,
                    object.get("name").getAsString(),
                    object.get("value").getAsString(),
                    Arrays.asList(object.get("tags").getAsString().split(",")),
                    this
            ));
        }
        return heads;
    }

    private boolean saveHeads(@NotNull List<Head> heads) {
        HeadsManager manager = Main.getInstance().getHeadsManager();
        if (manager != null && manager.isAvailable()) return false;

        try (Connection conn = Main.getInstance().getMySQLConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_heads WHERE category = ?")) {
                stmt.setString(1, name());
            }
            try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO st14_heads(uuid, name, value, tags, category) VALUES (?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, value = ?, tags = ?, category = ?")) {
                int index = 0;
                for (Head head : heads) {
                    stmt.setString(1, head.getUuid().toString());
                    stmt.setString(2, head.getName());
                    stmt.setString(3, head.getValue());
                    stmt.setString(4, String.join(",", head.getTags()));
                    stmt.setString(5, name());

                    stmt.setString(6, head.getName());
                    stmt.setString(7, head.getValue());
                    stmt.setString(8, String.join(",", head.getTags()));
                    stmt.setString(9, name());

                    stmt.addBatch();
                    index++;

                    if (index % 1000 == 0) {
                        stmt.executeBatch();
                    }
                }
                if (index % 1000 != 0) stmt.executeBatch();
            }
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
    }

    @Override
    public String toString() {
        return name;
    }

    public static @Nullable HeadsCategory getCategoryByApiName(@NotNull String apiName) {
        for (HeadsCategory category : HeadsCategory.values()) {
            if (category.apiName.equalsIgnoreCase(apiName)) return category;
        }
        return null;
    }

    public static @Nullable HeadsCategory getCategoryByName(@NotNull String name) {
        for (HeadsCategory category : HeadsCategory.values()) {
            if (category.name.equals(name)) return category;
        }
        return null;
    }
}