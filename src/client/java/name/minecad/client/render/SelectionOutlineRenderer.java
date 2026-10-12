package name.minecad.client.render;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import name.minecad.MineCad;
import name.minecad.client.input.BindingInput;
import name.minecad.scannedblock.ScannedBlock;
import name.minecad.scannedblock.ScannedBlockManager;
import name.minecad.ui.MineFabSettings;
import name.minecad.wand.WandManager;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gizmos.CuboidGizmo;
import net.minecraft.gizmos.Gizmo;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.LineGizmo;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class SelectionOutlineRenderer {
    private static final double OUTLINE_INFLATE = 0.002D;
    private static final Direction[] NEIGHBORS = Direction.values();
    private static boolean toggleBindingPreviouslyDown;
    private static long cachedRevision = Long.MIN_VALUE;
    private static int cachedOutlineColor = Integer.MIN_VALUE;
    private static float cachedStrokeWidth = Float.NaN;
    private static List<Gizmo> cachedSelectionGizmos = List.of();

    private SelectionOutlineRenderer() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.level != null && MineCad.getScannedBlockManager() != null) {
                MineCad.getScannedBlockManager().tick(client.level);
            }

            handleOutlineToggleHotkey(client);
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

        ensureSelectionGizmosUpToDate(manager);
        BlockPos hoveredBlockPos = getHoveredBlockPos();

        if (cachedSelectionGizmos.isEmpty() && hoveredBlockPos == null) {
            return;
        }

        SimpleGizmoCollector collector = new SimpleGizmoCollector();
        for (Gizmo selectionGizmo : cachedSelectionGizmos) {
            collector.add(selectionGizmo);
        }

        GizmoStyle hoveredStyle = GizmoStyle.stroke(
                MineFabSettings.getHoveredOutlineColor(),
                MineFabSettings.getOutlineStrokeWidth()
        );

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

    private static void ensureSelectionGizmosUpToDate(ScannedBlockManager manager) {
        long revision = manager.getSelectionRevision();
        int outlineColor = MineFabSettings.getSelectedOutlineColor();
        float strokeWidth = MineFabSettings.getOutlineStrokeWidth();
        if (revision == cachedRevision
                && outlineColor == cachedOutlineColor
                && Float.compare(strokeWidth, cachedStrokeWidth) == 0) {
            return;
        }

        List<ScannedBlock> scannedBlocks = manager.getScannedBlocks();
        cachedSelectionGizmos = buildUniqueSurfaceEdgeOutlines(scannedBlocks, outlineColor, strokeWidth);
        cachedRevision = revision;
        cachedOutlineColor = outlineColor;
        cachedStrokeWidth = strokeWidth;
    }

    private static List<Gizmo> buildUniqueSurfaceEdgeOutlines(List<ScannedBlock> scannedBlocks, int color, float width) {
        if (scannedBlocks.isEmpty()) {
            return List.of();
        }

        Set<BlockPos> selectedPositions = new HashSet<>(Math.max(16, scannedBlocks.size() * 2));
        for (ScannedBlock scannedBlock : scannedBlocks) {
            selectedPositions.add(scannedBlock.pos);
        }

        Set<EdgeKey> uniqueEdges = new HashSet<>();

        for (Direction direction : NEIGHBORS) {
            for (BlockPos pos : selectedPositions) {
                if (selectedPositions.contains(pos.relative(direction))) {
                    continue;
                }
                addExposedFaceEdges(uniqueEdges, pos, direction);
            }
        }

        List<EdgeKey> mergedEdges = mergeCollinearEdges(uniqueEdges);

        ArrayList<Gizmo> gizmos = new ArrayList<>(mergedEdges.size());
        for (EdgeKey edge : mergedEdges) {
            gizmos.add(new LineGizmo(edge.start().toVec3(), edge.end().toVec3(), color, width));
        }

        return gizmos;
    }

    private static List<EdgeKey> mergeCollinearEdges(Set<EdgeKey> uniqueEdges) {
        Map<LineKey, List<IntRange>> lineSegments = new HashMap<>();
        for (EdgeKey edge : uniqueEdges) {
            IntVec3 a = edge.start();
            IntVec3 b = edge.end();

            if (a.x != b.x) {
                lineSegments.computeIfAbsent(new LineKey(Axis.X, a.y, a.z), ignored -> new ArrayList<>())
                        .add(new IntRange(Math.min(a.x, b.x), Math.max(a.x, b.x)));
            } else if (a.y != b.y) {
                lineSegments.computeIfAbsent(new LineKey(Axis.Y, a.x, a.z), ignored -> new ArrayList<>())
                        .add(new IntRange(Math.min(a.y, b.y), Math.max(a.y, b.y)));
            } else if (a.z != b.z) {
                lineSegments.computeIfAbsent(new LineKey(Axis.Z, a.x, a.y), ignored -> new ArrayList<>())
                        .add(new IntRange(Math.min(a.z, b.z), Math.max(a.z, b.z)));
            }
        }

        ArrayList<EdgeKey> merged = new ArrayList<>();
        for (Map.Entry<LineKey, List<IntRange>> entry : lineSegments.entrySet()) {
            List<IntRange> intervals = entry.getValue();
            intervals.sort(Comparator.comparingInt(IntRange::start).thenComparingInt(IntRange::end));
            if (intervals.isEmpty()) {
                continue;
            }

            int currentStart = intervals.get(0).start();
            int currentEnd = intervals.get(0).end();
            for (int i = 1; i < intervals.size(); i++) {
                IntRange next = intervals.get(i);
                if (next.start() <= currentEnd) {
                    currentEnd = Math.max(currentEnd, next.end());
                } else {
                    merged.add(toEdge(entry.getKey(), currentStart, currentEnd));
                    currentStart = next.start();
                    currentEnd = next.end();
                }
            }
            merged.add(toEdge(entry.getKey(), currentStart, currentEnd));
        }
        return merged;
    }

    private static EdgeKey toEdge(LineKey line, int start, int end) {
        return switch (line.axis()) {
            case X -> EdgeKey.of(new IntVec3(start, line.fixedA(), line.fixedB()), new IntVec3(end, line.fixedA(), line.fixedB()));
            case Y -> EdgeKey.of(new IntVec3(line.fixedA(), start, line.fixedB()), new IntVec3(line.fixedA(), end, line.fixedB()));
            case Z -> EdgeKey.of(new IntVec3(line.fixedA(), line.fixedB(), start), new IntVec3(line.fixedA(), line.fixedB(), end));
        };
    }

    private static void addExposedFaceEdges(Set<EdgeKey> edges, BlockPos pos, Direction direction) {
        int x0 = pos.getX();
        int y0 = pos.getY();
        int z0 = pos.getZ();
        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        switch (direction) {
            case EAST -> {
                addEdge(edges, new IntVec3(x1, y0, z0), new IntVec3(x1, y1, z0));
                addEdge(edges, new IntVec3(x1, y1, z0), new IntVec3(x1, y1, z1));
                addEdge(edges, new IntVec3(x1, y1, z1), new IntVec3(x1, y0, z1));
                addEdge(edges, new IntVec3(x1, y0, z1), new IntVec3(x1, y0, z0));
            }
            case WEST -> {
                addEdge(edges, new IntVec3(x0, y0, z1), new IntVec3(x0, y1, z1));
                addEdge(edges, new IntVec3(x0, y1, z1), new IntVec3(x0, y1, z0));
                addEdge(edges, new IntVec3(x0, y1, z0), new IntVec3(x0, y0, z0));
                addEdge(edges, new IntVec3(x0, y0, z0), new IntVec3(x0, y0, z1));
            }
            case UP -> {
                addEdge(edges, new IntVec3(x0, y1, z0), new IntVec3(x1, y1, z0));
                addEdge(edges, new IntVec3(x1, y1, z0), new IntVec3(x1, y1, z1));
                addEdge(edges, new IntVec3(x1, y1, z1), new IntVec3(x0, y1, z1));
                addEdge(edges, new IntVec3(x0, y1, z1), new IntVec3(x0, y1, z0));
            }
            case DOWN -> {
                addEdge(edges, new IntVec3(x0, y0, z1), new IntVec3(x1, y0, z1));
                addEdge(edges, new IntVec3(x1, y0, z1), new IntVec3(x1, y0, z0));
                addEdge(edges, new IntVec3(x1, y0, z0), new IntVec3(x0, y0, z0));
                addEdge(edges, new IntVec3(x0, y0, z0), new IntVec3(x0, y0, z1));
            }
            case SOUTH -> {
                addEdge(edges, new IntVec3(x0, y0, z1), new IntVec3(x1, y0, z1));
                addEdge(edges, new IntVec3(x1, y0, z1), new IntVec3(x1, y1, z1));
                addEdge(edges, new IntVec3(x1, y1, z1), new IntVec3(x0, y1, z1));
                addEdge(edges, new IntVec3(x0, y1, z1), new IntVec3(x0, y0, z1));
            }
            case NORTH -> {
                addEdge(edges, new IntVec3(x1, y0, z0), new IntVec3(x0, y0, z0));
                addEdge(edges, new IntVec3(x0, y0, z0), new IntVec3(x0, y1, z0));
                addEdge(edges, new IntVec3(x0, y1, z0), new IntVec3(x1, y1, z0));
                addEdge(edges, new IntVec3(x1, y1, z0), new IntVec3(x1, y0, z0));
            }
        }
    }

    private static void addEdge(Set<EdgeKey> edges, IntVec3 a, IntVec3 b) {
        edges.add(EdgeKey.of(a, b));
    }

    private record IntVec3(int x, int y, int z) {
        Vec3 toVec3() {
            return new Vec3(x, y, z);
        }
    }

    private record EdgeKey(IntVec3 start, IntVec3 end) {
        static EdgeKey of(IntVec3 a, IntVec3 b) {
            if (compare(a, b) <= 0) {
                return new EdgeKey(a, b);
            }
            return new EdgeKey(b, a);
        }

        private static int compare(IntVec3 a, IntVec3 b) {
            if (a.x != b.x) {
                return Integer.compare(a.x, b.x);
            }
            if (a.y != b.y) {
                return Integer.compare(a.y, b.y);
            }
            return Integer.compare(a.z, b.z);
        }
    }

    private enum Axis {
        X,
        Y,
        Z
    }

    private record LineKey(Axis axis, int fixedA, int fixedB) {
    }

    private record IntRange(int start, int end) {
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

    private static void handleOutlineToggleHotkey(Minecraft client) {
        long handle = client.getWindow().handle();
        boolean down = BindingInput.isBindingActive(MineFabSettings.getSettingValue("keybinds.toggle_outline"), handle);
        if (down && !toggleBindingPreviouslyDown) {
            boolean enabled = !MineFabSettings.areOutlinesEnabled();
            MineFabSettings.setOutlinesEnabled(enabled);
            sendClientFeedback(client, "Selection outlines " + (enabled ? "enabled" : "disabled"));
        }
        toggleBindingPreviouslyDown = down;
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

