package name.minecad.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import name.minecad.MineCad;
import name.minecad.scannedblock.ScannedBlock;
import name.minecad.scannedblock.ScannedBlockManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.client.resources.model.sprite.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jspecify.annotations.Nullable;

/**
 * The scanned blocks as a 3D scene that is drawn entirely by the GPU.
 *
 * <p>The scene is rebuilt only when the scanned selection changes. A rebuild collects the visible
 * (surface) blocks together with their baked vanilla model parts, so shapes, textures and tints
 * match the world. Every frame the blocks are handed to the game's own block-model renderer, which
 * transforms, depth-tests and textures them on the GPU.
 */
public class ScannedBlockScene {
  /** Above this many selected blocks only the outer surface is previewed. */
  private static final int SURFACE_SIMPLIFY_THRESHOLD = 2_048;
  /** Upper limit of previewed blocks; beyond this a deterministic subset of the surface is shown. */
  private static final int MAX_PREVIEW_BLOCKS = 60_000;
  private static final int FULL_BRIGHT = 0xF000F0;
  private static final Direction[] DIRECTIONS = Direction.values();

  private final List<SceneBlock> blocks = new ArrayList<>();
  private final Map<BlockState, List<BlockStateModelPart>> partsCache = new HashMap<>();
  private final Map<CulledKey, List<BlockStateModelPart>> culledCache = new HashMap<>();
  private final Map<BlockState, Boolean> translucentCache = new HashMap<>();

  private double centerX;
  private double centerY;
  private double centerZ;
  private double radius = 1.0;
  private boolean built;
  private long builtRevision = Long.MIN_VALUE;
  private ClientLevel builtLevel;
  private int selectedBlockCount;

  private record SceneBlock(int bx, int by, int bz, List<BlockStateModelPart> parts, int[] tints,
                            boolean translucent) {
  }

  private record PreparedBlock(BlockPos pos, BlockState state, boolean full) {
  }

  private record CulledKey(BlockState state, int hiddenMask) {
  }

  /** A model part that hides the faces which touch another selected solid block. */
  private static final class CulledPart implements BlockStateModelPart {
    private final BlockStateModelPart delegate;
    private final int hiddenMask;

    private CulledPart(BlockStateModelPart delegate, int hiddenMask) {
      this.delegate = delegate;
      this.hiddenMask = hiddenMask;
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable Direction direction) {
      if (direction != null && (hiddenMask & (1 << direction.ordinal())) != 0) {
        return List.of();
      }
      return delegate.getQuads(direction);
    }

    @Override
    public boolean useAmbientOcclusion() {
      return delegate.useAmbientOcclusion();
    }

    @Override
    public Material.Baked particleMaterial() {
      return delegate.particleMaterial();
    }

    @Override
    @BakedQuad.MaterialFlags
    public int materialFlags() {
      return delegate.materialFlags();
    }
  }

  // ---------------------------------------------------------------- state

  /** True when there is nothing to draw (call after {@link #update()}). */
  public boolean isEmpty() {
    return blocks.isEmpty();
  }

  /** Number of blocks that are actually submitted for drawing. */
  public int previewBlockCount() {
    return blocks.size();
  }

  /** Number of selected blocks (that still exist in the world) that were considered. */
  public int selectedBlockCount() {
    return selectedBlockCount;
  }

  /** True when the preview shows fewer blocks than the selection (surface only or sampled). */
  public boolean isSimplified() {
    return !blocks.isEmpty() && blocks.size() < selectedBlockCount;
  }

  /** Factor that maps world units to the unit-sized preview space. */
  public double normalizeScale() {
    return 1.73 / Math.max(radius, 0.5);
  }

  /**
   * A value that changes whenever the drawn content could change. Used to decide whether the
   * previous frame can be reused.
   */
  public long stamp() {
    ScannedBlockManager manager = MineCad.getScannedBlockManager();
    ClientLevel level = Minecraft.getInstance().level;
    long revision = manager == null ? -1L : manager.getSelectionRevision();
    return revision * 31L + (level == null ? 0 : System.identityHashCode(level));
  }

  // ---------------------------------------------------------------- scene data

