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
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, name, value, IFNULL((SELECT GROUP_CONCAT(tag SEPARATOR ',') FROM st14_heads_tags WHERE uuid = st14_heads.uuid), '') as tags FROM st14_heads WHERE category = ?")) {
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
        if (manager == null || !manager.isAvailable()) return GUI.createGuiItem(Material.CREEPER_HEAD, "&9" + this);

        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, name, value FROM st14_heads WHERE category = ? LIMIT 1")) {
            stmt.setString(1, name());

            ResultSet result = stmt.executeQuery();
            if (!result.next()) return GUI.createGuiItem(Material.CREEPER_HEAD, "&9" + this);

            ItemStack item = new Head(
                    UUID.fromString(result.getString("uuid")),
                    result.getString("name"),
                    result.getString("value"),
                    new ArrayList<>(),
                    this
            ).getItem();

            ItemMeta meta = item.getItemMeta();
            meta.displayName(Utils.getComponentByString("&9" + this));
            item.setItemMeta(meta);

            guiHead = item;
            return guiHead;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return GUI.createGuiItem(Material.CREEPER_HEAD, "&9" + this);
    }

    public int getHeadsAmount() {
        HeadsManager manager = Main.getInstance().getHeadsManager();
        if (manager == null || !manager.isAvailable()) return -1;
        try (Connection conn = Main.getInstance().getMySQLConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) as amount FROM st14_heads WHERE category = ?")) {
            stmt.setString(1, name());

            ResultSet set = stmt.executeQuery();
            if (!set.next()) return 0;
            return set.getInt("amount");
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return -1;
    }

    public boolean downloadCategory() {
        try {
            String json = getCategoryJson();
            if (json == null) return false;
            JsonArray jsonArray = JsonParser.parseString(json).getAsJsonArray();
            if (saveHeads(parseCategoryJson(jsonArray))) {
                guiHead = null;
                return true;
            }
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

    private boolean saveHeads(@NotNull List<Head> heads) {
        HeadsManager manager = Main.getInstance().getHeadsManager();
        if (manager != null && manager.isAvailable()) return false;

        try (Connection conn = Main.getInstance().getMySQLConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_heads WHERE category = ?")) {
                stmt.setString(1, name());
                stmt.execute();
            }
            try (PreparedStatement stmt = conn.prepareStatement("DELETE FROM st14_heads_tags WHERE category = ?")) {
                stmt.setString(1, name());
                stmt.execute();
            }

            final int BATCH_SIZE = 500;

            try (PreparedStatement stmt_heads = conn.prepareStatement("INSERT INTO st14_heads(uuid, name, value, category) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name = ?, value = ?, category = ?");
                 PreparedStatement stmt_tags = conn.prepareStatement("INSERT INTO st14_heads_tags(uuid, tag, category) VALUES(?, ?, ?) ON DUPLICATE KEY UPDATE category = ?")) {
                int index_heads = 0;
                int index_tags = 0;

                for (Head head : heads) {
                    stmt_heads.setString(1, head.getUuid().toString());
                    stmt_heads.setString(2, head.getName());
                    stmt_heads.setString(3, head.getValue());
                    stmt_heads.setString(4, name());

                    stmt_heads.setString(5, head.getName());
                    stmt_heads.setString(6, head.getValue());
                    stmt_heads.setString(7, name());

                    stmt_heads.addBatch();
                    index_heads++;
                    if (index_heads % BATCH_SIZE == 0) {
                        stmt_heads.executeBatch();
                    }

                    for (String tag : head.getTags()) {
                        stmt_tags.setString(1, head.getUuid().toString());
                        stmt_tags.setString(2, tag);
                        stmt_tags.setString(3, name());
                        stmt_tags.setString(4, name());

                        stmt_tags.addBatch();
                        index_tags++;
                        if (index_tags % BATCH_SIZE == 0) {
                            stmt_tags.executeBatch();
                        }
                    }
                }
                if (index_heads % BATCH_SIZE != 0) stmt_heads.executeBatch();
                if (index_tags % BATCH_SIZE != 0) stmt_tags.executeBatch();
            }
            return true;
        } catch (SQLException e) {
            Logs.error(ERROR_MSG, e);
        }
        return false;
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
                    Arrays.asList(object.get("tags").getAsString().split(",(?! )")),
                    this
            ));
        }
        return heads;
    }

    @Override
    public String toString() {
        return name;
    }

    public static @Nullable HeadsCategory getCategoryByName(@NotNull String name) {
        for (HeadsCategory category : HeadsCategory.values()) {
            if (category.name.equalsIgnoreCase(name)) return category;
        }
        return null;
    }
}
