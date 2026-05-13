package me.matiego.st14.utils;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Base64;

public class Base64Utils {
    public static @Nullable ItemStack[] base64ToItemStacks(@NotNull String string) {
        try {
            return ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(string));
        } catch (Exception ignored) {}
        return null;
    }

    public static @Nullable String itemStacksToBase64(@NotNull ItemStack[] items) {
        try {
            return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
        } catch (Exception ignored) {}
        return null;
    }
}
