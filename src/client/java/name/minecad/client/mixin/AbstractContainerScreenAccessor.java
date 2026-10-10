package name.minecad.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int minecad$getLeftPos();

	@Accessor("topPos")
	int minecad$getTopPos();

	@Accessor("imageWidth")
	int minecad$getImageWidth();

	@Accessor("imageHeight")
	int minecad$getImageHeight();
}

