package name.minecad.ui;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;

public class MineFabSettings {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path SETTINGS_FILE = FabricLoader.getInstance().getConfigDir().resolve("mine-cad-settings.json");
	private static final String BUNDLED_DEFAULTS_RESOURCE = "mine-cad_default_settings_config.json";

	private static final double DEFAULT_METERS_PER_BLOCK = 1.0D;
	private static final double MIN_METERS_PER_BLOCK = 0.000001D;
	private static final double MAX_METERS_PER_BLOCK = 1_000_000D;
	private static final float MIN_STROKE_WIDTH = 0.5F;
	private static final float MAX_STROKE_WIDTH = 8.0F;
	private static final int DEFAULT_MAX_RENDER_BLOCKS = 100_000;
	private static final int MIN_MAX_RENDER_BLOCKS = 1;
	private static final int MAX_MAX_RENDER_BLOCKS = 5_000_000;
	private static final int DEFAULT_INTERACTIVE_RENDER_BLOCKS = 25_000;

	private static final String DEFAULT_MEASUREMENT_UNIT = "meters";
	private static final int DEFAULT_OUTLINE_COLOR = 0xFFFF0000;
	private static final float DEFAULT_OUTLINE_THICKNESS = 2.0F;
	private static final int DEFAULT_VIEWPORT_BG = 0xFFFFFFFF;
	private static final double DEFAULT_FACTOR = 1.0D;
	private static final String DEFAULT_WAND_ITEM = "minecraft:stick";

	private static MineFabSettings active = createDefault();

	// Flattened runtime fields; serialized via SettingsDocument only.
	private boolean outlineEnabled = true;
	private int outlineColor = DEFAULT_OUTLINE_COLOR;
	private float outlineThickness = DEFAULT_OUTLINE_THICKNESS;

	private int viewportBackgroundColor = DEFAULT_VIEWPORT_BG;
	private double mouseSensitivity = DEFAULT_FACTOR;
	private boolean mouseAccelerationEnabled = true;
	private double mouseAccelerationFactor = DEFAULT_FACTOR;
	private double cameraSpeed = DEFAULT_FACTOR;
	private boolean cameraAccelerationEnabled = true;
	private double cameraAccelerationFactor = DEFAULT_FACTOR;
	private int maxRenderBlocks = DEFAULT_MAX_RENDER_BLOCKS;
	private int interactiveRenderBlocks = DEFAULT_INTERACTIVE_RENDER_BLOCKS;

	private String keybindToggleOutline = "O";
	private String keybindCameraRotation = "MOUSE_SCROLL_CLICK";
	private String keybindCameraZoom = "MOUSE_SCROLL";
	private String keybindCameraPan = "MOUSE_RIGHT_CLICK";
	private String keybindCameraReset = "R";
	private String keybindCameraSpeedIncrease = "SHIFT";
	private String keybindCameraSpeedDecrease = "CTRL";
	private String keybindCameraSpeedReset = "ALT";
	private String keybindCameraIn = "MOUSE_SCROLL_CLICK + W";
	private String keybindCameraOut = "MOUSE_SCROLL_CLICK + S";
	private String keybindCameraLeft = "MOUSE_SCROLL_CLICK + A";
	private String keybindCameraRight = "MOUSE_SCROLL_CLICK + D";
	private String keybindCameraUp = "MOUSE_SCROLL_CLICK + E";
	private String keybindCameraDown = "MOUSE_SCROLL_CLICK + Q";

	private String measurementUnit = DEFAULT_MEASUREMENT_UNIT;
	private double metersPerBlock = DEFAULT_METERS_PER_BLOCK;

