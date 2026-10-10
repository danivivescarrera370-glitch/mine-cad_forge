package name.minecad.ui;

import net.minecraft.client.gui.screens.Screen;

/**
 * Tracks which inventory screen (if any) currently has the custom tab window open.
 * Mixins read this to suppress vanilla hover/tooltip behaviour underneath the window.
 */
public final class InventoryOverlayState {
	private static Screen blockingScreen;

	private InventoryOverlayState() {
	}

	public static void setBlockingScreen(Screen screen) {
		blockingScreen = screen;
	}

	public static void clear(Screen screen) {
		if (blockingScreen == screen) {
			blockingScreen = null;
		}
	}

	public static boolean isBlocking(Object screen) {
		return blockingScreen != null && blockingScreen == screen;
	}
}

