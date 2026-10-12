package name.minecad.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * A compact selectable tab button meant for the left-side tab list inside MineCad windows.
 */
public class InventorySideTabButton extends InventoryTabWindow {
  private static final int DEFAULT_WIDTH = 60;
  private static final int DEFAULT_HEIGHT = 20;

  private static final int BORDER = 0xFF000000;
  private static final int HIGHLIGHT = 0xFFFFFFFF;
  private static final int SHADOW = 0xFF555555;
  private static final int FACE = 0xFFC6C6C6;
  private static final int FACE_HOVER = 0xFFDADADA;
  private static final int FACE_SELECTED = 0xFFEAF2FF;
  private static final int TEXT = 0xFF202020;

  private Component label;
  private Runnable action;
  private boolean selected;

  public InventorySideTabButton(int width, int height, Component label, Runnable action) {
    super(width, height);
    setTitle(Component.empty());
    this.label = label == null ? Component.empty() : label;
    this.action = action;
    setClickListener((window, localX, localY, mouseButton) -> {
      if (mouseButton == 0 && this.action != null) {
        this.action.run();
      }
    });
  }

  public InventorySideTabButton(Component label, Runnable action) {
    this(DEFAULT_WIDTH, DEFAULT_HEIGHT, label, action);
  }

  public void setSelected(boolean selected) {
    this.selected = selected;
  }

  public void setLabel(Component label) {
    this.label = label == null ? Component.empty() : label;
  }

  public void setAction(Runnable action) {
    this.action = action;
  }

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    int w = getWidth();
    int h = getHeight();
    boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

    int face = selected ? FACE_SELECTED : hovered ? FACE_HOVER : FACE;
    graphics.fill(x, y, x + w, y + h, BORDER);
    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
    graphics.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
    graphics.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
    graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
    graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);

    int textX = x + (w - font.width(label)) / 2;
    int textY = y + (h - font.lineHeight) / 2;
    graphics.text(font, label, textX, textY, TEXT, false);
  }
}

