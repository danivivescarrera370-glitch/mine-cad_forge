package name.minecad.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * A simple orbiting 3D cube renderer that lives inside an InventoryTabWindow.
 *
 * Controls:
 *  - middle-mouse drag rotates the cube (cursor hidden and locked while dragging); rotation uses
 *    mouse acceleration, so faster movement rotates more per pixel,
 *  - while the middle button is held: W/S forward/back, A/D strafe, E/Q up/down,
 *  - the mouse wheel zooms,
 *  - the + and - buttons double / halve the render quality,
 *  - the AA button toggles antialiasing.
 */
public class InventoryTabWindow3DRenderer extends InventoryTabWindow {
  private static final double ROTATE_SENSITIVITY = 0.35;
  private static final double ACCELERATION = 0.15; // extra rotation multiplier per pixel of mouse movement
  private static final double MAX_ACCELERATION = 6.0;
  private static final double MOVE_SPEED = 3.0; // world units per second
  private static final double CAMERA_DISTANCE = 4.5;
  private static final double CUBE_SCALE = 0.70;
  private static final double DEFAULT_RENDER_SCALE_PERCENT = 0.42;
  private static final double MIN_RENDER_SCALE_PERCENT = 0.10;
  private static final double MAX_RENDER_SCALE_PERCENT = 2.00;
  private static final double ZOOM_STEP = 1.10; // scale change per wheel notch

  // Quality is 2^level: -2 -> 1/4 (very pixelated) ... 0 -> 1 ... 5 -> 32 (very smooth).
  private static final int MIN_QUALITY_LEVEL = -2;
  private static final int MAX_QUALITY_LEVEL = 5;
  private static final int DEFAULT_QUALITY_LEVEL = 2;

  private static final int EDGE_COLOR = 0xFF1B1B1B;

  private static final Vec3[] CUBE_VERTICES = {
      new Vec3(-1, -1, -1), new Vec3(1, -1, -1), new Vec3(1, 1, -1), new Vec3(-1, 1, -1),
      new Vec3(-1, -1, 1), new Vec3(1, -1, 1), new Vec3(1, 1, 1), new Vec3(-1, 1, 1)
  };

  private static final int[][] FACES = {
      {0, 1, 2, 3}, // back
      {4, 5, 6, 7}, // front
      {0, 4, 7, 3}, // left
      {1, 5, 6, 2}, // right
      {3, 2, 6, 7}, // top
      {0, 1, 5, 4}  // bottom
  };

  private static final int[] FACE_COLORS = {
      0xFF8AA0B8, 0xFFB8D4F0, 0xFF9AB0C8, 0xFF7C91A9, 0xFFCADAF0, 0xFF6F829A
  };

  private double yawDegrees = 35.0;
  private double pitchDegrees = -25.0;
  private boolean dragging;
  private double savedCursorX;
  private double savedCursorY;
  private double renderScalePercent = DEFAULT_RENDER_SCALE_PERCENT;
  private int qualityLevel = DEFAULT_QUALITY_LEVEL;
  private boolean antialiasing = true;
  private InventoryTabWindowButton antialiasButton;
  // View-space offset of the cube, changed with WASD (and Q/E) while the middle button is held.
  private double offsetX;
  private double offsetY;
  private double offsetZ;
  private long lastMoveNanos;

  public InventoryTabWindow3DRenderer(int width, int height) {
    super(width, height);
    setTitle(Component.literal("3D"));
    setBackgroundColor(0xFFECECEC);
    createButtons();
  }

  /** The AA, - and + buttons live inside this window, in its top right corner. */
  private void createButtons() {
    InventoryTabWindowButton plus = addButton("+", this::increaseQuality);
    plus.setSize(14, 12);
    plus.setAnchor(Anchor.TOP_RIGHT);
    plus.setMargin(3);

    InventoryTabWindowButton minus = addButton("-", this::decreaseQuality);
    minus.setSize(14, 12);
    minus.setAnchor(Anchor.TOP_RIGHT);
    minus.setMargin(3);
    minus.setOffset(-17, 0); // left of the + button

    antialiasButton = addButton("AA: On", this::toggleAntialiasing);
    antialiasButton.setSize(40, 12);
    antialiasButton.setAnchor(Anchor.TOP_RIGHT);
    antialiasButton.setMargin(3);
    antialiasButton.setOffset(-34, 0); // left of the - button
  }

