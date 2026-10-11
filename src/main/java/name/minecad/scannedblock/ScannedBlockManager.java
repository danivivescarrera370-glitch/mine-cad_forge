package name.minecad.scannedblock;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

public class ScannedBlockManager {
    private final Object lock = new Object();
    private final List<ScannedBlock> scannedBlocks = new ArrayList<>();
    private final Set<BlockPos> scannedPositions = new HashSet<>();
    private final int updateInterval;
    private int tickCounter = 0;
    private long selectionRevision = 0;

    /**
     * Constructor that sets the update interval for automatic block state synchronization.
     * @param updateInterval The number of game ticks between automatic updates (20 ticks = 1 second)
     */
    public ScannedBlockManager(int updateInterval) {
        this.updateInterval = updateInterval;
    }

    /**
     * This method should be called every game tick to handle automatic updates.
     * Call this from your client tick event.
     */
    public void tick(Level world) {
        synchronized (lock) {
            if (world == null || scannedBlocks.isEmpty()) return;

            tickCounter++;
            if (tickCounter >= updateInterval) {
                updateBlockStatesLocked(world);
                tickCounter = 0; // Reset counter
            }
        }
    }

    /**
     * NOTE TO SELF: IF THE BLOCK LIST GETS TOO LARGE, WE WILL NEED TO CONSIDER WRITING EVERYTHING TO A FILE
     * AFTER A CERTAIN NUMBER OF BLOCKS. BUT FOR NOW WE WILL KEEP IT IN MEMORY.
     * @param block
     * @param pos
     * @return
     */
    public boolean addScannedBlock(Block block, BlockPos pos, Level world) {
        return addScannedBlock(block, pos, world, true);
    }

    private boolean addScannedBlock(Block block, BlockPos pos, Level world, boolean logSelectionChange) {
        if (block == null || pos == null || world == null) {
            if (logSelectionChange) {
                System.out.println("Block, position, or world is null.");
            }
            return false;
        }
        synchronized (lock) {
            if (scannedPositions.contains(pos)) {
                if (logSelectionChange) {
                    System.out.println("Position already tracked: " + pos);
                }
                return false;
            }
            if (!doesBlockExist(block, pos, world)) {
                return false;
            }

            scannedBlocks.add(new ScannedBlock(block, pos));
            scannedPositions.add(pos);
            selectionRevision++;
        }
        if (logSelectionChange) {
            System.out.println("Block added: " + block.getName().getString() + " at location: " + pos);
        }
        return true;
    }

    public BulkAddResult addScannedBlocksInBounds(BlockPos firstPos, BlockPos secondPos, Level world) {
        if (firstPos == null || secondPos == null || world == null) {
            return new BulkAddResult(0, 0, 0, 0);
        }

        int minX = Math.min(firstPos.getX(), secondPos.getX());
        int minY = Math.min(firstPos.getY(), secondPos.getY());
        int minZ = Math.min(firstPos.getZ(), secondPos.getZ());
        int maxX = Math.max(firstPos.getX(), secondPos.getX());
        int maxY = Math.max(firstPos.getY(), secondPos.getY());
        int maxZ = Math.max(firstPos.getZ(), secondPos.getZ());

        int added = 0;
        int alreadyTracked = 0;
        int skipped = 0;

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState blockState = world.getBlockState(pos);
                    if (blockState.isAir()) {
                        skipped++;
                        continue;
                    }

                    if (addScannedBlock(blockState.getBlock(), pos, world, false)) {
                        added++;
                    } else {
                        synchronized (lock) {
                            if (scannedPositions.contains(pos)) {
                                alreadyTracked++;
                            } else {
                                skipped++;
                            }
                        }
                    }
                }
            }
        }

        long requestedVolume = (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        return new BulkAddResult(added, alreadyTracked, skipped, requestedVolume);
    }

    public boolean removeScannedBlock(Block block, BlockPos pos) {
        if (block == null || pos == null) {
            System.out.println("Block or position is null.");
            return false;
        }
        boolean removed;
        synchronized (lock) {
            removed = scannedBlocks.removeIf(scannedBlock -> scannedBlock.pos.equals(pos));
            if (removed) {
                scannedPositions.remove(pos);
                selectionRevision++;
            }
        }
        if (removed) {
            System.out.println("Block removed: " + block.getName().getString() + " at location: " + pos);
        } else {
            System.out.println("Block not found at location: " + pos);
        }
        return removed;
    }

    /**
     * Updates all tracked blocks to their current state in the world.
     * This should be called periodically to keep the list synchronized.
     */
    public void updateBlockStates(Level world) {
        synchronized (lock) {
            updateBlockStatesLocked(world);
        }
    }

    private void updateBlockStatesLocked(Level world) {
        if (world == null) return;

        for (int i = 0; i < scannedBlocks.size(); i++) {
            ScannedBlock scannedBlock = scannedBlocks.get(i);
            Block currentBlock = world.getBlockState(scannedBlock.pos).getBlock();

            // If the block has changed, update it
            if (!scannedBlock.block.equals(currentBlock)) {
                System.out.println("Block state changed at " + scannedBlock.pos + ": " +
                        scannedBlock.block.getName().getString() + " -> " + currentBlock.getName().getString());

                // Replace with updated block
                scannedBlocks.set(i, new ScannedBlock(currentBlock, scannedBlock.pos));
                selectionRevision++;
            }
        }
    }

    /**
     * Updates a specific block's state if it exists in the tracked list.
     */
    public boolean updateBlockState(BlockPos pos, Level world) {
        if (pos == null || world == null) return false;

        synchronized (lock) {
            for (int i = 0; i < scannedBlocks.size(); i++) {
                ScannedBlock scannedBlock = scannedBlocks.get(i);
                if (scannedBlock.pos.equals(pos)) {
                    Block currentBlock = world.getBlockState(pos).getBlock();
                    if (!scannedBlock.block.equals(currentBlock)) {
                        scannedBlocks.set(i, new ScannedBlock(currentBlock, pos));
                        selectionRevision++;
                        System.out.println("Updated block at " + pos + " to " + currentBlock.getName().getString());
                        return true;
                    }
                    break;
                }
            }
        }
        return false;
    }

    /**
     * Gets all currently tracked blocks.
     */
    public List<ScannedBlock> getScannedBlocks() {
        synchronized (lock) {
            return new ArrayList<>(scannedBlocks);
        }
    }

    /**
     * Gets the current update interval.
     */
    public int getUpdateInterval() {
        return updateInterval;
    }

    /** Increases every time the set of selected blocks (or one of their block types) changes. */
    public long getSelectionRevision() {
        synchronized (lock) {
            return selectionRevision;
        }
    }

    public void deleteSelection() {
        synchronized (lock) {
            if (!scannedBlocks.isEmpty()) {
                selectionRevision++;
            }
            scannedBlocks.clear();
            scannedPositions.clear();
        }
    }

    public long getSelectionCount() {
        synchronized (lock) {
            return this.scannedBlocks.size();
        }
    }

    private boolean doesBlockExist(Block block, BlockPos pos, Level world) {
        if (world == null || pos == null || block == null) return false;
        return world.hasChunkAt(pos) && world.getBlockState(pos).getBlock().equals(block);
    }

    public record BulkAddResult(int addedCount, int alreadyTrackedCount, int skippedCount, long requestedVolume) {
    }
}