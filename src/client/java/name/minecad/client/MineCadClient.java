package name.minecad.client;

import name.minecad.client.commands.MineFabClientCommands;
import name.minecad.client.render.SelectionOutlineRenderer;
import name.minecad.ui.InventoryTab;
import name.minecad.ui.InventoryTabManager;
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

		InventoryTab mineCadTab = new InventoryTab(
				0,
				0,
				Component.literal("My GUI"),
				null,
				false,
				() -> {
				}
		);
		inventoryTabManager = new InventoryTabManager(mineCadTab);
		inventoryTabManager.setAdditionalTabsOffset(pendingTabsOffsetX, pendingTabsOffsetY);



		// Add custom tabs whenever the player inventory screen is initialized.
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> inventoryTabManager.registerForScreen(client, screen, width, height));


		// debug log a message to indicate that the client mod has been initialized
		System.out.println("MineCadClient initialized");


	}
}