package name.minecad.client.mixin;

import name.minecad.ui.InventoryOverlayState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {
	// Vanilla creative tabs show their name tooltip on hover; suppress it while the custom window is open.
	@Inject(method = "checkTabHovering", at = @At("HEAD"), cancellable = true)
	private void minecad$noTabHover(GuiGraphicsExtractor graphics, CreativeModeTab tab, int mouseX, int mouseY, CallbackInfoReturnable<Boolean> cir) {
		if (InventoryOverlayState.isBlocking(this)) {
			cir.setReturnValue(false);
		}
	}
}

