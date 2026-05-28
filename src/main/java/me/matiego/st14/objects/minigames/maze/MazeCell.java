package me.matiego.st14.objects.minigames.maze;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

@Getter @Setter
public class MazeCell {
    public MazeCell(int x, int z) {
        this.x = x;
        this.z = z;
    }

    private final int cellHeight = 5;

    private final int x, z;
    private boolean visited = false;
    private boolean south = true;
    private boolean east = true;
    private MazeCell nextCell;

    public @NotNull Location getCellLocation(@NotNull Location baseCell) {
        Location loc = baseCell.clone();
        loc.add(3 * x, 0, 3 * z);
        return loc;
    }

    public boolean isInside(@NotNull Location baseCell, @NotNull Location location) {
        Location cell = getCellLocation(baseCell);

        int minX = cell.getBlockX() - 1;
        int minZ = cell.getBlockZ() - 1;
        int maxX = cell.getBlockX() + 2;
        int maxZ = cell.getBlockZ() + 2;

        double x = location.getX();
        double z = location.getZ();

        return (minX <= x && x <= maxX && minZ <= z && z <= maxZ);
    }

    public void setEndCellFloor(@NotNull Location baseCell) {
        Location cell = getCellLocation(baseCell);

        setBlock(cell, 0, -1, 0, Material.DIAMOND_BLOCK);
        setBlock(cell, 0, -1, 1, Material.DIAMOND_BLOCK);
        setBlock(cell, 1, -1, 0, Material.DIAMOND_BLOCK);
        setBlock(cell, 1, -1, 1, Material.DIAMOND_BLOCK);
    }

    public void buildWalls(@NotNull Location baseCell) {
        Location cell = getCellLocation(baseCell);

        for (int height = 0; height < cellHeight; height++) {
            if (isEast()) {
                setBlock(cell, 2, height, 0, Material.STONE_BRICKS);
                setBlock(cell, 2, height, 1, Material.STONE_BRICKS);
            }
            if (isSouth()) {
                setBlock(cell, 0, height, 2, Material.STONE_BRICKS);
                setBlock(cell, 1, height, 2, Material.STONE_BRICKS);
            }
        }
    }

    private void setBlock(@NotNull Location location, int x, int y, int z, @NotNull Material material) {
        location.add(x, y, z);
        location.getBlock().setType(material);
        location.add(-x, -y, -z);
    }
}
