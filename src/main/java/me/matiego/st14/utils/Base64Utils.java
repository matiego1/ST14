package me.matiego.st14.utils;

import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;

public class Base64Utils {
    public static @Nullable String objectToBase64(@NotNull Object object) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream bukkitObjectOutputStream = new BukkitObjectOutputStream(byteArrayOutputStream);

            bukkitObjectOutputStream.writeObject(object);
            bukkitObjectOutputStream.close();

            return Base64.getEncoder().encodeToString(byteArrayOutputStream.toByteArray());
        } catch (Exception ignored) {}
        return null;
    }

    public static @Nullable Object base64ToObject(@NotNull String string) {
        try {
            return new BukkitObjectInputStream(new ByteArrayInputStream(Base64.getDecoder().decode(string))).readObject();
        } catch (Exception ignored) {}
        return null;
    }

}
