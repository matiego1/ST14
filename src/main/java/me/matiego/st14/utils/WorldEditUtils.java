package me.matiego.st14.utils;

import com.sk89q.jnbt.CompoundTag;
import com.sk89q.jnbt.ListTag;
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
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileInputStream;
import java.util.Map;

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

    //https://github.com/MagmaGuy/BetterStructures/blob/ee4a998588e2925faa33b5e72b97b1bf4f4d141b/src/main/java/com/magmaguy/betterstructures/schematics/SchematicContainer.java#L153
    public static @NotNull String getSignLine(@NotNull BaseBlock baseBlock, int line) {
        CompoundTag nbt = baseBlock.getNbtData();
        if (nbt == null) return "";

        String string = "";
        try {
            string = getCleanSignLine(nbt.getString("Text" + line));
        } catch (Exception ignored) {}
        if (!string.isEmpty()) return string;

        try {
            string = getCleanSignLine(((ListTag) ((Map<?, ?>) baseBlock.getNbtData().getValue().get("front_text").getValue()).get("messages")).getString(line - 1));
        } catch (Exception ignored) {}
        if (!string.isEmpty()) return string;

        try {
            string = getCleanSignLine(((ListTag) ((Map<?, ?>) baseBlock.getNbtData().getValue().get("back_text").getValue()).get("messages")).getString(line));
        } catch (Exception ignored) {}

        return string;
    }

    private static @NotNull String getCleanSignLine(@NotNull String jsonLine) {
        if (jsonLine.split(":").length < 2) return "";
        return jsonLine.split(":", 2)[1].replace("\"", "").replace("}", "");
    }
}