  // ---------------------------------------------------------------- settings

  /**
   * Adjusts how large the cube is rendered relative to the available space.
   * 1.0 uses the full base size, 0.5 renders it at half size, and so on.
   */
  public void setRenderScalePercent(double renderScalePercent) {
    if (renderScalePercent <= 0.0) {
      throw new IllegalArgumentException("Render scale percent must be positive");
    }
    this.renderScalePercent = renderScalePercent;
  }

  public void toggleAntialiasing() {
    setAntialiasing(!antialiasing);
  }

  public void setAntialiasing(boolean enabled) {
    antialiasing = enabled;
    if (antialiasButton != null) {
      antialiasButton.setLabel(Component.literal(enabled ? "AA: On" : "AA: Off"));
    }
  }

  public boolean isAntialiasing() {
    return antialiasing;
  }

  /** Doubles the render quality (up to a limit). */
  public void increaseQuality() {
    qualityLevel = Math.min(MAX_QUALITY_LEVEL, qualityLevel + 1);
  }

  /** Halves the render quality (down to a limit). */
  public void decreaseQuality() {
    qualityLevel = Math.max(MIN_QUALITY_LEVEL, qualityLevel - 1);
  }

  /** Sets the quality as a power of two: level 0 is 1x, level 2 is 4x, level -1 is 1/2x. */
  public void setQualityLevel(int level) {
    qualityLevel = Math.max(MIN_QUALITY_LEVEL, Math.min(MAX_QUALITY_LEVEL, level));
  }

  /** Render pixels per GUI pixel along each axis. */
  public double getRenderQuality() {
    return Math.pow(2.0, qualityLevel);
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

  /** Mouse wheel zooms the cube in and out by changing the render scale. */
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
    renderCube(graphics, x, y);
    graphics.text(font, Component.literal("Quality: " + qualityText()), x + 8, y + getHeight() - 12, 0xFF404040, false);
  }

  private String qualityText() {
    return qualityLevel >= 0 ? (1 << qualityLevel) + "x" : "1/" + (1 << -qualityLevel) + "x";
  }

  private void renderCube(GuiGraphicsExtractor graphics, int x, int y) {
    int left = x + 4;
    int top = y + 18;
    int right = x + getWidth() - 4;
    int bottom = y + getHeight() - 4;
    double areaWidth = right - left;
    double areaHeight = bottom - top;
    if (areaWidth <= 0 || areaHeight <= 0) {
      return;
    }

    // Everything below is computed in render pixels (1/quality of a GUI pixel).
    double quality = getRenderQuality();
    double scale = Math.min(areaWidth, areaHeight) * CUBE_SCALE * renderScalePercent * quality;
    double centerX = (left + areaWidth / 2.0) * quality;
    double centerY = (top + areaHeight / 2.0) * quality;
    int clipLeft = (int) Math.ceil(left * quality);
    int clipTop = (int) Math.ceil(top * quality);
    int clipRight = (int) Math.floor(right * quality);
    int clipBottom = (int) Math.floor(bottom * quality);
    int lineWidth = Math.max(1, (int) Math.round(quality / 2.0));

    // Shrink the GUI matrix so one drawn unit is one render pixel.
    graphics.pose().pushMatrix();
    graphics.pose().scale((float) (1.0 / quality), (float) (1.0 / quality));
    try {
      drawCube(graphics, scale, centerX, centerY, clipLeft, clipTop, clipRight, clipBottom, lineWidth);
    } finally {
      graphics.pose().popMatrix();
    }
  }

