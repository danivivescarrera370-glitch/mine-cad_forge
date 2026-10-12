package name.minecad.ui;

import java.util.List;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.state.gui.pip.PictureInPictureRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * Describes one frame of the 3D scene preview for the GPU picture-in-picture renderer.
 * x0..y1 is the on-screen area in GUI pixels; scale is GUI pixels per unit-sized preview space.
 */
public record ScenePipRenderState(
    ScannedBlockScene scene,
    float yawDegrees,
    float pitchDegrees,
    float offsetX,
    float offsetY,
    int x0,
    int y0,
    int x1,
    int y1,
    float scale,
    Matrix3x2fc pose,
    @Nullable ScreenRectangle scissorArea,
    @Nullable ScreenRectangle bounds,
    long sceneStamp,
    int blockBudget
) implements PictureInPictureRenderState {

  public ScenePipRenderState(ScannedBlockScene scene, float yawDegrees, float pitchDegrees,
                             float offsetX, float offsetY, int x0, int y0, int x1, int y1, float scale,
                             Matrix3x2fc pose, @Nullable ScreenRectangle scissorArea, int blockBudget) {
    this(scene, yawDegrees, pitchDegrees, offsetX, offsetY, x0, y0, x1, y1, scale, pose, scissorArea,
        PictureInPictureRenderState.getBounds(x0, y0, x1, y1, scissorArea), scene.stamp(), blockBudget);
  }

  /** Two states with equal keys produce the same image, so the previous frame can be reused. */
  public List<Object> cacheKey() {
    return List.of(yawDegrees, pitchDegrees, offsetX, offsetY, scale, sceneStamp, x1 - x0, y1 - y0, blockBudget);
  }
}

