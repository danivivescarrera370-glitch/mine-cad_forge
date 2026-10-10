package name.minecad.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class InventoryTabWindow {
  private static final int DEFAULT_BACKGROUND_COLOR = 0xFFC6C6C6;
  private static final int BORDER_COLOR = 0xFF000000;
  private static final int HIGHLIGHT_COLOR = 0xFFFFFFFF;
  private static final int SHADOW_COLOR = 0xFF555555;

  private int width;
  private int height;
  private final String backgroundTextureFilePath;
  private int backgroundColor = DEFAULT_BACKGROUND_COLOR;
  private Component title = Component.literal("MineCad");

  public InventoryTabWindow(int width, int height, String backgroundTextureFilePath) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Window width and height must be positive");
    }
    this.width = width;
    this.height = height;
    this.backgroundTextureFilePath = backgroundTextureFilePath;
  }

  public InventoryTabWindow(int width, int height) {
    this(width, height, null);
  }

  public void setBackgroundColor(int argbColor) {
    backgroundColor = argbColor;
  }

  public void setTitle(Component title) {
    this.title = title == null ? Component.empty() : title;
  }

  public void setSize(int width, int height) {
    if (width <= 0 || height <= 0) {
      throw new IllegalArgumentException("Window width and height must be positive");
    }
    this.width = width;
    this.height = height;
  }

  public String getBackgroundTextureFilePath() {
    return backgroundTextureFilePath;
  }

  public void drawWindow(GuiGraphicsExtractor graphics, Font font, int x, int y) {

    graphics.fill(x, y, x + width, y + height, BORDER_COLOR);
    graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, backgroundColor);
    graphics.fill(x + 2, y + 2, x + width - 2, y + 3, HIGHLIGHT_COLOR);
    graphics.fill(x + 2, y + height - 3, x + width - 2, y + height - 2, SHADOW_COLOR);
    graphics.fill(x + width - 3, y + 2, x + width - 2, y + height - 2, SHADOW_COLOR);

    if (!title.getString().isEmpty()) {
      graphics.text(font, title, x + 8, y + 8, 0xFF404040, false);
    }
  }
}
