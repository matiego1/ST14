package me.matiego.st14.objects.minigames.maze;

import lombok.Getter;
import me.matiego.st14.utils.Utils;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MazeGenerator {
    public MazeGenerator(int sizeX, int sizeZ, int startX, int startZ) {
        this(sizeX, sizeZ, startX, startZ, null);
    }
    public MazeGenerator(int sizeX, int sizeZ, int startX, int startZ, @Nullable Long seed) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;

        if (seed == null) {
            random = new Random();
        } else {
            random = new Random(seed);
        }

        grid = new MazeCell[sizeX][sizeZ];
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                grid[x][z] = new MazeCell(x, z);
            }
        }

        startCell = grid[startX][startZ];
        pickExit();

        generate();
    }

    private final Random random;
    private final int sizeX, sizeZ;
    private final MazeCell[][] grid;
    private final MazeCell startCell;
    @Getter private MazeCell endCell;

    private void pickExit() {
        int randomZ = random.nextInt(sizeZ);
        endCell = grid[sizeX - 1][randomZ];
    }

    // Wilson's Algorithm (absolutely not written by Gemini)
    private void generate() {
        startCell.setVisited(true);

        int cellsInMaze = 1;
        int totalCells = sizeX * sizeZ;

        while (cellsInMaze < totalCells) {
            MazeCell startWalk = getRandomCellOutside();
            MazeCell current = startWalk;

            while (!current.isVisited()) {
                MazeCell next = getRandomNeighbor(current);
                current.setNextCell(next);
                current = next;
            }

            MazeCell carve = startWalk;
            while (!carve.isVisited()) {
                MazeCell next = carve.getNextCell();

                removeWalls(carve, next);
                carve.setVisited(true);
                cellsInMaze++;

                carve = next;
            }
        }
    }

    private @NotNull MazeCell getRandomCellOutside() {
        List<MazeCell> outside = new ArrayList<>();
        for (int x = 0; x < sizeX; x++) {
            for (int y = 0; y < sizeZ; y++) {
                if (!grid[x][y].isVisited()) {
                    outside.add(grid[x][y]);
                }
            }
        }
        return outside.get(random.nextInt(outside.size()));
    }

    private @NotNull MazeCell getRandomNeighbor(@NotNull MazeCell cell) {
        List<MazeCell> neighbors = new ArrayList<>();
        int x = cell.getX();
        int z = cell.getZ();

        if (z > 0) neighbors.add(grid[x][z - 1]);
        if (z < sizeZ - 1) neighbors.add(grid[x][z + 1]);
        if (x > 0) neighbors.add(grid[x - 1][z]);
        if (x < sizeX - 1) neighbors.add(grid[x + 1][z]);

        return neighbors.get(random.nextInt(neighbors.size()));
    }

    private void removeWalls(@NotNull MazeCell c1, @NotNull MazeCell c2) {
        if (c1.getX() == c2.getX()) {
            if (c1.getZ() < c2.getZ()) c1.setSouth(false);
            else c2.setSouth(false);
        } else {
            if (c1.getX() < c2.getX()) c1.setEast(false);
            else c2.setEast(false);
        }
    }

    public void build(@NotNull Location baseCell) {
        Utils.sync(() -> {
            endCell.setEndCellFloor(baseCell);
            for (int x = 0; x < sizeX; x++) {
                for (int z = 0; z < sizeZ; z++) {
                    grid[x][z].buildWalls(baseCell);
                }
            }
        });
    }
}