	private boolean wandEnabled = true;
	private String wandDefaultItem = DEFAULT_WAND_ITEM;

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
			SettingsDocument doc = GSON.fromJson(reader, SettingsDocument.class);
			if (doc == null || doc.settings == null) {
				active = createDefault();
			} else {
				active = sanitize(fromDocument(doc));
			}
		} catch (IOException | RuntimeException e) {
			System.err.println("Failed to load MineCad settings: " + e.getMessage());
			active = createDefault();
		}
	}

	public static synchronized void save() {
		try {
			Files.createDirectories(SETTINGS_FILE.getParent());
			try (Writer writer = Files.newBufferedWriter(SETTINGS_FILE)) {
				GSON.toJson(toDocument(active), writer);
			}
		} catch (IOException e) {
			System.err.println("Failed to save MineCad settings: " + e.getMessage());
		}
	}

	public static synchronized boolean areOutlinesEnabled() {
		return active.outlineEnabled;
	}

	public static synchronized void setOutlinesEnabled(boolean enabled) {
		active.outlineEnabled = enabled;
		save();
	}

	// Kept for compatibility: no separate hovered toggle in schema.
	public static synchronized boolean isHoveredOutlineEnabled() {
		return active.outlineEnabled;
	}

	public static synchronized void setHoveredOutlineEnabled(boolean enabled) {
		setOutlinesEnabled(enabled);
	}

	// Kept for compatibility: schema has one outline color.
	public static synchronized int getSelectedOutlineColor() {
		return active.outlineColor;
	}

	public static synchronized void setSelectedOutlineColor(int color) {
		active.outlineColor = normalizeColor(color);
		save();
	}

	public static synchronized int getHoveredOutlineColor() {
		return active.outlineColor;
	}

	public static synchronized void setHoveredOutlineColor(int color) {
		setSelectedOutlineColor(color);
	}

	public static synchronized float getOutlineStrokeWidth() {
		return active.outlineThickness;
	}

	public static synchronized void setOutlineStrokeWidth(float strokeWidth) {
		active.outlineThickness = clampStrokeWidth(strokeWidth);
		save();
	}

	public static synchronized void resetToDefaults() {
		active = createDefault();
		save();
	}

	public static synchronized double getMetersPerBlock() {
		return active.metersPerBlock;
	}

	public static synchronized void setMetersPerBlock(double metersPerBlock) {
		active.metersPerBlock = clampMetersPerBlock(metersPerBlock);
		save();
	}

	public static synchronized double blocksToMeters(double blocks) {
		return blocks * active.metersPerBlock;
	}

	public static synchronized String getMeasurementUnit() {
		return active.measurementUnit;
	}

	public static synchronized int getViewportBackgroundColor() {
		return active.viewportBackgroundColor;
	}

	public static synchronized double getMouseSensitivity() {
		return active.mouseSensitivity;
	}

	public static synchronized boolean isMouseAccelerationEnabled() {
		return active.mouseAccelerationEnabled;
	}

	public static synchronized double getMouseAccelerationFactor() {
		return active.mouseAccelerationFactor;
	}

	public static synchronized double getCameraSpeed() {
		return active.cameraSpeed;
	}

	public static synchronized boolean isCameraAccelerationEnabled() {
		return active.cameraAccelerationEnabled;
	}

	public static synchronized double getCameraAccelerationFactor() {
		return active.cameraAccelerationFactor;
	}

	public static synchronized int getMaxRenderBlocks() {
		return active.maxRenderBlocks;
	}

	public static synchronized void setMaxRenderBlocks(int maxRenderBlocks) {
		active.maxRenderBlocks = clampMaxRenderBlocks(maxRenderBlocks);
		save();
	}

	public static int getMinMaxRenderBlocks() {
		return MIN_MAX_RENDER_BLOCKS;
	}

	public static int getMaxMaxRenderBlocks() {
		return MAX_MAX_RENDER_BLOCKS;
	}

	public static synchronized int getInteractiveRenderBlocks() {
		return active.interactiveRenderBlocks;
	}

	public static synchronized void setInteractiveRenderBlocks(int interactiveRenderBlocks) {
		active.interactiveRenderBlocks = clampMaxRenderBlocks(interactiveRenderBlocks);
		save();
	}

	public static synchronized boolean isWandEnabled() {
		return active.wandEnabled;
	}

	public static synchronized String getWandDefaultItem() {
		return active.wandDefaultItem;
	}

	public static synchronized String getSettingValue(String key) {
		String k = normalizeKey(key);
		return switch (k) {
			case "outline_settings.enabled" -> Boolean.toString(active.outlineEnabled);
			case "outline_settings.color" -> formatColor(active.outlineColor);
			case "outline_settings.thickness" -> formatNumber(active.outlineThickness);
			case "3d_viewport_settings.background_color" -> formatColor(active.viewportBackgroundColor);
			case "3d_viewport_settings.mouse_sensitivity" -> formatNumber(active.mouseSensitivity);
			case "3d_viewport_settings.mouse_acceleration_enabled" -> Boolean.toString(active.mouseAccelerationEnabled);
			case "3d_viewport_settings.mouse_acceleration_factor" -> formatNumber(active.mouseAccelerationFactor);
			case "3d_viewport_settings.camera_speed" -> formatNumber(active.cameraSpeed);
			case "3d_viewport_settings.camera_acceleration_enabled" -> Boolean.toString(active.cameraAccelerationEnabled);
			case "3d_viewport_settings.camera_acceleration_factor" -> formatNumber(active.cameraAccelerationFactor);
			case "3d_viewport_settings.max_render_blocks" -> Integer.toString(active.maxRenderBlocks);
			case "3d_viewport_settings.interactive_render_blocks" -> Integer.toString(active.interactiveRenderBlocks);
			case "keybinds.toggle_outline" -> active.keybindToggleOutline;
			case "keybinds.3d_viewport_camera_rotation" -> active.keybindCameraRotation;
			case "keybinds.3d_viewport_camera_zoom" -> active.keybindCameraZoom;
			case "keybinds.3d_viewport_camera_pan" -> active.keybindCameraPan;
			case "keybinds.3d_viewport_camera_reset" -> active.keybindCameraReset;
			case "keybinds.3d_viewport_camera_speed_increase" -> active.keybindCameraSpeedIncrease;
			case "keybinds.3d_viewport_camera_speed_decrease" -> active.keybindCameraSpeedDecrease;
			case "keybinds.3d_viewport_camera_speed_reset" -> active.keybindCameraSpeedReset;
			case "keybinds.3d_viewport_camera_in" -> active.keybindCameraIn;
			case "keybinds.3d_viewport_camera_out" -> active.keybindCameraOut;
			case "keybinds.3d_viewport_camera_left" -> active.keybindCameraLeft;
			case "keybinds.3d_viewport_camera_right" -> active.keybindCameraRight;
			case "keybinds.3d_viewport_camera_up" -> active.keybindCameraUp;
			case "keybinds.3d_viewport_camera_down" -> active.keybindCameraDown;
			case "measurements.unit" -> active.measurementUnit;
			case "measurements.block_to_unit_scale" -> formatNumber(active.metersPerBlock);
			case "wand.enabled" -> Boolean.toString(active.wandEnabled);
			case "wand.default_wand_item" -> active.wandDefaultItem;
			default -> throw new IllegalArgumentException("Unknown settings key: " + key);
		};
	}

	public static synchronized void setSettingValue(String key, String value) {
		String k = normalizeKey(key);
		String raw = value == null ? "" : value.trim();
		switch (k) {
			case "outline_settings.enabled" -> active.outlineEnabled = parseBooleanValue(raw);
			case "outline_settings.color" -> active.outlineColor = normalizeColor(parseColor(raw));
			case "outline_settings.thickness" -> active.outlineThickness = clampStrokeWidth(Float.parseFloat(raw));
			case "3d_viewport_settings.background_color" -> active.viewportBackgroundColor = normalizeColor(parseColor(raw));
			case "3d_viewport_settings.mouse_sensitivity" -> active.mouseSensitivity = clampFactor(Double.parseDouble(raw));
			case "3d_viewport_settings.mouse_acceleration_enabled" -> active.mouseAccelerationEnabled = parseBooleanValue(raw);
			case "3d_viewport_settings.mouse_acceleration_factor" -> active.mouseAccelerationFactor = clampFactor(Double.parseDouble(raw));
			case "3d_viewport_settings.camera_speed" -> active.cameraSpeed = clampFactor(Double.parseDouble(raw));
			case "3d_viewport_settings.camera_acceleration_enabled" -> active.cameraAccelerationEnabled = parseBooleanValue(raw);
			case "3d_viewport_settings.camera_acceleration_factor" -> active.cameraAccelerationFactor = clampFactor(Double.parseDouble(raw));
			case "3d_viewport_settings.max_render_blocks" -> active.maxRenderBlocks = clampMaxRenderBlocks(parseBlockCount(raw));
			case "3d_viewport_settings.interactive_render_blocks" -> active.interactiveRenderBlocks = clampMaxRenderBlocks(parseBlockCount(raw));
			case "keybinds.toggle_outline" -> active.keybindToggleOutline = sanitizeText(raw, active.keybindToggleOutline);
			case "keybinds.3d_viewport_camera_rotation" -> active.keybindCameraRotation = sanitizeText(raw, active.keybindCameraRotation);
			case "keybinds.3d_viewport_camera_zoom" -> active.keybindCameraZoom = sanitizeText(raw, active.keybindCameraZoom);
			case "keybinds.3d_viewport_camera_pan" -> active.keybindCameraPan = sanitizeText(raw, active.keybindCameraPan);
			case "keybinds.3d_viewport_camera_reset" -> active.keybindCameraReset = sanitizeText(raw, active.keybindCameraReset);
			case "keybinds.3d_viewport_camera_speed_increase" -> active.keybindCameraSpeedIncrease = sanitizeText(raw, active.keybindCameraSpeedIncrease);
			case "keybinds.3d_viewport_camera_speed_decrease" -> active.keybindCameraSpeedDecrease = sanitizeText(raw, active.keybindCameraSpeedDecrease);
			case "keybinds.3d_viewport_camera_speed_reset" -> active.keybindCameraSpeedReset = sanitizeText(raw, active.keybindCameraSpeedReset);
			case "keybinds.3d_viewport_camera_in" -> active.keybindCameraIn = sanitizeText(raw, active.keybindCameraIn);
			case "keybinds.3d_viewport_camera_out" -> active.keybindCameraOut = sanitizeText(raw, active.keybindCameraOut);
			case "keybinds.3d_viewport_camera_left" -> active.keybindCameraLeft = sanitizeText(raw, active.keybindCameraLeft);
			case "keybinds.3d_viewport_camera_right" -> active.keybindCameraRight = sanitizeText(raw, active.keybindCameraRight);
			case "keybinds.3d_viewport_camera_up" -> active.keybindCameraUp = sanitizeText(raw, active.keybindCameraUp);
			case "keybinds.3d_viewport_camera_down" -> active.keybindCameraDown = sanitizeText(raw, active.keybindCameraDown);
			case "measurements.unit" -> {
				if (!"meters".equalsIgnoreCase(raw)) {
					throw new IllegalArgumentException("Only 'meters' is supported for measurements.unit");
				}
				active.measurementUnit = DEFAULT_MEASUREMENT_UNIT;
			}
			case "measurements.block_to_unit_scale" -> active.metersPerBlock = clampMetersPerBlock(Double.parseDouble(raw));
			case "wand.enabled" -> active.wandEnabled = parseBooleanValue(raw);
			case "wand.default_wand_item" -> active.wandDefaultItem = sanitizeText(raw, DEFAULT_WAND_ITEM);
			default -> throw new IllegalArgumentException("Unknown settings key: " + key);
		}
		save();
	}

	public static String formatNumber(double value) {
		return new java.math.BigDecimal(value).round(new java.math.MathContext(8)).stripTrailingZeros().toPlainString();
	}

	public static String formatColor(int color) {
		return String.format(Locale.ROOT, "#%08X", normalizeColor(color));
	}

	public static int parseColor(String colorValue) {
		String normalized = colorValue == null ? "" : colorValue.trim();
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

	public static double getMinMetersPerBlock() {
		return MIN_METERS_PER_BLOCK;
	}

	public static double getMaxMetersPerBlock() {
		return MAX_METERS_PER_BLOCK;
	}

	public static float getMinStrokeWidth() {
		return MIN_STROKE_WIDTH;
	}

	public static float getMaxStrokeWidth() {
		return MAX_STROKE_WIDTH;
	}

	private static MineFabSettings createDefault() {
		try (InputStream stream = MineFabSettings.class.getClassLoader().getResourceAsStream(BUNDLED_DEFAULTS_RESOURCE)) {
			if (stream == null) {
				return sanitize(new MineFabSettings());
			}
			SettingsDocument doc = GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), SettingsDocument.class);
			if (doc == null || doc.settings == null) {
				return sanitize(new MineFabSettings());
			}
			return sanitize(fromDocument(doc));
		} catch (IOException | RuntimeException e) {
			System.err.println("Failed to read bundled MineCad defaults: " + e.getMessage());
			return sanitize(new MineFabSettings());
		}
	}

	private static MineFabSettings fromDocument(SettingsDocument doc) {
		MineFabSettings s = new MineFabSettings();
		SettingsNode n = doc.settings;
		if (n.outlineSettings != null) {
			s.outlineEnabled = n.outlineSettings.enabled;
			s.outlineColor = normalizeColor(parseColor(n.outlineSettings.color));
			s.outlineThickness = n.outlineSettings.thickness;
		}
		if (n.viewportSettings != null) {
			s.viewportBackgroundColor = normalizeColor(parseColor(n.viewportSettings.backgroundColor));
			s.mouseSensitivity = n.viewportSettings.mouseSensitivity;
			s.mouseAccelerationEnabled = n.viewportSettings.mouseAccelerationEnabled;
			s.mouseAccelerationFactor = n.viewportSettings.mouseAccelerationFactor;
			s.cameraSpeed = n.viewportSettings.cameraSpeed;
			s.cameraAccelerationEnabled = n.viewportSettings.cameraAccelerationEnabled;
			s.cameraAccelerationFactor = n.viewportSettings.cameraAccelerationFactor;
			s.maxRenderBlocks = n.viewportSettings.maxRenderBlocks;
			s.interactiveRenderBlocks = n.viewportSettings.interactiveRenderBlocks;
		}
		if (n.keybinds != null) {
			s.keybindToggleOutline = sanitizeText(n.keybinds.toggleOutline, s.keybindToggleOutline);
			s.keybindCameraRotation = sanitizeText(n.keybinds.cameraRotation, s.keybindCameraRotation);
			s.keybindCameraZoom = sanitizeText(n.keybinds.cameraZoom, s.keybindCameraZoom);
			s.keybindCameraPan = sanitizeText(n.keybinds.cameraPan, s.keybindCameraPan);
			s.keybindCameraReset = sanitizeText(n.keybinds.cameraReset, s.keybindCameraReset);
			s.keybindCameraSpeedIncrease = sanitizeText(n.keybinds.cameraSpeedIncrease, s.keybindCameraSpeedIncrease);
			s.keybindCameraSpeedDecrease = sanitizeText(n.keybinds.cameraSpeedDecrease, s.keybindCameraSpeedDecrease);
			s.keybindCameraSpeedReset = sanitizeText(n.keybinds.cameraSpeedReset, s.keybindCameraSpeedReset);
			s.keybindCameraIn = sanitizeText(n.keybinds.cameraIn, s.keybindCameraIn);
			s.keybindCameraOut = sanitizeText(n.keybinds.cameraOut, s.keybindCameraOut);
			s.keybindCameraLeft = sanitizeText(n.keybinds.cameraLeft, s.keybindCameraLeft);
			s.keybindCameraRight = sanitizeText(n.keybinds.cameraRight, s.keybindCameraRight);
			s.keybindCameraUp = sanitizeText(n.keybinds.cameraUp, s.keybindCameraUp);
			s.keybindCameraDown = sanitizeText(n.keybinds.cameraDown, s.keybindCameraDown);
		}
		if (n.measurements != null) {
			s.measurementUnit = "meters";
			s.metersPerBlock = n.measurements.blockToUnitScale;
		}
		if (n.wand != null) {
			s.wandEnabled = n.wand.enabled;
			s.wandDefaultItem = sanitizeText(n.wand.defaultWandItem, DEFAULT_WAND_ITEM);
		}
		return s;
	}

	private static SettingsDocument toDocument(MineFabSettings s) {
		SettingsDocument doc = new SettingsDocument();
		doc.settings = new SettingsNode();
		doc.settings.outlineSettings = new OutlineSettingsNode();
		doc.settings.outlineSettings.enabled = s.outlineEnabled;
		doc.settings.outlineSettings.color = formatColor(s.outlineColor);
		doc.settings.outlineSettings.thickness = s.outlineThickness;

		doc.settings.viewportSettings = new ViewportSettingsNode();
		doc.settings.viewportSettings.backgroundColor = formatColor(s.viewportBackgroundColor);
		doc.settings.viewportSettings.mouseSensitivity = s.mouseSensitivity;
		doc.settings.viewportSettings.mouseAccelerationEnabled = s.mouseAccelerationEnabled;
		doc.settings.viewportSettings.mouseAccelerationFactor = s.mouseAccelerationFactor;
		doc.settings.viewportSettings.cameraSpeed = s.cameraSpeed;
		doc.settings.viewportSettings.cameraAccelerationEnabled = s.cameraAccelerationEnabled;
		doc.settings.viewportSettings.cameraAccelerationFactor = s.cameraAccelerationFactor;
		doc.settings.viewportSettings.maxRenderBlocks = s.maxRenderBlocks;
		doc.settings.viewportSettings.interactiveRenderBlocks = s.interactiveRenderBlocks;

		doc.settings.keybinds = new KeybindsNode();
		doc.settings.keybinds.toggleOutline = s.keybindToggleOutline;
		doc.settings.keybinds.cameraRotation = s.keybindCameraRotation;
		doc.settings.keybinds.cameraZoom = s.keybindCameraZoom;
		doc.settings.keybinds.cameraPan = s.keybindCameraPan;
		doc.settings.keybinds.cameraReset = s.keybindCameraReset;
		doc.settings.keybinds.cameraSpeedIncrease = s.keybindCameraSpeedIncrease;
		doc.settings.keybinds.cameraSpeedDecrease = s.keybindCameraSpeedDecrease;
		doc.settings.keybinds.cameraSpeedReset = s.keybindCameraSpeedReset;
		doc.settings.keybinds.cameraIn = s.keybindCameraIn;
		doc.settings.keybinds.cameraOut = s.keybindCameraOut;
		doc.settings.keybinds.cameraLeft = s.keybindCameraLeft;
		doc.settings.keybinds.cameraRight = s.keybindCameraRight;
		doc.settings.keybinds.cameraUp = s.keybindCameraUp;
		doc.settings.keybinds.cameraDown = s.keybindCameraDown;

		doc.settings.measurements = new MeasurementsNode();
		doc.settings.measurements.unit = DEFAULT_MEASUREMENT_UNIT;
		doc.settings.measurements.blockToUnitScale = s.metersPerBlock;

		doc.settings.wand = new WandNode();
		doc.settings.wand.enabled = s.wandEnabled;
		doc.settings.wand.defaultWandItem = s.wandDefaultItem;
		return doc;
	}

	private static MineFabSettings sanitize(MineFabSettings s) {
		s.outlineColor = normalizeColor(s.outlineColor);
		s.outlineThickness = clampStrokeWidth(s.outlineThickness);
		s.viewportBackgroundColor = normalizeColor(s.viewportBackgroundColor);
		s.mouseSensitivity = clampFactor(s.mouseSensitivity);
		s.mouseAccelerationFactor = clampFactor(s.mouseAccelerationFactor);
		s.cameraSpeed = clampFactor(s.cameraSpeed);
		s.cameraAccelerationFactor = clampFactor(s.cameraAccelerationFactor);
		s.maxRenderBlocks = clampMaxRenderBlocks(s.maxRenderBlocks);
		s.interactiveRenderBlocks = clampMaxRenderBlocks(s.interactiveRenderBlocks);
		s.metersPerBlock = clampMetersPerBlock(s.metersPerBlock);
		s.measurementUnit = DEFAULT_MEASUREMENT_UNIT;
		s.wandDefaultItem = sanitizeText(s.wandDefaultItem, DEFAULT_WAND_ITEM);
		s.keybindToggleOutline = sanitizeText(s.keybindToggleOutline, "O");
		s.keybindCameraRotation = sanitizeText(s.keybindCameraRotation, "MOUSE_SCROLL_CLICK");
		s.keybindCameraZoom = sanitizeText(s.keybindCameraZoom, "MOUSE_SCROLL");
		s.keybindCameraPan = sanitizeText(s.keybindCameraPan, "MOUSE_RIGHT_CLICK");
		s.keybindCameraReset = sanitizeText(s.keybindCameraReset, "R");
		s.keybindCameraSpeedIncrease = sanitizeText(s.keybindCameraSpeedIncrease, "SHIFT");
		s.keybindCameraSpeedDecrease = sanitizeText(s.keybindCameraSpeedDecrease, "CTRL");
		s.keybindCameraSpeedReset = sanitizeText(s.keybindCameraSpeedReset, "ALT");
		s.keybindCameraIn = sanitizeText(s.keybindCameraIn, "MOUSE_SCROLL_CLICK + W");
		s.keybindCameraOut = sanitizeText(s.keybindCameraOut, "MOUSE_SCROLL_CLICK + S");
		s.keybindCameraLeft = sanitizeText(s.keybindCameraLeft, "MOUSE_SCROLL_CLICK + A");
		s.keybindCameraRight = sanitizeText(s.keybindCameraRight, "MOUSE_SCROLL_CLICK + D");
		s.keybindCameraUp = sanitizeText(s.keybindCameraUp, "MOUSE_SCROLL_CLICK + E");
		s.keybindCameraDown = sanitizeText(s.keybindCameraDown, "MOUSE_SCROLL_CLICK + Q");
		return s;
	}

	private static String normalizeKey(String key) {
		String normalized = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
		return normalized.startsWith("settings.") ? normalized.substring("settings.".length()) : normalized;
	}

	private static boolean parseBooleanValue(String value) {
		return switch (value.toLowerCase(Locale.ROOT)) {
			case "true", "on", "enable", "enabled", "yes", "1" -> true;
			case "false", "off", "disable", "disabled", "no", "0" -> false;
			default -> throw new IllegalArgumentException("Expected boolean value (on/off, true/false) but got: " + value);
		};
	}

	private static String sanitizeText(String value, String fallback) {
		if (value == null) {
			return fallback;
		}
		String trimmed = value.trim();
		return trimmed.isEmpty() ? fallback : trimmed;
	}

	private static double clampMetersPerBlock(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0D) {
			return DEFAULT_METERS_PER_BLOCK;
		}
		return Math.max(MIN_METERS_PER_BLOCK, Math.min(MAX_METERS_PER_BLOCK, value));
	}

	private static double clampFactor(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value) || value <= 0.0D) {
			return DEFAULT_FACTOR;
		}
		return Math.max(0.000001D, Math.min(1_000_000D, value));
	}

	private static int clampMaxRenderBlocks(int value) {
		if (value <= 0) {
			return DEFAULT_MAX_RENDER_BLOCKS;
		}
		return Math.max(MIN_MAX_RENDER_BLOCKS, Math.min(MAX_MAX_RENDER_BLOCKS, value));
	}

	/** Accepts plain integers and tolerates separators/decimals such as "100,000", "100_000" or "1e5". */
	private static int parseBlockCount(String raw) {
		String cleaned = raw.replace(",", "").replace("_", "").trim();
		double parsed = Double.parseDouble(cleaned);
		if (Double.isNaN(parsed) || Double.isInfinite(parsed)) {
			throw new IllegalArgumentException("Expected a whole number of blocks but got: " + raw);
		}
		return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(parsed)));
	}

	private static int normalizeColor(int color) {
		return color | 0xFF000000;
	}

	private static float clampStrokeWidth(float strokeWidth) {
		if (Float.isNaN(strokeWidth) || Float.isInfinite(strokeWidth)) {
			return DEFAULT_OUTLINE_THICKNESS;
		}
		return Math.max(MIN_STROKE_WIDTH, Math.min(MAX_STROKE_WIDTH, strokeWidth));
	}

	private static class SettingsDocument {
		private SettingsNode settings;
	}

	private static class SettingsNode {
		@SerializedName("outline_settings")
		private OutlineSettingsNode outlineSettings;
		@SerializedName("3d_viewport_settings")
		private ViewportSettingsNode viewportSettings;
		private KeybindsNode keybinds;
		private MeasurementsNode measurements;
		private WandNode wand;
	}

	private static class OutlineSettingsNode {
		private boolean enabled = true;
		private String color = "#FF0000";
		private float thickness = 2.0F;
	}

	private static class ViewportSettingsNode {
		@SerializedName("background_color")
		private String backgroundColor = "#FFFFFF";
		@SerializedName("mouse_sensitivity")
		private double mouseSensitivity = 1.0D;
		@SerializedName("mouse_acceleration_enabled")
		private boolean mouseAccelerationEnabled = true;
		@SerializedName("mouse_acceleration_factor")
		private double mouseAccelerationFactor = 1.0D;
		@SerializedName("camera_speed")
		private double cameraSpeed = 1.0D;
		@SerializedName("camera_acceleration_enabled")
		private boolean cameraAccelerationEnabled = true;
		@SerializedName("camera_acceleration_factor")
		private double cameraAccelerationFactor = 1.0D;
		@SerializedName("max_render_blocks")
		private int maxRenderBlocks = 100_000;
		@SerializedName("interactive_render_blocks")
		private int interactiveRenderBlocks = 25_000;
	}

	private static class KeybindsNode {
		@SerializedName("toggle_outline")
		private String toggleOutline = "O";
		@SerializedName("3d_viewport_camera_rotation")
		private String cameraRotation = "MOUSE_SCROLL_CLICK";
		@SerializedName("3d_viewport_camera_zoom")
		private String cameraZoom = "MOUSE_SCROLL";
		@SerializedName("3d_viewport_camera_pan")
		private String cameraPan = "MOUSE_RIGHT_CLICK";
		@SerializedName("3d_viewport_camera_reset")
		private String cameraReset = "R";
		@SerializedName("3d_viewport_camera_speed_increase")
		private String cameraSpeedIncrease = "SHIFT";
		@SerializedName("3d_viewport_camera_speed_decrease")
		private String cameraSpeedDecrease = "CTRL";
		@SerializedName("3d_viewport_camera_speed_reset")
		private String cameraSpeedReset = "ALT";
		@SerializedName("3d_viewport_camera_in")
		private String cameraIn = "MOUSE_SCROLL_CLICK + W";
		@SerializedName("3d_viewport_camera_out")
		private String cameraOut = "MOUSE_SCROLL_CLICK + S";
		@SerializedName("3d_viewport_camera_left")
		private String cameraLeft = "MOUSE_SCROLL_CLICK + A";
		@SerializedName("3d_viewport_camera_right")
		private String cameraRight = "MOUSE_SCROLL_CLICK + D";
		@SerializedName("3d_viewport_camera_up")
		private String cameraUp = "MOUSE_SCROLL_CLICK + E";
		@SerializedName("3d_viewport_camera_down")
		private String cameraDown = "MOUSE_SCROLL_CLICK + Q";
	}

	private static class MeasurementsNode {
		private String unit = "meters";
		@SerializedName("block_to_unit_scale")
		private double blockToUnitScale = 1.0D;
	}

	private static class WandNode {
		private boolean enabled = true;
		@SerializedName("default_wand_item")
		private String defaultWandItem = "minecraft:stick";
	}
}
