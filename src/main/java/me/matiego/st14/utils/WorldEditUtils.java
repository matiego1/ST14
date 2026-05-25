package me.matiego.st14.utils;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.ListTag;
import com.sk89q.jnbt.StringTag;
import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import com.sk89q.worldedit.world.block.BaseBlock;
import me.matiego.st14.Logs;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;

public class WorldEditUtils {

    public static @NotNull Clipboard pasteSchematic(@NotNull World world, @NotNull BlockVector3 vector, @NotNull File file) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalStateException("cannot find clipboard format by file");

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            //noinspection deprecation
            try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(world), -1)) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(vector)
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
            }

            return clipboard;
        }
    }

    // https://github.com/MagmaGuy/BetterStructures/blob/dde7144544cbc077d9a561d79522b17a56838331/src/main/java/com/magmaguy/betterstructures/util/WorldEditUtils.java#L114
    public static @NotNull String getSignLine(@NotNull BaseBlock baseBlock, int line) {
        CompoundTag data = baseBlock.getNbtData();
        if (data == null) return "";

        try {
            CompoundTag frontText = (CompoundTag) data.getValue().get("front_text");
            ListTag messages = (ListTag) frontText.getValue().get("messages");
            Object object = messages.getValue().get(line - 1);
            if (object instanceof CompoundTag compoundTag) {
                object = compoundTag.getValue().get("text");
            }
            String text = ((StringTag) object).getValue();

            if (text.contains("\"text\":")) text = text.split("text\":\"")[1].split("\"")[0];
            return text.replaceAll("\"", "");

        } catch (Exception e) {
            Logs.warning("Unexpected sign format: " + data, e);
        }
        return "";
    }
}
