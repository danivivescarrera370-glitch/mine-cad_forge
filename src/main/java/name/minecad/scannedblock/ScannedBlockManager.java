package name.minecad.scannedblock;

import java.util.LinkedList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

public class ScannedBlockManager {
    private final List<ScannedBlock> scannedBlocks = new LinkedList<>();
    private final int updateInterval;
    private int tickCounter = 0;

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
        if (world == null || scannedBlocks.isEmpty()) return;

        tickCounter++;
        if (tickCounter >= updateInterval) {
            updateBlockStates(world);
            tickCounter = 0; // Reset counter
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
        if (block == null || pos == null || world == null) {
            System.out.println("Block, position, or world is null.");
            return false;
        }
        boolean exists = scannedBlocks.stream().noneMatch(scannedBlock -> scannedBlock.pos.equals(pos));
        if (!exists) {
            System.out.println("Position already tracked: " + pos);
            return false;
        }
        if (!doesBlockExist(block, pos, world)) {
            return false;
        }

        scannedBlocks.add(new ScannedBlock(block, pos));
        System.out.println("Block added: " + block.getName().getString() + " at location: " + pos);
        return true;
    }

    public boolean removeScannedBlock(Block block, BlockPos pos) {
        if (block == null || pos == null) {
            System.out.println("Block or position is null.");
            return false;
        }
        boolean removed = scannedBlocks.removeIf(scannedBlock -> scannedBlock.pos.equals(pos));
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
            }
        }
    }

    /**
     * Updates a specific block's state if it exists in the tracked list.
     */
    public boolean updateBlockState(BlockPos pos, Level world) {
        if (pos == null || world == null) return false;

        for (int i = 0; i < scannedBlocks.size(); i++) {
            ScannedBlock scannedBlock = scannedBlocks.get(i);
            if (scannedBlock.pos.equals(pos)) {
                Block currentBlock = world.getBlockState(pos).getBlock();
                if (!scannedBlock.block.equals(currentBlock)) {
                    scannedBlocks.set(i, new ScannedBlock(currentBlock, pos));
                    System.out.println("Updated block at " + pos + " to " + currentBlock.getName().getString());
                    return true;
                }
                break;
            }
        }
        return false;
    }

    /**
     * Gets all currently tracked blocks.
     */
    public List<ScannedBlock> getScannedBlocks() {
        return new LinkedList<>(scannedBlocks);
    }

    /**
     * Gets the current update interval.
     */
    public int getUpdateInterval() {
        return updateInterval;
    }

    public void deleteSelection() {
        scannedBlocks.clear();
    }

    public long getSelectionCount() {
        return this.scannedBlocks.size();
    }

    private boolean doesBlockExist(Block block, BlockPos pos, Level world) {
        if (world == null || pos == null || block == null) return false;
        return world.hasChunkAt(pos) && world.getBlockState(pos).getBlock().equals(block);
    }
}