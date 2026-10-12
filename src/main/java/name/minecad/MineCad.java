package name.minecad;

import name.minecad.player.commands.MineFabCommands;
import name.minecad.scannedblock.ScannedBlockManager;
import name.minecad.stl.STLManager;
import name.minecad.ui.MineFabSettings;
import name.minecad.wand.Wand;
import name.minecad.wand.WandManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Items;

public class MineCad implements ModInitializer {
	public static final String MOD_ID = "mine-cad";

	private static ScannedBlockManager scannedBlockManager;
	private static WandManager wandManager;
	private static STLManager stlManager;

	@Override
	public void onInitialize() {
		// Load settings on the command/common side too, so exports honor persisted scale values.
		MineFabSettings.load();
		scannedBlockManager = new ScannedBlockManager(20);
		var wand = new Wand(Items.STICK);
		wandManager = new WandManager(wand, scannedBlockManager);
		wandManager.registerEvents();
		stlManager = new STLManager(1);


		// register commands/events here
		 MineFabCommands.initialize(scannedBlockManager, wandManager, stlManager);
		 CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> MineFabCommands.register(dispatcher));

	}

	public static ScannedBlockManager getScannedBlockManager() {
		return scannedBlockManager;
	}

	public static WandManager getWandManager() {
		return wandManager;
	}
}
