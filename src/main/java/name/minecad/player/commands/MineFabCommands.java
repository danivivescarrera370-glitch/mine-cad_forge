package name.minecad.player.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import name.minecad.scannedblock.ScannedBlock;
import name.minecad.scannedblock.ScannedBlockManager;
import name.minecad.stl.STLManager;
import name.minecad.wand.WandManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.LevelResource;

public class MineFabCommands {
    private static ScannedBlockManager scannedBlockManager;
    private static WandManager wandManager;
    private static STLManager stlMng;
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private static final Set<BlockPos> pendingVolumePoints = new LinkedHashSet<>();

    public static void initialize(ScannedBlockManager blockManager, WandManager wManager, STLManager stlManager) {
        scannedBlockManager = blockManager;
        wandManager = wManager;
        stlMng = stlManager;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("selection")
                .then(Commands.literal("show").executes(MineFabCommands::showSelection))
                .then(Commands.literal("info").executes(MineFabCommands::showSelectionInfo))
                .then(Commands.literal("save")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(MineFabCommands::saveSelection)))
                .then(Commands.literal("import")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(MineFabCommands::importSelection)))
                .then(Commands.literal("list").executes(MineFabCommands::listSelections))
                .then(Commands.literal("delete").executes(MineFabCommands::deleteSelection))
                .then(Commands.literal("add")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(MineFabCommands::addPendingVolumePoint)))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("y", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(MineFabCommands::removePendingVolumePoint)))))
                .then(Commands.literal("points")
                        .then(Commands.literal("add")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(MineFabCommands::addPendingVolumePoint)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("y", IntegerArgumentType.integer())
                                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                                        .executes(MineFabCommands::removePendingVolumePoint)))))
                        .then(Commands.literal("list").executes(MineFabCommands::listPendingVolumePoints))
                        .then(Commands.literal("clear").executes(MineFabCommands::clearPendingVolumePoints))
                        .then(Commands.literal("fill").executes(MineFabCommands::fillSelectionFromPendingPoints))
                        .then(Commands.literal("info").executes(MineFabCommands::showPendingPointsInfo)))
                .then(Commands.literal("fillscanned").executes(MineFabCommands::fillSelectionFromScannedBounds))
                .then(Commands.literal("fillbounds").executes(MineFabCommands::fillSelectionFromScannedBounds))
                .then(Commands.literal("export")
                        .then(Commands.argument("filename", StringArgumentType.string())
                                .executes(MineFabCommands::exportSelection)))
                .then(Commands.literal("geometry")
                        .executes(MineFabCommands::showGeometryAll)
                        .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                .executes(MineFabCommands::showGeometryByIndex))));

        dispatcher.register(Commands.literal("wand")
                .then(Commands.literal("info").executes(MineFabCommands::wandInfo))
                .then(Commands.literal("clear").executes(MineFabCommands::wandClear)));

        dispatcher.register(Commands.literal("minefab")
                .then(Commands.literal("help").executes(MineFabCommands::showHelp))
                .then(Commands.literal("info").executes(MineFabCommands::showInfo)));
    }

    private static int showSelection(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(getCurrentSelectionFormatted()), false);
        return 1;
    }

    private static int saveSelection(CommandContext<CommandSourceStack> context) {
        String filename = StringArgumentType.getString(context, "filename");
        String result = saveSelectionToFile(context, filename, getCurrentSelectionJson());
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int importSelection(CommandContext<CommandSourceStack> context) {
        String filename = StringArgumentType.getString(context, "filename");
        String result = loadSelectionFromFile(context, filename);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int listSelections(CommandContext<CommandSourceStack> context) {
        String result = listSavedSelections(context);
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int deleteSelection(CommandContext<CommandSourceStack> context) {
        String result = clearWandSelection();
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int addPendingVolumePoint(CommandContext<CommandSourceStack> context) {
        BlockPos pos = getCommandBlockPos(context);
        boolean added = pendingVolumePoints.add(pos);
        String message = added
                ? "Added volume point " + formatBlockPos(pos) + ". Pending points: " + pendingVolumePoints.size()
                : "Volume point " + formatBlockPos(pos) + " is already in the pending list.";
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int removePendingVolumePoint(CommandContext<CommandSourceStack> context) {
        BlockPos pos = getCommandBlockPos(context);
        boolean removed = pendingVolumePoints.remove(pos);
        String message = removed
                ? "Removed volume point " + formatBlockPos(pos) + ". Pending points: " + pendingVolumePoints.size()
                : "Volume point " + formatBlockPos(pos) + " was not in the pending list.";
        context.getSource().sendSuccess(() -> Component.literal(message), false);
        return 1;
    }

    private static int listPendingVolumePoints(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(getPendingPointsSummary()), false);
        return 1;
    }

    private static int clearPendingVolumePoints(CommandContext<CommandSourceStack> context) {
        int count = pendingVolumePoints.size();
        pendingVolumePoints.clear();
        context.getSource().sendSuccess(() -> Component.literal("Cleared " + count + " pending volume point" + (count == 1 ? "" : "s") + "."), false);
        return 1;
    }

    private static int fillSelectionFromPendingPoints(CommandContext<CommandSourceStack> context) {
        if (pendingVolumePoints.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No pending volume points. Use /selection add <x> <y> <z> first."));
            return 0;
        }

        FillResult result = fillSelectionFromPositions(new ArrayList<>(pendingVolumePoints), context, "pending point list");
        context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
        return result.success() ? 1 : 0;
    }

    private static int fillSelectionFromScannedBounds(CommandContext<CommandSourceStack> context) {
        if (scannedBlockManager == null) {
            context.getSource().sendFailure(Component.literal("ScannedBlockManager not initialized"));
            return 0;
        }

        List<BlockPos> scannedPositions = scannedBlockManager.getScannedBlocks().stream()
                .map(scannedBlock -> scannedBlock.pos)
                .collect(Collectors.toList());

        if (scannedPositions.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No scanned blocks selected. Scan blocks first, then run /selection fillscanned."));
            return 0;
        }

        FillResult result = fillSelectionFromPositions(scannedPositions, context, "current scanned selection");
        context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
        return result.success() ? 1 : 0;
    }

    private static int exportSelection(CommandContext<CommandSourceStack> context) {
        String filename = StringArgumentType.getString(context, "filename");
        context.getSource().sendSuccess(() -> Component.literal("Exporting selection to " + filename + ".stl is not yet implemented."), false);
        return 1;
    }

    private static int wandInfo(CommandContext<CommandSourceStack> context) {
        if (wandManager == null) {
            context.getSource().sendFailure(Component.literal("WandManager not initialized"));
            return 0;
        }
        int scannedCount = scannedBlockManager != null ? scannedBlockManager.getScannedBlocks().size() : 0;
        String info = wandManager.getWandInfo() + "\n" +
                "Selected blocks: " + scannedCount + "\n" +
                "Usage: right-click with the wand to add a block, shift-right-click to remove one.";
        context.getSource().sendSuccess(() -> Component.literal(info), false);
        return 1;
    }

    private static int wandClear(CommandContext<CommandSourceStack> context) {
        String result = clearWandSelection();
        context.getSource().sendSuccess(() -> Component.literal(result), false);
        return 1;
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "MineFab Commands:\n" +
                        "/selection show - Display current selection\n" +
                        "/selection info - Show selection and pending-volume summary\n" +
                        "/selection save <filename> - Save selection to file\n" +
                        "/selection import <filename> - Load selection from file\n" +
                        "/selection list - List saved selections\n" +
                        "/selection delete - Clear current selection\n" +
                        "/selection add <x> <y> <z> - Add a point to the pending volume-point list\n" +
                        "/selection remove <x> <y> <z> - Remove a point from the pending volume-point list\n" +
                        "/selection points list - Show pending volume points\n" +
                        "/selection points clear - Clear pending volume points\n" +
                        "/selection points fill - Fill the bounds of pending points into the block manager\n" +
                        "/selection fillscanned - Fill the bounds of the current scanned blocks into the block manager\n" +
                        "/selection export <filename> - Export current selection to a STL\n" +
                        "/selection geometry [index] - Show block geometry info\n" +
                        "/wand info - Show wand information\n" +
                        "/wand clear - Clear wand selection\n" +
                        "/minefab help - Show this help message\n" +
                        "/minefab info - Show mod information"
        ), false);
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(getModInfoSummary()), false);
        return 1;
    }

    private static int showSelectionInfo(CommandContext<CommandSourceStack> context) {
        String summary = getCurrentSelectionFormatted() + "\n\n" + getPendingPointsSummary();
        context.getSource().sendSuccess(() -> Component.literal(summary), false);
        return 1;
    }

    private static int showPendingPointsInfo(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(getPendingPointsSummary()), false);
        return 1;
    }

    private static String getCurrentSelectionJson() {
        if (scannedBlockManager == null) {
            return "{\"blocks\": [], \"count\": 0, \"error\": \"ScannedBlockManager not initialized\"}";
        }

        List<ScannedBlock> blocks = scannedBlockManager.getScannedBlocks();

        SelectionData data = new SelectionData();
        data.count = blocks.size();
        data.blocks = blocks.stream().map(block -> {
            BlockData bd = new BlockData();
            bd.x = block.pos.getX();
            bd.y = block.pos.getY();
            bd.z = block.pos.getZ();
            bd.translationKey = block.block.getDescriptionId();
            return bd;
        }).collect(Collectors.toList());

        return gson.toJson(data);
    }

    private static String getCurrentSelectionFormatted() {
        if (scannedBlockManager == null) {
            return "Error: ScannedBlockManager not initialized";
        }

        List<ScannedBlock> blocks = scannedBlockManager.getScannedBlocks();
        if (blocks.isEmpty()) {
            return "No blocks selected";
        }

        StringBuilder sb = new StringBuilder("Current selection (" + blocks.size() + " blocks):\n");
        for (int i = 0; i < Math.min(blocks.size(), 10); i++) {
            ScannedBlock block = blocks.get(i);
            sb.append(String.format("  %d. %s at (%d, %d, %d)\n",
                    i + 1, block.block.getName().getString(),
                    block.pos.getX(), block.pos.getY(), block.pos.getZ()));
        }

        if (blocks.size() > 10) {
            sb.append("  ... and ").append(blocks.size() - 10).append(" more blocks");
        }

        SelectionBounds bounds = calculateBounds(blocks.stream().map(scannedBlock -> scannedBlock.pos).collect(Collectors.toList()));
        if (bounds != null) {
            sb.append("\nBounds: ").append(bounds.toDisplayString());
            sb.append("\nBounding volume: ").append(bounds.volume()).append(" blocks");
        }

        return sb.toString();
    }

    private static String getPendingPointsSummary() {
        if (pendingVolumePoints.isEmpty()) {
            return "Pending volume points: none";
        }

        List<BlockPos> points = new ArrayList<>(pendingVolumePoints);
        StringBuilder sb = new StringBuilder("Pending volume points (")
                .append(points.size())
                .append(points.size() == 1 ? " point" : " points")
                .append("):\n");

        for (int i = 0; i < Math.min(points.size(), 10); i++) {
            sb.append("  ").append(i + 1).append(". ").append(formatBlockPos(points.get(i))).append("\n");
        }

        if (points.size() > 10) {
            sb.append("  ... and ").append(points.size() - 10).append(" more points\n");
        }

        SelectionBounds bounds = calculateBounds(points);
        if (bounds != null) {
            sb.append("Bounds: ").append(bounds.toDisplayString()).append("\n");
            sb.append("Bounding volume: ").append(bounds.volume()).append(" blocks");
        }

        return sb.toString();
    }

    private static String getModInfoSummary() {
        int selectionCount = scannedBlockManager != null ? scannedBlockManager.getScannedBlocks().size() : 0;
        SelectionBounds selectionBounds = scannedBlockManager == null
                ? null
                : calculateBounds(scannedBlockManager.getScannedBlocks().stream()
                .map(scannedBlock -> scannedBlock.pos)
                .collect(Collectors.toList()));
        SelectionBounds pendingBounds = calculateBounds(new ArrayList<>(pendingVolumePoints));

        StringBuilder sb = new StringBuilder("MineFab v1.0 - Advanced block selection and manipulation mod\n");
        sb.append("Current selection: ").append(selectionCount).append(selectionCount == 1 ? " block" : " blocks").append("\n");
        if (selectionBounds != null) {
            sb.append("Selection bounds: ").append(selectionBounds.toDisplayString()).append("\n");
            sb.append("Selection bounding volume: ").append(selectionBounds.volume()).append(" blocks\n");
        }
        sb.append("Pending volume points: ").append(pendingVolumePoints.size()).append("\n");
        if (pendingBounds != null) {
            sb.append("Pending point bounds: ").append(pendingBounds.toDisplayString()).append("\n");
            sb.append("Pending point bounding volume: ").append(pendingBounds.volume()).append(" blocks\n");
        }
        sb.append("Use /minefab help to see all available commands.");
        return sb.toString();
    }

    private static String clearWandSelection() {
        if (scannedBlockManager == null) {
            return "Error: ScannedBlockManager not initialized";
        }

        List<ScannedBlock> blocks = scannedBlockManager.getScannedBlocks();
        int count = blocks.size();

        scannedBlockManager.deleteSelection();
        return "Cleared " + count + " blocks from selection";
    }

    private static String saveSelectionToFile(CommandContext<CommandSourceStack> context,
                                              String filename, String selectionData) {
        try {
            Path saveDir = getMineFabDataDir(context);
            Files.createDirectories(saveDir);
            Path filePath = saveDir.resolve(filename + ".json");
            Files.writeString(filePath, selectionData);
            return "Selection saved to " + filename + ".json";
        } catch (IOException e) {
            return "Error saving selection: " + e.getMessage();
        }
    }

    private static String loadSelectionFromFile(CommandContext<CommandSourceStack> context, String filename) {
        try {
            Path saveDir = getMineFabDataDir(context);
            Path filePath = saveDir.resolve(filename + ".json");
            if (!Files.exists(filePath)) {
                return "File not found: " + filename + ".json";
            }
            String content = Files.readString(filePath);

            SelectionData selectionData = gson.fromJson(content, SelectionData.class);
            if (selectionData == null || selectionData.blocks == null) {
                return "File loaded but contained no blocks";
            }

            for (BlockData blockData : selectionData.blocks) {
                BlockPos pos = new BlockPos(blockData.x, blockData.y, blockData.z);
                Block block = getBlockFromTranslationKey(blockData.translationKey);
                if (block != null) {
                    scannedBlockManager.addScannedBlock(block, pos, context.getSource().getLevel());
                }
            }

            return "Loaded selection from " + filename + ".json";
        } catch (IOException e) {
            return "Error loading selection: " + e.getMessage();
        }
    }

    private static String listSavedSelections(CommandContext<CommandSourceStack> context) {
        try {
            Path saveDir = getMineFabDataDir(context);
            if (!Files.exists(saveDir)) {
                return "No saved selections found";
            }

            StringBuilder result = new StringBuilder("Saved selections:\n");
            try (var stream = Files.list(saveDir)) {
                stream.filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> result.append("- ").append(path.getFileName()).append("\n"));
            }
            return result.toString();
        } catch (IOException e) {
            return "Error listing selections: " + e.getMessage();
        }
    }

    private static Path getMineFabDataDir(CommandContext<CommandSourceStack> context) {
        return context.getSource().getServer().getWorldPath(LevelResource.ROOT).resolve("minefab_data");
    }

    private static class SelectionData {
        public int count;
        public List<BlockData> blocks;
    }

    private static class BlockData {
        public int x;
        public int y;
        public int z;
        public String translationKey;
    }

    private static Block getBlockFromTranslationKey(String translationKey) {
        try {
            String registryName = translationKey.replace("block.", "");
            String[] parts = registryName.split("\\.", 2);
            if (parts.length != 2) {
                return null;
            }
            Identifier id = Identifier.fromNamespaceAndPath(parts[0], parts[1]);
            return BuiltInRegistries.BLOCK.getValue(id);
        } catch (Exception e) {
            System.err.println("Failed to get block from translation key: " + translationKey);
            return null;
        }
    }

    private static FillResult fillSelectionFromPositions(List<BlockPos> positions,
                                                         CommandContext<CommandSourceStack> context,
                                                         String sourceDescription) {
        if (scannedBlockManager == null) {
            return new FillResult(false, "ScannedBlockManager not initialized");
        }
        if (positions == null || positions.isEmpty()) {
            return new FillResult(false, "No positions were provided to fill.");
        }

        SelectionBounds bounds = calculateBounds(positions);
        if (bounds == null) {
            return new FillResult(false, "Could not calculate bounds from the provided positions.");
        }

        ScannedBlockManager.BulkAddResult bulkAddResult = scannedBlockManager.addScannedBlocksInBounds(
                new BlockPos(bounds.minX, bounds.minY, bounds.minZ),
                new BlockPos(bounds.maxX, bounds.maxY, bounds.maxZ),
                context.getSource().getLevel()
        );

        String message = "Filled bounds from " + sourceDescription + ": " + bounds.toDisplayString() +
                " (volume " + bulkAddResult.requestedVolume() + ")" +
                " -> added " + bulkAddResult.addedCount() + " blocks" +
                ", skipped " + bulkAddResult.alreadyTrackedCount() + " already tracked" +
                ", skipped " + bulkAddResult.skippedCount() + " non-solid/invalid blocks.";

        return new FillResult(true, message);
    }

    private static SelectionBounds calculateBounds(List<BlockPos> positions) {
        if (positions == null || positions.isEmpty()) {
            return null;
        }

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : positions) {
            if (pos == null) {
                continue;
            }
            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());
            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        if (minX == Integer.MAX_VALUE) {
            return null;
        }

        return new SelectionBounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static BlockPos getCommandBlockPos(CommandContext<CommandSourceStack> context) {
        return new BlockPos(
                IntegerArgumentType.getInteger(context, "x"),
                IntegerArgumentType.getInteger(context, "y"),
                IntegerArgumentType.getInteger(context, "z")
        );
    }

    private static String formatBlockPos(BlockPos pos) {
        return "(" + pos.getX() + ", " + pos.getY() + ", " + pos.getZ() + ")";
    }

    private record FillResult(boolean success, String message) {
    }

    private record SelectionBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        private long volume() {
            return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
        }

        private String toDisplayString() {
            return "min(" + minX + ", " + minY + ", " + minZ + ") max(" + maxX + ", " + maxY + ", " + maxZ + ")";
        }
    }

    private static int showGeometryAll(CommandContext<CommandSourceStack> context) {
        if (scannedBlockManager == null) {
            context.getSource().sendFailure(Component.literal("ScannedBlockManager not initialized"));
            return 0;
        }

        List<ScannedBlock> blocks = scannedBlockManager.getScannedBlocks();
        if (blocks.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No blocks in selection"), false);
            return 0;
        }

        StringBuilder result = new StringBuilder();
        result.append("Geometry for all ").append(blocks.size()).append(" blocks:\n");

        for (int i = 0; i < blocks.size(); i++) {
            ScannedBlock scannedBlock = blocks.get(i);
            result.append("\n--- Block ").append(i + 1).append(" ---\n");
            result.append("Block: ").append(scannedBlock.block.getName().getString()).append("\n");
            result.append("Position: ").append(scannedBlock.pos).append("\n");

            List<double[]> geometry = scannedBlock.getGeometry();
            result.append("Faces: ").append(geometry.size()).append("\n");

            int facesToShow = Math.min(geometry.size(), 3);
            for (int j = 0; j < facesToShow; j++) {
                double[] face = geometry.get(j);
                result.append(String.format("  Face %d: [%.2f,%.2f,%.2f] [%.2f,%.2f,%.2f] [%.2f,%.2f,%.2f]\n",
                        j + 1,
                        face[0], face[1], face[2],
                        face[3], face[4], face[5],
                        face[6], face[7], face[8]));
            }
            if (geometry.size() > facesToShow) {
                result.append("  ... and ").append(geometry.size() - facesToShow).append(" more faces\n");
            }
        }

        context.getSource().sendSuccess(() -> Component.literal(result.toString()), false);
        return 1;
    }

    private static int showGeometryByIndex(CommandContext<CommandSourceStack> context) {
        if (scannedBlockManager == null) {
            context.getSource().sendFailure(Component.literal("ScannedBlockManager not initialized"));
            return 0;
        }

        int index = IntegerArgumentType.getInteger(context, "index");
        List<ScannedBlock> blocks = scannedBlockManager.getScannedBlocks();

        if (blocks.isEmpty()) {
            context.getSource().sendSuccess(() -> Component.literal("No blocks in selection"), false);
            return 0;
        }

        if (index < 0 || index >= blocks.size()) {
            context.getSource().sendFailure(Component.literal("Invalid index. Valid range: 0-" + (blocks.size() - 1)));
            return 0;
        }

        ScannedBlock scannedBlock = blocks.get(index);
        StringBuilder result = new StringBuilder();

        result.append("Geometry for block at index ").append(index).append(":\n");
        result.append("Block: ").append(scannedBlock.block.getName().getString()).append("\n");
        result.append("Position: ").append(scannedBlock.pos).append("\n");

        List<double[]> geometry = scannedBlock.getGeometry();
        result.append("Total faces: ").append(geometry.size()).append("\n\n");

        for (int i = 0; i < geometry.size(); i++) {
            double[] face = geometry.get(i);
            result.append(String.format("Face %d: Triangle [%.2f,%.2f,%.2f] [%.2f,%.2f,%.2f] [%.2f,%.2f,%.2f]\n",
                    i + 1,
                    face[0], face[1], face[2],
                    face[3], face[4], face[5],
                    face[6], face[7], face[8]));
        }

        context.getSource().sendSuccess(() -> Component.literal(result.toString()), false);
        return 1;
    }

}