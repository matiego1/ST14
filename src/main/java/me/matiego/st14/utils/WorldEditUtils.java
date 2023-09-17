package me.matiego.st14.utils;

import com.sk89q.jnbt.CompoundTag;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Container;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class WorldEditUtils {
    public static void pasteSchematic(@NotNull World world, @NotNull BlockVector3 vector, @NotNull File file) throws Exception {
        pasteSchematicAndGenerateChests(world, vector, file, null);
    }

    public static void pasteSchematicAndGenerateChests(@NotNull World world, @NotNull BlockVector3 vector, @NotNull File file, @Nullable Function<String, List<ItemStack>> itemsByChestType) throws Exception {
        ClipboardFormat format = ClipboardFormats.findByFile(file);
        if (format == null) throw new IllegalStateException("cannot find clipboard format by file");

        try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
            Clipboard clipboard = reader.read();

            HashMap<BlockVector3, String> chests = new HashMap<>();
            if (itemsByChestType != null) {
                for (int x = 0; x <= clipboard.getDimensions().getX(); x++) {
                    for (int y = 0; y <= clipboard.getDimensions().getY(); y++) {
                        for (int z = 0; z <= clipboard.getDimensions().getZ(); z++) {

                            BlockVector3 blockLocation = BlockVector3.at(x, y, z).add(clipboard.getMinimumPoint());
                            Material blockMaterial = BukkitAdapter.adapt(clipboard.getBlock(blockLocation).getBlockType());
                            if (blockMaterial == null || !blockMaterial.toString().contains("SIGN")) continue;

                            CompoundTag blockNbtData = clipboard.getFullBlock(blockLocation).getNbtData();
                            if (blockNbtData == null) continue;

                            if (!blockNbtData.getString("Text1").contains("[chest]")) continue;
                            //The chest type must start and end with a colon
                            chests.put(vector.add(x, y, z), blockNbtData.getString("Text2").split(":")[1]);
                        }
                    }
                }
            }

            //noinspection deprecation
            try (EditSession editSession = WorldEdit.getInstance().getEditSessionFactory().getEditSession(BukkitAdapter.adapt(world), -1)) {
                Operation operation = new ClipboardHolder(clipboard)
                        .createPaste(editSession)
                        .to(vector)
                        .ignoreAirBlocks(false)
                        .build();
                Operations.complete(operation);
            }

            if (itemsByChestType != null) {
                for (Map.Entry<BlockVector3, String> entry : chests.entrySet()) {
                    Location chestLocation = BukkitAdapter.adapt(world, entry.getKey());
                    chestLocation.getBlock().setType(Material.CHEST);
                    if (!(chestLocation.getBlock().getState() instanceof Container container)) continue;

                    int i = 0;
                    for (ItemStack item : itemsByChestType.apply(entry.getValue())) {
                        container.getInventory().setItem(i, item);
                        i++; //support air
                    }
                }
            }
        }
    }
}
