package name.minecad.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.lwjgl.glfw.GLFW;

/**
 * An orbiting 3D view of the scanned blocks that lives inside an InventoryTabWindow.
 * The scene itself is drawn on the GPU (see {@link ScenePipRenderer}); this window only handles
 * input and tells the renderer where and how to draw.
 *
 * Controls:
 *  - middle-mouse drag rotates the view (cursor hidden and locked while dragging); rotation uses
 *    mouse acceleration, so faster movement rotates more per pixel,
 *  - while the middle button is held: W/S forward/back (zoom), A/D strafe, E/Q up/down,
 *  - the mouse wheel zooms.
 */
public class InventoryTabWindow3DRenderer extends InventoryTabWindow {
  private static final double ROTATE_SENSITIVITY = 0.35;
  private static final double ACCELERATION = 0.15; // extra rotation multiplier per pixel of mouse movement
  private static final double MAX_ACCELERATION = 6.0;
  private static final double MOVE_SPEED = 3.0; // preview units per second
  private static final double CAMERA_DISTANCE = 4.5;
  private static final double SCENE_SCALE = 0.70;
  private static final double DEFAULT_RENDER_SCALE_PERCENT = 0.42;
  private static final double MIN_RENDER_SCALE_PERCENT = 0.10;
  private static final double MAX_RENDER_SCALE_PERCENT = 2.00;
  private static final double ZOOM_STEP = 1.10; // scale change per wheel notch

  private final ScannedBlockScene scene = new ScannedBlockScene();
  private double yawDegrees = 0.0; // looking north from the south side
  private double pitchDegrees = -25.0;
  private boolean dragging;
  private double savedCursorX;
  private double savedCursorY;
  private double renderScalePercent = DEFAULT_RENDER_SCALE_PERCENT;
  // View-space offset of the scene, changed with WASD (and Q/E) while the middle button is held.
  private double offsetX;
  private double offsetY;
  private double offsetZ;
  private long lastMoveNanos;

  public InventoryTabWindow3DRenderer(int width, int height) {
    super(width, height);
    setTitle(Component.literal("3D"));
    setBackgroundColor(0xFFECECEC);
  }

  // ---------------------------------------------------------------- settings

  /**
   * Adjusts how large the scene is rendered relative to the available space.
   * 1.0 uses the full base size, 0.5 renders it at half size, and so on.
   */
  public void setRenderScalePercent(double renderScalePercent) {
    if (renderScalePercent <= 0.0) {
      throw new IllegalArgumentException("Render scale percent must be positive");
    }
    this.renderScalePercent = renderScalePercent;
  }

  // ---------------------------------------------------------------- interaction