  /** Rebuilds the scene if the selection (or world) changed since the last call. Cheap otherwise. */
  public void update() {
    ClientLevel level = Minecraft.getInstance().level;
    ScannedBlockManager manager = MineCad.getScannedBlockManager();
    if (level == null || manager == null) {
      built = false;
      blocks.clear();
      selectedBlockCount = 0;
      return;
    }

    long revision = manager.getSelectionRevision();
    if (built && builtRevision == revision && builtLevel == level) {
      return;
    }
    built = true;
    builtRevision = revision;
    builtLevel = level;
    rebuild(level, manager);
  }

  private void rebuild(ClientLevel level, ScannedBlockManager manager) {
    blocks.clear();
    partsCache.clear();
    culledCache.clear();
    translucentCache.clear();
    selectedBlockCount = 0;

    List<ScannedBlock> scanned = manager.getScannedBlocks();
    if (scanned.isEmpty()) {
      return;
    }

    int minX = Integer.MAX_VALUE;
    int minY = Integer.MAX_VALUE;
    int minZ = Integer.MAX_VALUE;
    int maxX = Integer.MIN_VALUE;
    int maxY = Integer.MIN_VALUE;
    int maxZ = Integer.MIN_VALUE;
    for (ScannedBlock block : scanned) {
      BlockPos pos = block.pos;
      minX = Math.min(minX, pos.getX());
      minY = Math.min(minY, pos.getY());
      minZ = Math.min(minZ, pos.getZ());
      maxX = Math.max(maxX, pos.getX());
      maxY = Math.max(maxY, pos.getY());
      maxZ = Math.max(maxZ, pos.getZ());
    }
    centerX = (minX + maxX + 1) / 2.0;
    centerY = (minY + maxY + 1) / 2.0;
    centerZ = (minZ + maxZ + 1) / 2.0;
    double sizeX = maxX - minX + 1;
    double sizeY = maxY - minY + 1;
    double sizeZ = maxZ - minZ + 1;
    radius = 0.5 * Math.sqrt(sizeX * sizeX + sizeY * sizeY + sizeZ * sizeZ);

    // Read the current world state of every selected block once.
    Map<Long, PreparedBlock> prepared = new HashMap<>(scanned.size() * 2);
    for (ScannedBlock block : scanned) {
      BlockPos pos = block.pos;
      BlockState state = level.getBlockState(pos);
      if (state.isAir()) {
        continue;
      }
      // Culling must only treat true full occluders as "solid", otherwise partial blocks like
      // stairs/slabs can incorrectly hide neighboring faces and create holes.
      VoxelShape occlusion = state.getOcclusionShape();
      boolean full = state.canOcclude() && net.minecraft.world.level.block.Block.isShapeFullBlock(occlusion);
      prepared.put(pos.asLong(), new PreparedBlock(pos, state, full));
    }
    selectedBlockCount = prepared.size();

    // Large selections: keep only blocks that touch the outside, never the buried interior.
    List<PreparedBlock> preview = new ArrayList<>(prepared.values());
    if (preview.size() > SURFACE_SIMPLIFY_THRESHOLD) {
      List<PreparedBlock> surface = new ArrayList<>();
      for (PreparedBlock candidate : preview) {
        if (isSurface(candidate.pos(), prepared)) {
          surface.add(candidate);
        }
      }
      if (!surface.isEmpty()) {
        preview = surface;
      }
    }
    if (preview.size() > MAX_PREVIEW_BLOCKS) {
      preview.sort(Comparator.comparingInt(block -> positionHash(block.pos())));
      preview = new ArrayList<>(preview.subList(0, MAX_PREVIEW_BLOCKS));
    }

    // Occlusion is decided against the blocks that are really drawn, so no holes appear.
    Map<Long, Boolean> drawnFull = new HashMap<>(preview.size() * 2);
    for (PreparedBlock block : preview) {
      drawnFull.put(block.pos().asLong(), block.full());
    }

    for (PreparedBlock block : preview) {
      BlockPos pos = block.pos();
      BlockState state = block.state();

      int hiddenMask = 0;
      if (block.full()) {
        for (Direction direction : DIRECTIONS) {
          long neighbour = BlockPos.asLong(
              pos.getX() + direction.getStepX(),
              pos.getY() + direction.getStepY(),
              pos.getZ() + direction.getStepZ());
          if (Boolean.TRUE.equals(drawnFull.get(neighbour))) {
            hiddenMask |= 1 << direction.ordinal();
          }
        }
      }

      List<BlockStateModelPart> parts = culledParts(state, hiddenMask);
      if (parts.isEmpty()) {
        continue;
      }

      List<BlockTintSource> sources = Minecraft.getInstance().getBlockColors().getTintSources(state);
      int[] tints = new int[sources.size()];
      for (int i = 0; i < tints.length; i++) {
        tints[i] = 0xFF000000 | sources.get(i).colorInWorld(state, level, pos);
      }
      blocks.add(new SceneBlock(pos.getX(), pos.getY(), pos.getZ(), parts, tints, isTranslucent(state)));
    }
  }

