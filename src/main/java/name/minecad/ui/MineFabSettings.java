package name.minecad.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public class MineFabSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("mine-cad-settings.json");

	private static final boolean DEFAULT_OUTLINES_ENABLED = true;
	private static final boolean DEFAULT_HOVERED_OUTLINE_ENABLED = true;
	private static final int DEFAULT_SELECTED_OUTLINE_COLOR = 0xFF00FF00;
	private static final int DEFAULT_HOVERED_OUTLINE_COLOR = 0xFFFFB000;
	private static final float DEFAULT_OUTLINE_STROKE_WIDTH = 2.0F;
	private static final float MIN_STROKE_WIDTH = 0.5F;
	private static final float MAX_STROKE_WIDTH = 8.0F;

	private static MineFabSettings active = createDefault();

	private boolean outlinesEnabled;
	private boolean hoveredOutlineEnabled;
	private int selectedOutlineColor;
	private int hoveredOutlineColor;
	private float outlineStrokeWidth;

	public MineFabSettings() {
		this(DEFAULT_OUTLINES_ENABLED, DEFAULT_HOVERED_OUTLINE_ENABLED,
				DEFAULT_SELECTED_OUTLINE_COLOR, DEFAULT_HOVERED_OUTLINE_COLOR,
				DEFAULT_OUTLINE_STROKE_WIDTH);
	}

	public MineFabSettings(boolean outlinesEnabled, boolean hoveredOutlineEnabled,
						   int selectedOutlineColor, int hoveredOutlineColor,
						   float outlineStrokeWidth) {
		this.outlinesEnabled = outlinesEnabled;
		this.hoveredOutlineEnabled = hoveredOutlineEnabled;
		this.selectedOutlineColor = normalizeColor(selectedOutlineColor);
		this.hoveredOutlineColor = normalizeColor(hoveredOutlineColor);
		this.outlineStrokeWidth = clampStrokeWidth(outlineStrokeWidth);
	}

	public static synchronized MineFabSettings get() {
		return active;
	}

	public static synchronized void load() {
		if (!Files.exists(SETTINGS_FILE)) {
			active = createDefault();
			save();
			return;
		}

		try (Reader reader = Files.newBufferedReader(SETTINGS_FILE)) {
			MineFabSettings loaded = GSON.fromJson(reader, MineFabSettings.class);
			active = loaded == null ? createDefault() : sanitize(loaded);
		} catch (IOException | RuntimeException e) {
			System.err.println("Failed to load MineCad settings: " + e.getMessage());
			active = createDefault();
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(SETTINGS_FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE)) {
				GSON.toJson(active, writer);
			}
		} catch (IOException e) {
			System.err.println("Failed to save MineCad settings: " + e.getMessage());
		}
	}

	public static synchronized boolean areOutlinesEnabled() {
		return active.outlinesEnabled;
	}

	public static synchronized void setOutlinesEnabled(boolean enabled) {
		active.outlinesEnabled = enabled;
		save();
	}

	public static synchronized boolean isHoveredOutlineEnabled() {
		return active.hoveredOutlineEnabled;
	}

	public static synchronized void setHoveredOutlineEnabled(boolean enabled) {
		active.hoveredOutlineEnabled = enabled;
		save();
	}

	public static synchronized int getSelectedOutlineColor() {
		return active.selectedOutlineColor;
	}

	public static synchronized void setSelectedOutlineColor(int color) {
		active.selectedOutlineColor = normalizeColor(color);
		save();
	}

	public static synchronized int getHoveredOutlineColor() {
		return active.hoveredOutlineColor;
	}

	public static synchronized void setHoveredOutlineColor(int color) {
		active.hoveredOutlineColor = normalizeColor(color);
		save();
	}

	public static synchronized float getOutlineStrokeWidth() {
		return active.outlineStrokeWidth;
	}

	public static synchronized void setOutlineStrokeWidth(float strokeWidth) {
		active.outlineStrokeWidth = clampStrokeWidth(strokeWidth);
		save();
	}

	public static synchronized void resetToDefaults() {
		active = createDefault();
		save();
	}

	public static float getMinStrokeWidth() {
		return MIN_STROKE_WIDTH;
	}

	public static float getMaxStrokeWidth() {
		return MAX_STROKE_WIDTH;
	}

	public static String formatColor(int color) {
		return String.format(Locale.ROOT, "#%08X", normalizeColor(color));
	}

	public static int parseColor(String colorValue) {
		String normalized = colorValue.trim();

		if (normalized.startsWith("#")) {
			normalized = normalized.substring(1);
		}
		if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
			normalized = normalized.substring(2);
		}

		if (normalized.length() == 6) {
			return (int) (0xFF000000L | Long.parseLong(normalized, 16));
		}
		if (normalized.length() == 8) {
			return (int) Long.parseLong(normalized, 16);
		}

		throw new IllegalArgumentException("Expected color in RRGGBB or AARRGGBB format");
	}

	private static MineFabSettings createDefault() {
		return new MineFabSettings();
	}

	private static MineFabSettings sanitize(MineFabSettings settings) {
		return new MineFabSettings(
				settings.outlinesEnabled,
				settings.hoveredOutlineEnabled,
				settings.selectedOutlineColor,
				settings.hoveredOutlineColor,
				settings.outlineStrokeWidth
		);
	}

	private static int normalizeColor(int color) {
		return color | 0xFF000000;
	}

	private static float clampStrokeWidth(float strokeWidth) {
		if (Float.isNaN(strokeWidth) || Float.isInfinite(strokeWidth)) {
			return DEFAULT_OUTLINE_STROKE_WIDTH;
		}
		return Math.max(MIN_STROKE_WIDTH, Math.min(MAX_STROKE_WIDTH, strokeWidth));
	}
}