  @Override
  public boolean beginMouseDrag(int button) {
    if (button != 2) {
      return false;
    }
    dragging = true;
    lastMoveNanos = System.nanoTime();
    long handle = Minecraft.getInstance().getWindow().handle();
    double[] cursorX = new double[1];
    double[] cursorY = new double[1];
    GLFW.glfwGetCursorPos(handle, cursorX, cursorY);
    savedCursorX = cursorX[0];
    savedCursorY = cursorY[0];
    // Hide and lock the cursor: movement keeps being reported, so the rotation never hits a screen edge.
    GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_DISABLED);
    return true;
  }

  @Override
  public void onMouseDrag(double localX, double localY, double deltaX, double deltaY, int button) {
    if (!dragging || button != 2) {
      return;
    }

    // Mouse acceleration: the faster the mouse moves, the more each pixel rotates.
    double speed = Math.hypot(deltaX, deltaY);
    double factor = Math.min(MAX_ACCELERATION, 1.0 + ACCELERATION * speed);
    yawDegrees += deltaX * ROTATE_SENSITIVITY * factor;
    pitchDegrees -= deltaY * ROTATE_SENSITIVITY * factor;
    pitchDegrees = Math.max(-89.0, Math.min(89.0, pitchDegrees));
  }

  @Override
  public void endMouseDrag(int button) {
    if (button == 2) {
      stopDragging();
    }
  }

  /** Shows the cursor again and puts it back where the drag started. */
  private void stopDragging() {
    if (!dragging) {
      return;
    }
    dragging = false;
    long handle = Minecraft.getInstance().getWindow().handle();
    GLFW.glfwSetInputMode(handle, GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_NORMAL);
    GLFW.glfwSetCursorPos(handle, savedCursorX, savedCursorY);
  }

  /** W/S move forward/back, A/D strafe, E/Q move up/down (polled directly from GLFW). */
  private void applyKeyboardMovement(long handle) {
    long now = System.nanoTime();
    double dt = Math.min(0.1, (now - lastMoveNanos) / 1.0e9);
    lastMoveNanos = now;
    double step = MOVE_SPEED * dt;
    // Objects move opposite to the camera in view space.
    if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_W) == GLFW.GLFW_PRESS) {
      offsetZ -= step;
    }
    if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_S) == GLFW.GLFW_PRESS) {
      offsetZ += step;
    }
    if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_A) == GLFW.GLFW_PRESS) {
      offsetX += step;
    }
    if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_D) == GLFW.GLFW_PRESS) {
      offsetX -= step;
    }
    if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_E) == GLFW.GLFW_PRESS) {
      offsetY -= step;
    }
    if (GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_Q) == GLFW.GLFW_PRESS) {
      offsetY += step;
    }
    offsetZ = Math.max(-CAMERA_DISTANCE + 2.0, Math.min(30.0, offsetZ));
    offsetX = Math.max(-20.0, Math.min(20.0, offsetX));
    offsetY = Math.max(-20.0, Math.min(20.0, offsetY));
  }

  /** Mouse wheel zooms the scene in and out by changing the render scale. */
  @Override
  public boolean onMouseScroll(double localX, double localY, double horizontalAmount, double verticalAmount) {
    if (verticalAmount == 0) {
      return false;
    }
    double scaled = renderScalePercent * Math.pow(ZOOM_STEP, verticalAmount);
    renderScalePercent = Math.max(MIN_RENDER_SCALE_PERCENT, Math.min(MAX_RENDER_SCALE_PERCENT, scaled));
    return true;
  }

  // ---------------------------------------------------------------- drawing

  @Override
  protected void drawSelf(GuiGraphicsExtractor graphics, Font font, int x, int y, int mouseX, int mouseY) {
    super.drawSelf(graphics, font, x, y, mouseX, mouseY);
    if (dragging) {
      long handle = Minecraft.getInstance().getWindow().handle();
      // Safety net: if the middle button release was missed, give the cursor back.
      if (GLFW.glfwGetMouseButton(handle, GLFW.GLFW_MOUSE_BUTTON_MIDDLE) != GLFW.GLFW_PRESS) {
        stopDragging();
      } else {
        applyKeyboardMovement(handle);
      }
    }
    renderScene(graphics, x, y);
    drawStatusText(graphics, font, x, y);
  }

  private void drawStatusText(GuiGraphicsExtractor graphics, Font font, int x, int y) {
    if (scene.isEmpty()) {
      graphics.text(font, Component.literal("No blocks selected"), x + 8, y + getHeight() - 12, 0xFF404040, false);
    } else if (scene.isSimplified()) {
      graphics.text(font, Component.literal("Surface preview: " + scene.previewBlockCount() + " of "
          + scene.selectedBlockCount() + " blocks"), x + 8, y + getHeight() - 12, 0xFF404040, false);
    }
  }

  /** Queues the GPU render of the scene; the game draws it right after the GUI is assembled. */
  private void renderScene(GuiGraphicsExtractor graphics, int x, int y) {
    int left = x + 4;
    int top = y + 4; // the 3D view fills the whole window; the title draws on top of it
    int right = x + getWidth() - 4;
    int bottom = y + getHeight() - 4;
    int areaWidth = right - left;
    int areaHeight = bottom - top;
    if (areaWidth <= 0 || areaHeight <= 0) {
      return;
    }

    scene.update();
    if (scene.isEmpty()) {
      return;
    }

    // Moving "forward" brings the scene closer, which in an orthographic view is the same as zooming.
    double zoom = CAMERA_DISTANCE / (CAMERA_DISTANCE + offsetZ);
    float scale = (float) (Math.min(areaWidth, areaHeight) * SCENE_SCALE * renderScalePercent * zoom);
    ScreenRectangle scissor = graphics.scissorStack.peek();
    graphics.guiRenderState.addPicturesInPictureState(new ScenePipRenderState(
        scene, (float) yawDegrees, (float) pitchDegrees, (float) offsetX, (float) offsetY,
        left, top, right, bottom, scale, new Matrix3x2f(graphics.pose()), scissor));
  }
}

