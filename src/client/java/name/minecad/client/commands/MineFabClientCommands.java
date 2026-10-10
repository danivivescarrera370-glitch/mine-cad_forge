package name.minecad.client.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.Locale;
import name.minecad.ui.MineFabSettings;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

public final class MineFabClientCommands {
    private MineFabClientCommands() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, buildContext) -> register(dispatcher));
    }

    private static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(ClientCommands.literal("minefabclient")
                .then(ClientCommands.literal("outline")
                        .then(ClientCommands.literal("status")
                                .executes(context -> sendStatus(context.getSource())))
                        .then(ClientCommands.literal("toggle")
                                .executes(context -> toggleOutlines(context.getSource())))
                        .then(ClientCommands.literal("enable")
                                .executes(context -> setOutlinesEnabled(context.getSource(), true)))
                        .then(ClientCommands.literal("disable")
                                .executes(context -> setOutlinesEnabled(context.getSource(), false)))
                        .then(ClientCommands.literal("hovered")
                                .then(ClientCommands.literal("on")
                                        .executes(context -> setHoveredOutline(context.getSource(), true)))
                                .then(ClientCommands.literal("off")
                                        .executes(context -> setHoveredOutline(context.getSource(), false))))
                        .then(ClientCommands.literal("width")
                                .then(ClientCommands.argument("value", FloatArgumentType.floatArg(MineFabSettings.getMinStrokeWidth(), MineFabSettings.getMaxStrokeWidth()))
                                        .executes(context -> setOutlineWidth(context.getSource(), FloatArgumentType.getFloat(context, "value")))))
                        .then(ClientCommands.literal("color")
                                .then(ClientCommands.literal("selected")
                                        .then(ClientCommands.argument("value", StringArgumentType.word())
                                                .executes(context -> setSelectedColor(context.getSource(), StringArgumentType.getString(context, "value")))))
                                .then(ClientCommands.literal("hovered")
                                        .then(ClientCommands.argument("value", StringArgumentType.word())
                                                .executes(context -> setHoveredColor(context.getSource(), StringArgumentType.getString(context, "value"))))))
                        .then(ClientCommands.literal("reset")
                                .executes(context -> resetSettings(context.getSource())))));
    }

    private static int sendStatus(FabricClientCommandSource source) {
        source.sendFeedback(Component.literal(
                "MineCad outline settings:\n" +
                        "- enabled: " + MineFabSettings.areOutlinesEnabled() + "\n" +
                        "- hovered outline: " + MineFabSettings.isHoveredOutlineEnabled() + "\n" +
                        "- selected color: " + MineFabSettings.formatColor(MineFabSettings.getSelectedOutlineColor()) + "\n" +
                        "- hovered color: " + MineFabSettings.formatColor(MineFabSettings.getHoveredOutlineColor()) + "\n" +
                        "- stroke width: " + String.format(Locale.ROOT, "%.2f", MineFabSettings.getOutlineStrokeWidth())
        ));
        return 1;
    }

    private static int toggleOutlines(FabricClientCommandSource source) {
        boolean enabled = !MineFabSettings.areOutlinesEnabled();
        MineFabSettings.setOutlinesEnabled(enabled);
        source.sendFeedback(Component.literal("Selection outlines " + (enabled ? "enabled" : "disabled")));
        return 1;
    }

    private static int setOutlinesEnabled(FabricClientCommandSource source, boolean enabled) {
        MineFabSettings.setOutlinesEnabled(enabled);
        source.sendFeedback(Component.literal("Selection outlines " + (enabled ? "enabled" : "disabled")));
        return 1;
    }

    private static int setHoveredOutline(FabricClientCommandSource source, boolean enabled) {
        MineFabSettings.setHoveredOutlineEnabled(enabled);
        source.sendFeedback(Component.literal("Hovered wand outline " + (enabled ? "enabled" : "disabled")));
        return 1;
    }

    private static int setOutlineWidth(FabricClientCommandSource source, float strokeWidth) {
        MineFabSettings.setOutlineStrokeWidth(strokeWidth);
        source.sendFeedback(Component.literal("Outline width set to " + String.format(Locale.ROOT, "%.2f", MineFabSettings.getOutlineStrokeWidth())));
        return 1;
    }

    private static int setSelectedColor(FabricClientCommandSource source, String value) {
        try {
            MineFabSettings.setSelectedOutlineColor(MineFabSettings.parseColor(value));
            source.sendFeedback(Component.literal("Selected outline color set to " + MineFabSettings.formatColor(MineFabSettings.getSelectedOutlineColor())));
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendError(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int setHoveredColor(FabricClientCommandSource source, String value) {
        try {
            MineFabSettings.setHoveredOutlineColor(MineFabSettings.parseColor(value));
            source.sendFeedback(Component.literal("Hovered outline color set to " + MineFabSettings.formatColor(MineFabSettings.getHoveredOutlineColor())));
            return 1;
        } catch (IllegalArgumentException exception) {
            source.sendError(Component.literal(exception.getMessage()));
            return 0;
        }
    }

    private static int resetSettings(FabricClientCommandSource source) {
        MineFabSettings.resetToDefaults();
        source.sendFeedback(Component.literal("MineCad outline settings reset to defaults"));
        return sendStatus(source);
    }
}


