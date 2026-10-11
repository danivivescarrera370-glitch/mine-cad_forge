package name.minecad.ui;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.pip.PictureInPictureRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.joml.Matrix3f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Renders the scanned-block scene on the GPU into an offscreen colour + depth texture (the
 * picture-in-picture mechanism the game uses for the inventory player, books and banners) and
 * lets the GUI draw that texture. When nothing changed since the last frame the texture is simply
 * drawn again without re-rendering.
 */
public class ScenePipRenderer extends PictureInPictureRenderer<ScenePipRenderState> {
  /** The ortho projection used by picture-in-picture only covers depths of about +-1000. */
  private static final float MAX_DEPTH_EXTENT = 900.0f;

  private List<Object> lastKey;

  @Override
  public Class<ScenePipRenderState> getRenderStateClass() {
    return ScenePipRenderState.class;
  }

  @Override
  protected boolean textureIsReadyToBlit(ScenePipRenderState state) {
    return state.cacheKey().equals(lastKey);
  }

  @Override
  protected void renderToTexture(ScenePipRenderState state, PoseStack poseStack, SubmitNodeCollector collector) {
    lastKey = state.cacheKey();
    ScannedBlockScene scene = state.scene();
    if (scene.isEmpty()) {
      return;
    }

    Minecraft.getInstance().gameRenderer.lighting().setupFor(Lighting.Entry.ITEMS_3D);

    // The pose stack maps one unit to s pixels, with x to the right, y down and z away from the viewer.
    float pixelsPerUnit = poseStack.last().pose().getScale(new Vector3f()).x();
    float depthSquash = Math.min(1.0f, MAX_DEPTH_EXTENT / (1.8f * Math.max(pixelsPerUnit, 1.0f)));

    // View offset (moved with WASD) in unit-sized preview space; view y points up, pose y points down.
    poseStack.translate(state.offsetX(), -state.offsetY(), 0.0f);
    // Only affects depth ordering (keeps large zoom inside the depth range).
    poseStack.scale(1.0f, 1.0f, depthSquash);
    poseStack.mulPose(viewRotation(state.yawDegrees(), state.pitchDegrees()));
    float normalize = (float) scene.normalizeScale();
    poseStack.scale(normalize, normalize, normalize);

    scene.submit(poseStack, collector);
  }

  /**
   * Rotation from world space (east right, up up, south towards the viewer) into the picture's
   * space (x right, y down, z away). With zero yaw and pitch this is a half turn around the x axis.
   */
  private static Quaternionf viewRotation(float yawDegrees, float pitchDegrees) {
    float yaw = (float) Math.toRadians(yawDegrees);
    float pitch = (float) Math.toRadians(pitchDegrees);
    float cy = (float) Math.cos(yaw);
    float sy = (float) Math.sin(yaw);
    float cp = (float) Math.cos(pitch);
    float sp = (float) Math.sin(pitch);
    // Columns are the images of the world x, y and z axes.
    Matrix3f matrix = new Matrix3f(
        cy, -sy * sp, -sy * cp,
        0.0f, -cp, sp,
        -sy, -cy * sp, -cy * cp);
    return new Quaternionf().setFromNormalized(matrix);
  }

  @Override
  protected float getTranslateY(int height, int guiScale) {
    return height / 2.0f;
  }

  @Override
  protected String getTextureLabel() {
    return "mine-cad scene";
  }
}