  /**
   * A block is on the surface when at least one direct neighbour is missing from the selection or
   * is not a full occluder (stairs, slabs, ... leave gaps, so the block behind them is still visible).
   */
  private static boolean isSurface(BlockPos pos, Map<Long, PreparedBlock> selection) {
    for (Direction direction : DIRECTIONS) {
      long neighbour = BlockPos.asLong(
          pos.getX() + direction.getStepX(),
          pos.getY() + direction.getStepY(),
          pos.getZ() + direction.getStepZ());
      PreparedBlock neighbourBlock = selection.get(neighbour);
      if (neighbourBlock == null || !neighbourBlock.full()) {
        return true;
      }
    }
    return false;
  }

  private static int positionHash(BlockPos pos) {
    int hash = pos.getX() * 73428767 ^ pos.getY() * 912931 ^ pos.getZ() * 19349663;
    return hash ^ (hash >>> 16);
  }

  private List<BlockStateModelPart> modelParts(BlockState state) {
    List<BlockStateModelPart> cached = partsCache.get(state);
    if (cached != null) {
      return cached;
    }
    List<BlockStateModelPart> parts = new ArrayList<>();
    try {
      Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state)
          .collectParts(RandomSource.create(42L), parts);
    } catch (Exception e) {
      System.out.println("[mine-cad] could not get model of " + state + ": " + e);
      parts.clear();
    }
    partsCache.put(state, parts);
    return parts;
  }

  private List<BlockStateModelPart> culledParts(BlockState state, int hiddenMask) {
    List<BlockStateModelPart> parts = modelParts(state);
    if (hiddenMask == 0 || parts.isEmpty()) {
      return parts;
    }
    CulledKey key = new CulledKey(state, hiddenMask);
    List<BlockStateModelPart> cached = culledCache.get(key);
    if (cached != null) {
      return cached;
    }
    List<BlockStateModelPart> culled = new ArrayList<>(parts.size());
    for (BlockStateModelPart part : parts) {
      culled.add(new CulledPart(part, hiddenMask));
    }
    culledCache.put(key, culled);
    return culled;
  }

  private boolean isTranslucent(BlockState state) {
    Boolean cached = translucentCache.get(state);
    if (cached != null) {
      return cached;
    }
    boolean translucent = false;
    for (BlockStateModelPart part : modelParts(state)) {
      for (int i = -1; i < DIRECTIONS.length && !translucent; i++) {
        for (BakedQuad quad : part.getQuads(i < 0 ? null : DIRECTIONS[i])) {
          if (quad.materialInfo().layer() == ChunkSectionLayer.TRANSLUCENT) {
            translucent = true;
            break;
          }
        }
      }
    }
    translucentCache.put(state, translucent);
    return translucent;
  }

  // ---------------------------------------------------------------- drawing

  /**
   * Hands every block to the game's renderer. The pose stack must already contain the view
   * transform (rotation, offset and scale); each block is positioned relative to the scene centre.
   */
  public void submit(PoseStack poseStack, SubmitNodeCollector collector) {
    var solidType = Sheets.cutoutBlockItemSheet();
    var translucentType = Sheets.translucentBlockItemSheet();
    for (SceneBlock block : blocks) {
      poseStack.pushPose();
      poseStack.translate((float) (block.bx - centerX), (float) (block.by - centerY), (float) (block.bz - centerZ));
      collector.submitBlockModel(poseStack, block.translucent ? translucentType : solidType,
          block.parts, block.tints, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
    }
  }
}

