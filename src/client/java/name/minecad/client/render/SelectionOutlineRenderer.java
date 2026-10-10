package name.minecad.client.render;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import name.minecad.MineCad;
import name.minecad.scannedblock.ScannedBlock;
import name.minecad.scannedblock.ScannedBlockManager;
import name.minecad.ui.MineFabSettings;
import name.minecad.wand.WandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public final class SelectionOutlineRenderer {
    private static final double OUTLINE_INFLATE = 0.002D;
    private static final KeyMapping TOGGLE_OUTLINES_KEY = KeyMappingHelper.registerKeyMapping(new KeyMapping(
            "key.mine-cad.toggle_outlines",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_O,
            KeyMapping.Category.MISC
    ));

    private SelectionOutlineRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && MineCad.getScannedBlockManager() != null) {
                MineCad.getScannedBlockManager().tick(client.level);
            }

            while (TOGGLE_OUTLINES_KEY.consumeClick()) {
                boolean enabled = !MineFabSettings.areOutlinesEnabled();
                MineFabSettings.setOutlinesEnabled(enabled);
                sendClientFeedback(client, "Selection outlines " + (enabled ? "enabled" : "disabled"));
            }
        });

        LevelRenderEvents.BEFORE_GIZMOS.register(SelectionOutlineRenderer::renderSelectionOutlines);
    }

    private static void renderSelectionOutlines(LevelRenderContext context) {
        if (!MineFabSettings.areOutlinesEnabled()) {
            return;
        }

        ScannedBlockManager manager = MineCad.getScannedBlockManager();
        if (manager == null) {
            return;
        }

        List<ScannedBlock> scannedBlocks = manager.getScannedBlocks();
        BlockPos hoveredBlockPos = getHoveredBlockPos();

        if (scannedBlocks.isEmpty() && hoveredBlockPos == null) {
            return;
        }

        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        GizmoStyle selectedStyle = GizmoStyle.stroke(
                MineFabSettings.getSelectedOutlineColor(),
                MineFabSettings.getOutlineStrokeWidth()
        );
        GizmoStyle hoveredStyle = GizmoStyle.stroke(
                MineFabSettings.getHoveredOutlineColor(),
                MineFabSettings.getOutlineStrokeWidth()
        );

        for (ScannedBlock scannedBlock : scannedBlocks) {
            if (hoveredBlockPos != null && hoveredBlockPos.equals(scannedBlock.pos)) {
                continue;
            }
            collector.add(new CuboidGizmo(scannedBlock.getOutlineBox(OUTLINE_INFLATE), selectedStyle, true));
        }

        if (hoveredBlockPos != null) {
            collector.add(new CuboidGizmo(
                    new net.minecraft.world.phys.AABB(
                            hoveredBlockPos.getX(), hoveredBlockPos.getY(), hoveredBlockPos.getZ(),
                            hoveredBlockPos.getX() + 1.0D, hoveredBlockPos.getY() + 1.0D, hoveredBlockPos.getZ() + 1.0D
                    ).inflate(OUTLINE_INFLATE),
                    hoveredStyle,
                    true
            ));
        }

        context.levelRenderer().addMainThreadGizmos(collector.drainGizmos());
    }

    private static BlockPos getHoveredBlockPos() {
        if (!MineFabSettings.isHoveredOutlineEnabled()) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || !isHoldingWand(player)) {
            return null;
        }

        HitResult hitResult = client.hitResult;
        if (!(hitResult instanceof BlockHitResult blockHitResult) || hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return blockHitResult.getBlockPos();
    }

    private static boolean isHoldingWand(LocalPlayer player) {
        WandManager wandManager = MineCad.getWandManager();
        if (wandManager == null || wandManager.getWand() == null) {
            return false;
        }

        Item wandItem = wandManager.getWand().getItemForWand();
        return player.getItemInHand(InteractionHand.MAIN_HAND).getItem() == wandItem
                || player.getItemInHand(InteractionHand.OFF_HAND).getItem() == wandItem;
    }

    private static void sendClientFeedback(Minecraft client, String message) {
        if (client.player != null) {
            client.player.sendSystemMessage(Component.literal(message));
        }
    }
}

