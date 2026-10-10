package name.minecad.client;

import name.minecad.client.commands.MineFabClientCommands;
import name.minecad.client.render.SelectionOutlineRenderer;
import name.minecad.ui.InventoryTab;
import name.minecad.ui.InventoryTabManager;
import name.minecad.ui.InventoryTabWindow;
import name.minecad.ui.MineFabSettings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.network.chat.Component;

public class MineCadClient implements ClientModInitializer {
	private static InventoryTabManager inventoryTabManager;
	private static int pendingTabsOffsetX = 200;
	private static int pendingTabsOffsetY = 0;

	public static void setAdditionalTabsOffset(int xOffset, int yOffset) {
		pendingTabsOffsetX = xOffset;
		pendingTabsOffsetY = yOffset;
		if (inventoryTabManager != null) {
			inventoryTabManager.setAdditionalTabsOffset(xOffset, yOffset);
		}
	}

	public static InventoryTabManager getInventoryTabManager() {
		if (inventoryTabManager == null) {
			throw new IllegalStateException("InventoryTabManager is not initialized yet");
		}
		return inventoryTabManager;
	}



	@Override
	public void onInitializeClient() {
		MineFabSettings.load();
		MineFabClientCommands.register();
		SelectionOutlineRenderer.register();

		InventoryTabWindow mainWindow = new InventoryTabWindow(176, 166);
		mainWindow.setTitle(Component.literal("MineCad"));
		mainWindow.setPadding(4); // keeps children away from the main window edges

		// Two windows inside the main window: a top one and a bottom one, each half of the usable height.
		InventoryTabWindow topWindow = mainWindow.addChild(new InventoryTabWindow(100, 50));
		topWindow.setTitle(Component.literal("3D"));
		topWindow.setBackgroundColor(0xFFFFFFFF);
		topWindow.setAnchor(InventoryTabWindow.Anchor.TOP);
		topWindow.setMargin(2); // space around the window
		topWindow.setRelativeSize(1.0, 0.85); // scales with the inventory in survival/creative/etc.c.

		InventoryTabWindow bottomWindow = mainWindow.addChild(new InventoryTabWindow(100, 10));
		bottomWindow.setTitle(Component.literal("Inspector"));
		bottomWindow.setBackgroundColor(0xFFECECEC);
		bottomWindow.setAnchor(InventoryTabWindow.Anchor.BOTTOM);
		bottomWindow.setMargin(2);
		bottomWindow.setRelativeSize(1.0, .15); // scales with the inventory in survival/creative/etc.

		// Set click listeners for the windows to log click events
		topWindow.setClickListener((window, localX, localY, button) ->
				System.out.println("Clicked top window at local (" + localX + ", " + localY + "), button " + button));
		bottomWindow.setClickListener((window, localX, localY, button) ->
				System.out.println("Clicked bottom window at local (" + localX + ", " + localY + "), button " + button));
		mainWindow.setClickListener((window, localX, localY, button) ->
				System.out.println("Clicked main window at local (" + localX + ", " + localY + "), button " + button));
		// end of click listeners


		InventoryTab mineCadTab = new InventoryTab(0, 0, Component.literal("My GUI"), null, false, () -> {}, mainWindow);
		inventoryTabManager = new InventoryTabManager(mineCadTab);
		inventoryTabManager.setAdditionalTabsOffset(pendingTabsOffsetX, pendingTabsOffsetY);



		// Add custom tabs whenever the player inventory screen is initialized.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> inventoryTabManager.registerForScreen(client, screen, width, height));


		// debug log a message to indicate that the client mod has been initialized
		System.out.println("MineCadClient initialized");


	}
}