package name.minecad.player.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import name.minecad.export.SelectionExporter;
import name.minecad.scannedblock.ScannedBlock;
import name.minecad.scannedblock.ScannedBlockManager;
import name.minecad.stl.STLManager;
import name.minecad.ui.MineFabSettings;
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
        dispatcher.register(Commands.literal("minecad")
                .then(Commands.literal("selection")
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
                                        .executes(MineFabCommands::exportSelectionDefault))
                                .then(Commands.argument("format", StringArgumentType.word())
                                        .then(Commands.argument("filename", StringArgumentType.string())
                                                .executes(MineFabCommands::exportSelectionWithFormat))))
                        .then(Commands.literal("geometry")
                                .executes(MineFabCommands::showGeometryAll)
                                .then(Commands.argument("index", IntegerArgumentType.integer(0))
                                        .executes(MineFabCommands::showGeometryByIndex)))
                        .then(Commands.literal("scale")
                                .executes(MineFabCommands::showScale)
                                .then(Commands.argument("metersPerBlock",
                                                DoubleArgumentType.doubleArg(MineFabSettings.getMinMetersPerBlock(), MineFabSettings.getMaxMetersPerBlock()))
                                        .executes(MineFabCommands::setScale))))
                .then(Commands.literal("wand")
                        .then(Commands.literal("info").executes(MineFabCommands::wandInfo))
                        .then(Commands.literal("clear").executes(MineFabCommands::wandClear)))
                .then(Commands.literal("help").executes(MineFabCommands::showHelp))
                .then(Commands.literal("info").executes(MineFabCommands::showInfo))
                .then(Commands.literal("settings")
                        .executes(MineFabCommands::showSettings)
                        .then(Commands.literal("show").executes(MineFabCommands::showSettings))
                        .then(Commands.literal("list").executes(MineFabCommands::listSettingsKeys))
                        .then(Commands.literal("get")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(MineFabCommands::getSettingsValueByKey)))
                        .then(Commands.literal("set")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(MineFabCommands::setSettingsValueByKey))))
                        .then(Commands.literal("reset").executes(MineFabCommands::resetSettings))
                        .then(Commands.literal("scale")
                                .executes(MineFabCommands::showSettingsScale)
                                .then(Commands.argument("metersPerBlock",
                                                DoubleArgumentType.doubleArg(MineFabSettings.getMinMetersPerBlock(), MineFabSettings.getMaxMetersPerBlock()))
                                        .executes(MineFabCommands::setSettingsScale)))
                        .then(Commands.literal("outlines")
                                .then(Commands.argument("enabled", StringArgumentType.word())
                                        .executes(MineFabCommands::setOutlinesEnabledFromSettings)))
                        .then(Commands.literal("outlinecolor")
                                .then(Commands.argument("color", StringArgumentType.word())
                                        .executes(MineFabCommands::setOutlineColorFromSettings)))
                        .then(Commands.literal("outlinethickness")
                                .then(Commands.argument("width",
                                                DoubleArgumentType.doubleArg(MineFabSettings.getMinStrokeWidth(), MineFabSettings.getMaxStrokeWidth()))
                                        .executes(MineFabCommands::setOutlineStrokeWidthFromSettings)))));
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
            context.getSource().sendFailure(Component.literal("No pending volume points. Use /minecad selection add <x> <y> <z> first."));
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
            context.getSource().sendFailure(Component.literal("No scanned blocks selected. Scan blocks first, then run /minecad selection fillscanned."));
            return 0;
        }

        FillResult result = fillSelectionFromPositions(scannedPositions, context, "current scanned selection");
        context.getSource().sendSuccess(() -> Component.literal(result.message()), false);
        return result.success() ? 1 : 0;
    }

    private static int exportSelectionDefault(CommandContext<CommandSourceStack> context) {
        String filename = StringArgumentType.getString(context, "filename");
        return exportSelection(context, SelectionExporter.Format.STL, filename);
    }

    private static int exportSelectionWithFormat(CommandContext<CommandSourceStack> context) {
        String formatRaw = StringArgumentType.getString(context, "format");
        String filename = StringArgumentType.getString(context, "filename");
        final SelectionExporter.Format format;
        try {
            format = SelectionExporter.Format.parse(formatRaw);
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
        return exportSelection(context, format, filename);
    }

    private static int exportSelection(CommandContext<CommandSourceStack> context,
                                       SelectionExporter.Format format,
                                       String filename) {
        if (scannedBlockManager == null) {
            context.getSource().sendFailure(Component.literal("ScannedBlockManager not initialized"));
            return 0;
        }

        String normalized = sanitizeBaseFileName(filename);
        if (normalized.isEmpty()) {
            context.getSource().sendFailure(Component.literal("Please provide a valid file name."));
            return 0;
        }

        List<ScannedBlock> blocks = scannedBlockManager.getScannedBlocks();
        if (blocks.isEmpty()) {
            context.getSource().sendFailure(Component.literal("No blocks selected to export."));
            return 0;
        }

        // Commands run on the common/server side; reload persisted settings so export uses the latest saved scale.
        MineFabSettings.load();
        double metersPerBlock = MineFabSettings.getMetersPerBlock();

        Path exportDir = getMineFabDataDir(context).resolve("exports");
        Path filePath = exportDir.resolve(stripKnownExtension(normalized) + "." + format.extension());
        SelectionExporter.ExportResult result = SelectionExporter.export(
                context.getSource().getLevel(),
                blocks,
                format,
                filePath,
                metersPerBlock
        );

        if (!result.success()) {
            context.getSource().sendFailure(Component.literal(result.message()));
            return 0;
        }

        String relativeOutput = getMineFabDataDir(context).getParent().relativize(filePath).toString();
        context.getSource().sendSuccess(() -> Component.literal(
                result.message() + " [" + format.name().toLowerCase(Locale.ROOT) + "] at " + relativeOutput
                        + " (scale: 1 block = " + MineFabSettings.formatNumber(metersPerBlock) + " m)"
                        + (format == SelectionExporter.Format.STL ? " [STL units: mm]" : " [units: m]")
        ), false);
        return 1;
    }

    private static int showScale(CommandContext<CommandSourceStack> context) {
        double scale = MineFabSettings.getMetersPerBlock();
        context.getSource().sendSuccess(() -> Component.literal(
                "Scale: 1 block = " + MineFabSettings.formatNumber(scale) + " meter" + (scale == 1.0D ? "" : "s")), false);
        return 1;
    }

    private static int setScale(CommandContext<CommandSourceStack> context) {
        double metersPerBlock = DoubleArgumentType.getDouble(context, "metersPerBlock");
        MineFabSettings.setMetersPerBlock(metersPerBlock);
        double applied = MineFabSettings.getMetersPerBlock();
        context.getSource().sendSuccess(() -> Component.literal(
                "Scale set: 1 block = " + MineFabSettings.formatNumber(applied) + " meter" + (applied == 1.0D ? "" : "s")), false);
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
                        "/minecad selection show - Display current selection\n" +
                        "/minecad selection info - Show selection and pending-volume summary\n" +
                        "/minecad selection save <filename> - Save selection to file\n" +
                        "/minecad selection import <filename> - Load selection from file\n" +
                        "/minecad selection list - List saved selections\n" +
                        "/minecad selection delete - Clear current selection\n" +
                        "/minecad selection add <x> <y> <z> - Add a point to the pending volume-point list\n" +
                        "/minecad selection remove <x> <y> <z> - Remove a point from the pending volume-point list\n" +
                        "/minecad selection points list - Show pending volume points\n" +
                        "/minecad selection points clear - Clear pending volume points\n" +
                        "/minecad selection points fill - Fill the bounds of pending points into the block manager\n" +
                        "/minecad selection fillscanned - Fill the bounds of the current scanned blocks into the block manager\n" +
                        "/minecad selection export <filename> - Export current selection to STL\n" +
                        "/minecad selection export <obj|stl|fbx> <filename> - Export current selection to a specific format\n" +
                        "/minecad selection geometry [index] - Show block geometry info\n" +
                        "/minecad selection scale [metersPerBlock] - Show or set how many meters 1 block represents (default 1)\n" +
                        "/minecad settings show - Show current settings\n" +
                        "/minecad settings list - List all supported settings keys\n" +
                        "/minecad settings get <key> - Show the current value for a settings key\n" +
                        "/minecad settings set <key> <value> - Set any settings key\n" +
                        "/minecad settings reset - Reset settings to defaults\n" +
                        "/minecad settings scale [metersPerBlock] - Show or set export scale\n" +
                        "/minecad settings outlines <on|off> - Enable/disable selection outlines\n" +
                        "/minecad settings outlinecolor <hex> - Set outline color\n" +
                        "/minecad settings outlinethickness <width> - Set outline thickness\n" +
                        "/minecad wand info - Show wand information\n" +
                        "/minecad wand clear - Clear wand selection\n" +
                        "/minecad help - Show this help message\n" +
                        "/minecad info - Show mod information"
        ), false);
        return 1;
    }

    private static int showInfo(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(getModInfoSummary()), false);
        return 1;
    }

    private static int showSettings(CommandContext<CommandSourceStack> context) {
        MineFabSettings.load();
        context.getSource().sendSuccess(() -> Component.literal(getSettingsSummary()), false);
        return 1;
    }

    private static int resetSettings(CommandContext<CommandSourceStack> context) {
        MineFabSettings.resetToDefaults();
        context.getSource().sendSuccess(() -> Component.literal("MineFab settings reset to defaults.\n" + getSettingsSummary()), false);
        return 1;
    }

    private static int listSettingsKeys(CommandContext<CommandSourceStack> context) {
        context.getSource().sendSuccess(() -> Component.literal(
                "Settings keys:\n"
                        + "- outline_settings.enabled\n"
                        + "- outline_settings.color\n"
                        + "- outline_settings.thickness\n"
                        + "- 3d_viewport_settings.background_color\n"
                        + "- 3d_viewport_settings.mouse_sensitivity\n"
                        + "- 3d_viewport_settings.mouse_acceleration_enabled\n"
                        + "- 3d_viewport_settings.mouse_acceleration_factor\n"
                        + "- 3d_viewport_settings.camera_speed\n"
                        + "- 3d_viewport_settings.camera_acceleration_enabled\n"
                        + "- 3d_viewport_settings.camera_acceleration_factor\n"
                        + "- keybinds.toggle_outline\n"
                        + "- keybinds.3d_viewport_camera_rotation\n"
                        + "- keybinds.3d_viewport_camera_zoom\n"
                        + "- keybinds.3d_viewport_camera_pan\n"
                        + "- keybinds.3d_viewport_camera_reset\n"
                        + "- keybinds.3d_viewport_camera_speed_increase\n"
                        + "- keybinds.3d_viewport_camera_speed_decrease\n"
                        + "- keybinds.3d_viewport_camera_speed_reset\n"
                        + "- keybinds.3d_viewport_camera_in\n"
                        + "- keybinds.3d_viewport_camera_out\n"
                        + "- keybinds.3d_viewport_camera_left\n"
                        + "- keybinds.3d_viewport_camera_right\n"
                        + "- keybinds.3d_viewport_camera_up\n"
                        + "- keybinds.3d_viewport_camera_down\n"
                        + "- measurements.unit\n"
                        + "- measurements.block_to_unit_scale\n"
                        + "- wand.enabled\n"
                        + "- wand.default_wand_item"
        ), false);
        return 1;
    }

    private static int getSettingsValueByKey(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        try {
            String value = MineFabSettings.getSettingValue(key);
            context.getSource().sendSuccess(() -> Component.literal(key + " = " + value), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int setSettingsValueByKey(CommandContext<CommandSourceStack> context) {
        String key = StringArgumentType.getString(context, "key");
        String value = StringArgumentType.getString(context, "value");
        try {
            MineFabSettings.setSettingValue(key, value);
            String applied = MineFabSettings.getSettingValue(key);
            context.getSource().sendSuccess(() -> Component.literal("Set " + key + " = " + applied), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Could not set " + key + ": " + e.getMessage()));
            return 0;
        }
    }

    private static int showSettingsScale(CommandContext<CommandSourceStack> context) {
        return showScale(context);
    }

    private static int setSettingsScale(CommandContext<CommandSourceStack> context) {
        return setScale(context);
    }

    private static int setOutlinesEnabledFromSettings(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "enabled");
        try {
            MineFabSettings.setSettingValue("outline_settings.enabled", raw);
            boolean enabled = Boolean.parseBoolean(MineFabSettings.getSettingValue("outline_settings.enabled"));
            context.getSource().sendSuccess(() -> Component.literal("Outline setting 'enabled' = " + enabled), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }
    }

    private static int setOutlineColorFromSettings(CommandContext<CommandSourceStack> context) {
        String raw = StringArgumentType.getString(context, "color");
        try {
            MineFabSettings.setSettingValue("outline_settings.color", raw);
            String value = MineFabSettings.getSettingValue("outline_settings.color");
            context.getSource().sendSuccess(() -> Component.literal("Outline setting 'color' = " + value), false);
            return 1;
        } catch (IllegalArgumentException e) {
            context.getSource().sendFailure(Component.literal("Invalid color: " + e.getMessage()));
            return 0;
        }
    }

    private static int setOutlineStrokeWidthFromSettings(CommandContext<CommandSourceStack> context) {
        double width = DoubleArgumentType.getDouble(context, "width");
        MineFabSettings.setSettingValue("outline_settings.thickness", MineFabSettings.formatNumber(width));
        String value = MineFabSettings.getSettingValue("outline_settings.thickness");
        context.getSource().sendSuccess(() -> Component.literal("Outline setting 'thickness' = " + value), false);
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
            sb.append("\nSize: ").append(bounds.toScaledSizeString());
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
        sb.append("Use /minecad help to see all available commands.");
        return sb.toString();
    }

    private static String getSettingsSummary() {
        return "MineFab settings:\n"
                + "- Outlines enabled: " + MineFabSettings.areOutlinesEnabled() + "\n"
                + "- Outline color: " + MineFabSettings.getSettingValue("outline_settings.color") + "\n"
                + "- Outline thickness: " + MineFabSettings.getSettingValue("outline_settings.thickness") + "\n"
                + "- Scale: 1 block = " + MineFabSettings.formatNumber(MineFabSettings.getMetersPerBlock()) + " m\n"
                + "- Measurement unit: " + MineFabSettings.getMeasurementUnit() + "\n"
                + "- Viewport background: " + MineFabSettings.formatColor(MineFabSettings.getViewportBackgroundColor()) + "\n"
                + "- Mouse sensitivity: " + MineFabSettings.formatNumber(MineFabSettings.getMouseSensitivity()) + "\n"
                + "- Mouse acceleration: " + MineFabSettings.isMouseAccelerationEnabled() + " (factor "
                + MineFabSettings.formatNumber(MineFabSettings.getMouseAccelerationFactor()) + ")\n"
                + "- Camera speed: " + MineFabSettings.formatNumber(MineFabSettings.getCameraSpeed()) + "\n"
                + "- Camera acceleration: " + MineFabSettings.isCameraAccelerationEnabled() + " (factor "
                + MineFabSettings.formatNumber(MineFabSettings.getCameraAccelerationFactor()) + ")\n"
                + "- Wand enabled: " + MineFabSettings.isWandEnabled() + "\n"
                + "- Wand default item: " + MineFabSettings.getWandDefaultItem();
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

    private static String sanitizeBaseFileName(String rawName) {
        if (rawName == null) {
            return "";
        }
        String trimmed = rawName.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String normalized = trimmed.replace('\\', '_').replace('/', '_');
        normalized = normalized.replaceAll("[^A-Za-z0-9._-]", "_");
        return normalized;
    }

    private static String stripKnownExtension(String filename) {
        String lower = filename.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".obj") || lower.endsWith(".stl") || lower.endsWith(".fbx")) {
            return filename.substring(0, filename.lastIndexOf('.'));
        }
        return filename;
    }

    private static boolean parseBooleanSetting(String raw) {
        String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "true", "on", "enable", "enabled", "yes", "1" -> true;
            case "false", "off", "disable", "disabled", "no", "0" -> false;
            default -> throw new IllegalArgumentException("Expected on/off or true/false but got: " + raw);
        };
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

        private String toScaledSizeString() {
            int sizeX = maxX - minX + 1;
            int sizeY = maxY - minY + 1;
            int sizeZ = maxZ - minZ + 1;
            return sizeX + "x" + sizeY + "x" + sizeZ + " blocks = "
                    + MineFabSettings.formatNumber(MineFabSettings.blocksToMeters(sizeX)) + " x "
                    + MineFabSettings.formatNumber(MineFabSettings.blocksToMeters(sizeY)) + " x "
                    + MineFabSettings.formatNumber(MineFabSettings.blocksToMeters(sizeZ)) + " m (1 block = "
                    + MineFabSettings.formatNumber(MineFabSettings.getMetersPerBlock()) + " m)";
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