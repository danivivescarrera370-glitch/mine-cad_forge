package name.minecad.client;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class MyGuiScreen extends Screen {
    private final Screen parent;

    public MyGuiScreen(Screen parent) {
        super(Component.literal("My GUI"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(
                Button.builder(Component.literal("Back"), button -> onClose())
                        .bounds(this.width / 2 - 50, this.height - 40, 100, 20)
                        .build()
        );
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(parent);
    }
}


