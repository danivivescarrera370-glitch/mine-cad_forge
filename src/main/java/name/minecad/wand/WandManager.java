package name.minecad.wand;

import name.minecad.scannedblock.ScannedBlockManager;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class WandManager {
    private final Wand wand;
    private final ScannedBlockManager scannedBlockManager;

    /**
     * Manages the magic wand functionality in the game.
     * This class is responsible for handling the wand's properties, actions, and interactions with blocks
     */
    public WandManager(Wand wand, ScannedBlockManager scannedBlockManager) {
        this.wand = wand;
        this.scannedBlockManager = scannedBlockManager;
    }

    public Wand getWand() {
        return this.wand;
    }

    /**
     * Registers Fabric event callbacks for wand interactions.
     */
    public void registerEvents() {
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player.isShiftKeyDown()) {
                return handleWandRemoval(player, world, hand, hitResult.getBlockPos());
            }
            return handleWandSelection(player, world, hand, hitResult.getBlockPos());
        });

        detectBlockDestroyed();
    }

    /**
     * Detects if the wand has been right-clicked on a block.
     * @param player The player using the wand
     * @param world The world the player is in
     * @param hand The hand holding the item
     * @return ActionResult indicating success or pass
     */
    public InteractionResult detectWandRightClick(Player player, Level world, InteractionHand hand) {

        // if the player is holding the wand item
        if (player.getItemInHand(hand).getItem() == wand.getItemForWand()) {
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            HitResult hitResult = player.pick(5.0D, 0.0F, false);

            // if the player has hit (right clicked) a block with the wand
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                BlockPos pos = blockHit.getBlockPos();
                BlockState blockState = world.getBlockState(pos);
                String blockName = blockState.getBlock().getName().getString();

                // Add the scanned block to the ScannedBlockManager
                if (scannedBlockManager.addScannedBlock(blockState.getBlock(), pos, world)) {
                    player.sendSystemMessage(Component.literal("Block scanned: " + blockName + " at location: " + pos));
                } else {
                    player.sendSystemMessage(Component.literal("Block already scanned: " + blockName + " at location: " + pos));
                }
                return InteractionResult.SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Wand detected no block."));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }


    /**
     * Detects if the wand has been shift-right-clicked.
     * If so, the block that was shift right-clicked will be removed from the scanned blocks list (if it's in the list).
     */
    public InteractionResult detectWandShiftRightClick(Player player, Level world, InteractionHand hand) {
        // Check if the player is holding the wand item and is sneaking (shift-right-click)
        if (player.getItemInHand(hand).getItem() == wand.getItemForWand() && player.isShiftKeyDown()) {
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            HitResult hitResult = player.pick(5.0D, 0.0F, false);

            // If the player has hit a block with the wand
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                BlockPos pos = blockHit.getBlockPos();
                BlockState blockState = world.getBlockState(pos);
                String blockName = blockState.getBlock().getName().getString();

                // Remove the scanned block from the ScannedBlockManager
                if (scannedBlockManager.removeScannedBlock(blockState.getBlock(), pos)) {
                    player.sendSystemMessage(Component.literal("Block removed: " + blockName + " at location: " + pos));
                } else {
                    player.sendSystemMessage(Component.literal("Block not found in scanned blocks: " + blockName + " at location: " + pos));
                }
                return InteractionResult.SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Wand detected no block."));
                // If the player is holding the wand and sneaking, but no block was hit, return SUCCESS
                return InteractionResult.SUCCESS;
            }
        }

        // If the player is not holding the wand or not sneaking, return PASS
        return InteractionResult.PASS;
    }

    private InteractionResult handleWandSelection(Player player, Level world, InteractionHand hand, BlockPos pos) {
        if (player.getItemInHand(hand).getItem() != wand.getItemForWand()) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockState blockState = world.getBlockState(pos);
        String blockName = blockState.getBlock().getName().getString();

        if (scannedBlockManager.addScannedBlock(blockState.getBlock(), pos, world)) {
            player.sendSystemMessage(Component.literal("Block scanned: " + blockName + " at location: " + pos));
        } else {
            player.sendSystemMessage(Component.literal("Block already scanned: " + blockName + " at location: " + pos));
        }
        return InteractionResult.SUCCESS;
    }

    private InteractionResult handleWandRemoval(Player player, Level world, InteractionHand hand, BlockPos pos) {
        if (player.getItemInHand(hand).getItem() != wand.getItemForWand()) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide()) {
            return InteractionResult.SUCCESS;
        }

        BlockState blockState = world.getBlockState(pos);
        String blockName = blockState.getBlock().getName().getString();

        if (scannedBlockManager.removeScannedBlock(blockState.getBlock(), pos)) {
            player.sendSystemMessage(Component.literal("Block removed: " + blockName + " at location: " + pos));
        } else {
            player.sendSystemMessage(Component.literal("Block not found in scanned blocks: " + blockName + " at location: " + pos));
        }
        return InteractionResult.SUCCESS;
    }

    /**
     * This method is called when a block is destroyed by the player via the @wand.
     * It can be used to handle any specific actions when a block is destroyed.
     * But will be primarily used to remove the block from the scanned blocks list.
     * @param player The player who destroyed the block
     * @param world The world in which the block was destroyed
     * @param hand The hand used to destroy the block
     * @return ActionResult indicating the result of the action
     */
    public InteractionResult detectBlockDestroyedByWand(Player player, Level world, InteractionHand hand) {
        // Check if the player is holding the wand item
        if (player.getItemInHand(hand).getItem() == wand.getItemForWand()) {
            if (world.isClientSide()) {
                return InteractionResult.SUCCESS;
            }

            HitResult hitResult = player.pick(5.0D, 0.0F, false);

            // If the player has hit a block with the wand
            if (hitResult.getType() == HitResult.Type.BLOCK) {
                BlockHitResult blockHit = (BlockHitResult) hitResult;
                BlockPos pos = blockHit.getBlockPos();
                BlockState blockState = world.getBlockState(pos);
                String blockName = blockState.getBlock().getName().getString();

                // Remove the scanned block from the ScannedBlockManager
                if (scannedBlockManager.removeScannedBlock(blockState.getBlock(), pos)) {
                    player.sendSystemMessage(Component.literal("Block destroyed: " + blockName + " at location: " + pos));
                } else {
                    player.sendSystemMessage(Component.literal("Block not found in scanned blocks: " + blockName + " at location: " + pos));
                }
                return InteractionResult.SUCCESS;
            } else {
                player.sendSystemMessage(Component.literal("Wand detected no block."));
                return InteractionResult.SUCCESS;
            }
        }
        return InteractionResult.PASS;
    }

    /**
     * This method registers an event listener for when any block is destroyed.
     * When a block is destroyed, it will check if the block is in the scanned blocks list.
     * If it is, it will remove the block from the scanned blocks list.
     */
    public void detectBlockDestroyed() {
        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            // Check if any block is destroyed (not just with wand)
            BlockState blockState = world.getBlockState(pos);
            if (scannedBlockManager.removeScannedBlock(blockState.getBlock(), pos)) {
                player.sendSystemMessage(Component.literal("Tracked block destroyed: " + blockState.getBlock().getName().getString() + " at " + pos));
            }
            return InteractionResult.PASS;
        });
    }


    public String getWandInfo() {

        return "Current wand ID: " + this.wand.getItemForWand().getDescriptionId();
    }

}
