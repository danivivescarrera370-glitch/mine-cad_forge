package name.minecad.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/**
 * A clickable button that lives inside an InventoryTabWindow.
 *
 * It is itself a window, so it supports the same layout calls as any window
 * (setAnchor, setMargin, setOffset, setRelativeSize, centerWindow, ...), and it takes part in the
 * same hit testing and click dispatching. Create one with window.addButton("Label", () -> ...)
 * or build it yourself and add it with window.addChild(button).
 */
public class InventoryTabWindowButton extends InventoryTabWindow {
  private static final int DEFAULT_WIDTH = 60;
  private static final int DEFAULT_HEIGHT = 20;

  private static final int BORDER = 0xFF000000;
  private static final int HIGHLIGHT = 0xFFFFFFFF;
  private static final int SHADOW = 0xFF555555;
  private static final int FACE = 0xFFC6C6C6;
  private static final int FACE_HOVER = 0xFFDADADA;
  private static final int FACE_DISABLED = 0xFF8B8B8B;
  private static final int TEXT = 0xFF202020;
  private static final int TEXT_DISABLED = 0xFF606060;
  private static final int TEXT_HEIGHT = 8;

  private Component label;
  private Runnable action;
  private boolean enabled = true;

  public InventoryTabWindowButton(int width, int height, Component label, Runnable action) {
    super(width, height);
    setTitle(Component.empty());
    this.label = label == null ? Component.empty() : label;
    this.action = action;
    // Left click presses the button.
    setClickListener((window, localX, localY, mouseButton) -> {
      if (mouseButton == 0) {
        press();
      }
    });
  }

  public InventoryTabWindowButton(Component label, Runnable action) {
    this(DEFAULT_WIDTH, DEFAULT_HEIGHT, label, action);
  }

  /** Sets what happens when the button is clicked. */
  public void setAction(Runnable action) {
    this.action = action;
  }

  public void setLabel(Component label) {
    this.label = label == null ? Component.empty() : label;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isEnabled() {
    return enabled;
  }

  /** Runs the button's action (if enabled). Also usable to trigger the button from code. */
  public void press() {
    if (!enabled || action == null) {
      return;
    }
    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    action.run();
  }

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    int w = getWidth();
    int h = getHeight();
    boolean hovered = enabled && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;

    int face = !enabled ? FACE_DISABLED : hovered ? FACE_HOVER : FACE;
    graphics.fill(x, y, x + w, y + h, BORDER);
    graphics.fill(x + 1, y + 1, x + w - 1, y + h - 1, face);
    graphics.fill(x + 1, y + 1, x + w - 1, y + 2, HIGHLIGHT);
    graphics.fill(x + 1, y + 1, x + 2, y + h - 1, HIGHLIGHT);
    graphics.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, SHADOW);
    graphics.fill(x + w - 2, y + 1, x + w - 1, y + h - 1, SHADOW);

    int textX = x + (w - font.width(label)) / 2;
    int textY = y + (h - TEXT_HEIGHT) / 2;
    graphics.text(font, label, textX, textY, enabled ? TEXT : TEXT_DISABLED, false);
  }
}