  private void drawCube(GuiGraphicsExtractor graphics, double scale, double centerX, double centerY,
                        int clipLeft, int clipTop, int clipRight, int clipBottom, int lineWidth) {
    double yaw = Math.toRadians(yawDegrees);
    double pitch = Math.toRadians(pitchDegrees);

    Vec3[] rotated = new Vec3[CUBE_VERTICES.length];
    Point2D[] projected = new Point2D[CUBE_VERTICES.length];
    for (int i = 0; i < CUBE_VERTICES.length; i++) {
      Vec3 r = rotate(CUBE_VERTICES[i], yaw, pitch);
      rotated[i] = new Vec3(r.x + offsetX, r.y + offsetY, r.z + offsetZ);
      double perspective = CAMERA_DISTANCE / (CAMERA_DISTANCE + rotated[i].z);
      projected[i] = new Point2D(
          centerX + rotated[i].x * scale * perspective,
          centerY - rotated[i].y * scale * perspective);
    }

    // Keep only faces turned towards the camera (camera sits at z = -CAMERA_DISTANCE).
    List<VisibleFace> visible = new ArrayList<>();
    for (int i = 0; i < FACES.length; i++) {
      int[] face = FACES[i];
      double cx = 0;
      double cy = 0;
      double cz = 0;
      for (int index : face) {
        cx += rotated[index].x / 4.0;
        cy += rotated[index].y / 4.0;
        cz += rotated[index].z / 4.0;
      }
      // The outward normal points from the cube centre (the offset) to the face centre.
      double nx = cx - offsetX;
      double ny = cy - offsetY;
      double nz = cz - offsetZ;
      double facing = nx * (0 - cx) + ny * (0 - cy) + nz * (-CAMERA_DISTANCE - cz);
      if (facing > 0) {
        visible.add(new VisibleFace(face, cz, FACE_COLORS[i]));
      }
    }
    visible.sort(Comparator.comparingDouble(VisibleFace::depth).reversed());

    for (VisibleFace face : visible) {
      Point2D a = projected[face.indices[0]];
      Point2D b = projected[face.indices[1]];
      Point2D c = projected[face.indices[2]];
      Point2D d = projected[face.indices[3]];
      fillTriangle(graphics, a, b, c, face.color, clipLeft, clipTop, clipRight, clipBottom);
      fillTriangle(graphics, a, c, d, face.color, clipLeft, clipTop, clipRight, clipBottom);
    }

    if (antialiasing) {
      // Soft halos around the edges (two translucent layers) smooth the staircase look.
      // Thin halos (about a quarter of a line width) keep the edge crisp instead of fuzzy.
      int outer = Math.max(1, lineWidth / 2);
      int inner = Math.max(1, lineWidth / 4);
      int[][] layers = {{outer, 0x28}, {inner, 0x50}};
      for (int[] layer : layers) {
        int grow = layer[0];
        int color = (layer[1] << 24) | (EDGE_COLOR & 0xFFFFFF);
        for (VisibleFace face : visible) {
          for (int i = 0; i < 4; i++) {
            drawLine(graphics, projected[face.indices[i]], projected[face.indices[(i + 1) % 4]],
                color, lineWidth + 2 * grow, grow, clipLeft, clipTop, clipRight, clipBottom);
          }
        }
      }
    }
    for (VisibleFace face : visible) {
      for (int i = 0; i < 4; i++) {
        drawLine(graphics, projected[face.indices[i]], projected[face.indices[(i + 1) % 4]],
            EDGE_COLOR, lineWidth, 0, clipLeft, clipTop, clipRight, clipBottom);
      }
    }
  }

  /**
   * Fills a triangle by intersecting each pixel row (sampled at its centre) with the three edges.
   * Only real edge intersections are used, so nothing is ever extrapolated past the triangle.
   * The half-open pixel rule leaves no gaps or overlaps between triangles that share an edge.
   */
  private void fillTriangle(GuiGraphicsExtractor graphics, Point2D a, Point2D b, Point2D c, int color,
                            int clipLeft, int clipTop, int clipRight, int clipBottom) {
    Point2D[] p = {a, b, c};
    double minY = Math.min(a.y, Math.min(b.y, c.y));
    double maxY = Math.max(a.y, Math.max(b.y, c.y));
    int yStart = Math.max(clipTop, (int) Math.ceil(minY - 0.5));
    int yEnd = Math.min(clipBottom - 1, (int) Math.ceil(maxY - 0.5) - 1);

    for (int y = yStart; y <= yEnd; y++) {
      double sampleY = y + 0.5;
      double minX = Double.POSITIVE_INFINITY;
      double maxX = Double.NEGATIVE_INFINITY;
      int hits = 0;
      for (int i = 0; i < 3; i++) {
        Point2D p0 = p[i];
        Point2D p1 = p[(i + 1) % 3];
        if ((p0.y <= sampleY && p1.y > sampleY) || (p1.y <= sampleY && p0.y > sampleY)) {
          double t = (sampleY - p0.y) / (p1.y - p0.y);
          double crossX = p0.x + t * (p1.x - p0.x);
          minX = Math.min(minX, crossX);
          maxX = Math.max(maxX, crossX);
          hits++;
        }
      }
      if (hits < 2) {
        continue;
      }
      int xStart = Math.max(clipLeft, (int) Math.ceil(minX - 0.5));
      int xEnd = Math.min(clipRight, (int) Math.ceil(maxX - 0.5));
      if (xStart < xEnd) {
        graphics.fill(xStart, y, xEnd, y + 1, color);
      }
    }
  }

