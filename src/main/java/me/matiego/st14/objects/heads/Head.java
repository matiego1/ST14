package me.matiego.st14.objects.heads;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.properties.Property;
import lombok.Getter;
import me.matiego.st14.Logs;
import me.matiego.st14.objects.GUI;
import me.matiego.st14.utils.Utils;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Head {

    public Head(@NotNull UUID uuid, @NotNull String name, @NotNull String value, @NotNull List<String> tags, @NotNull HeadsCategory category) {
        this.uuid = uuid;
        this.name = name;
        this.value = value;
        this.tags = tags;
        this.category = category;
    }

    @Getter
    private final String name;
    @Getter
    private final UUID uuid;
    @Getter
    private final String value;
    @Getter
    private final List<String> tags;
    private ItemStack item = null;
    @Getter
    private final HeadsCategory category;

    public @NotNull ItemStack getItem() {
        if (item == null) createItem();
        return item.clone();
    }

    private void createItem() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);

        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta == null) return;
        meta.displayName(Utils.getComponentByString(getName()).decoration(TextDecoration.ITALIC, false));

        List<Component> lores = new ArrayList<>();
        lores.add(Utils.getComponentByString("&7Kategoria: " + category));
        if (!getTags().isEmpty()) {
            lores.add(Utils.getComponentByString("&7Tagi: " + String.join(", ", getTags())));
        }
        meta.lore(lores);

        GameProfile profile = new GameProfile(getUuid(), getName());
        profile.getProperties().put("textures", new Property("textures", value));
        try {
            Field field = meta.getClass().getDeclaredField("profile");
            field.setAccessible(true);
            field.set(meta, profile);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            Logs.error("An error occurred while creating a custom player head.", e);

            this.item = GUI.createGuiItem(Material.BARRIER, "&cBŁĄD", "&cNapotkano błąd przy generowaniu.", "&cProsimy, zgłoś ten błąd do administratora.");
            return;
        }

        item.setItemMeta(meta);
        this.item = item;
    }
}