  /**
   * Bresenham line made of thickness x thickness squares, shifted up-left by "shift" pixels (used
   * for the antialiasing halos). Neighbouring squares that continue in a straight horizontal or
   * vertical run are merged into a single rectangle, so high quality levels do not need one draw
   * call per pixel.
   */
  private void drawLine(GuiGraphicsExtractor graphics, Point2D from, Point2D to, int color, int thickness,
                        int shift, int clipLeft, int clipTop, int clipRight, int clipBottom) {
    int x0 = (int) Math.round(from.x) - shift;
    int y0 = (int) Math.round(from.y) - shift;
    int x1 = (int) Math.round(to.x) - shift;
    int y1 = (int) Math.round(to.y) - shift;

    int dx = Math.abs(x1 - x0);
    int sx = x0 < x1 ? 1 : -1;
    int dy = -Math.abs(y1 - y0);
    int sy = y0 < y1 ? 1 : -1;
    int err = dx + dy;

    boolean active = false;
    int rx0 = 0;
    int ry0 = 0;
    int rx1 = 0;
    int ry1 = 0;
    // Thick lines only need a square every half thickness along the line (they still overlap).
    int stride = Math.max(1, thickness / 2);
    int step = 0;

    while (true) {
      int px = x0;
      int py = y0;
      boolean atEnd = x0 == x1 && y0 == y1;
      if (step % stride == 0 || atEnd) {
        if (!active) {
          rx0 = px;
          ry0 = py;
          rx1 = px + thickness;
          ry1 = py + thickness;
          active = true;
        } else if (ry1 - ry0 == thickness && py == ry0 && px <= rx1 && px + thickness > rx1) {
          rx1 = px + thickness;
        } else if (ry1 - ry0 == thickness && py == ry0 && px < rx0 && px + thickness >= rx0) {
          rx0 = px;
        } else if (rx1 - rx0 == thickness && px == rx0 && py <= ry1 && py + thickness > ry1) {
          ry1 = py + thickness;
        } else if (rx1 - rx0 == thickness && px == rx0 && py < ry0 && py + thickness >= ry0) {
          ry0 = py;
        } else {
          fillClipped(graphics, rx0, ry0, rx1, ry1, color, clipLeft, clipTop, clipRight, clipBottom);
          rx0 = px;
          ry0 = py;
          rx1 = px + thickness;
          ry1 = py + thickness;
        }
      }
      step++;

      if (atEnd) {
        break;
      }
      int e2 = 2 * err;
      if (e2 >= dy) {
        err += dy;
        x0 += sx;
      }
      if (e2 <= dx) {
        err += dx;
        y0 += sy;
      }
    }
    if (active) {
      fillClipped(graphics, rx0, ry0, rx1, ry1, color, clipLeft, clipTop, clipRight, clipBottom);
    }
  }

  private void fillClipped(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color,
                           int clipLeft, int clipTop, int clipRight, int clipBottom) {
    int fx0 = Math.max(x0, clipLeft);
    int fy0 = Math.max(y0, clipTop);
    int fx1 = Math.min(x1, clipRight);
    int fy1 = Math.min(y1, clipBottom);
    if (fx0 < fx1 && fy0 < fy1) {
      graphics.fill(fx0, fy0, fx1, fy1, color);
    }
  }

  private static Vec3 rotate(Vec3 v, double yaw, double pitch) {
    double cosYaw = Math.cos(yaw);
    double sinYaw = Math.sin(yaw);
    double cosPitch = Math.cos(pitch);
    double sinPitch = Math.sin(pitch);

    double x1 = v.x * cosYaw + v.z * sinYaw;
    double z1 = -v.x * sinYaw + v.z * cosYaw;
    double y1 = v.y * cosPitch - z1 * sinPitch;
    double z2 = v.y * sinPitch + z1 * cosPitch;
    return new Vec3(x1, y1, z2);
  }

  private record Vec3(double x, double y, double z) {
  }

  private record Point2D(double x, double y) {
  }

  private record VisibleFace(int[] indices, double depth, int color) {
  }
}

