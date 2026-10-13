package name.minecad.ui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import name.minecad.client.MineCadClient;
import name.minecad.client.scannedblock.ScannedBlock;
import name.minecad.client.scannedblock.ScannedBlockManager;
import name.minecad.client.settings.MineCadSettings;
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
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * The scanned blocks as a 3D scene that is drawn entirely by the GPU.
 *
 * <p>The scene is rebuilt only when the scanned selection changes. A rebuild collects the visible
 * (surface) blocks together with their baked vanilla model parts, so shapes, textures and tints
 * match the world. Every frame the blocks are handed to the game's own block-model renderer, which
 * transforms, depth-tests and textures them on the GPU.
 */
public class ScannedBlockScene {
  /** Above this many selected blocks the status text reports a surface-only preview. */
  private static final int SURFACE_SIMPLIFY_THRESHOLD = 2_048;
  private static final int FULL_BRIGHT = 0xF000F0;
  /** Half the diagonal of a unit cube: the radius of a block's bounding sphere. */
  private static final float BLOCK_BOUNDING_RADIUS = 0.8660254F;
  /** Extra slack (in pixels) around the visible area so blocks at the edge never pop out early. */
  private static final float VIEW_CULL_MARGIN_PIXELS = 4.0F;
  /** Faces up to this far past edge-on still count as facing the camera (keeps edges safe). */
  private static final double FACING_TOLERANCE = 0.02;
  /** The coarse preview merges at most 2^n blocks per side (n = this value). */
  private static final int MAX_LOD_LEVEL = 5;
  /** Block-state-only changes (not selection changes) rebuild the scene at most this often. */
  private static final long STATE_REBUILD_COOLDOWN_NANOS = 3_000_000_000L;
  private static final Direction[] DIRECTIONS = Direction.values();

  private final List<SceneBlock> blocks = new ArrayList<>();
  /** Coarser stand-in for the scene, drawn while the view is moving. Empty when not needed. */
  private final List<SceneBlock> lodBlocks = new ArrayList<>();
  private final Map<BlockState, List<BlockStateModelPart>> partsCache = new HashMap<>();
  /** Culled part lists per block state, indexed by hidden-face mask (no allocation per lookup). */
  private final Map<BlockState, List<BlockStateModelPart>[]> culledCache = new HashMap<>();
  private final Map<BlockState, Boolean> translucentCache = new HashMap<>();

  private double centerX;
  private double centerY;
  private double centerZ;
  private double radius = 1.0;
  private boolean built;
  private long builtRevision = Long.MIN_VALUE;
  private long builtStructureRevision = Long.MIN_VALUE;
  private long lastBuildNanos;
  private ClientLevel builtLevel;
  private int selectedBlockCount;
  private int visibleBlockCount;
  private int builtMaxBlocks = -1;
  private int builtInteractiveBlocks = -1;
  private int builtHardLimit = -1;
  private boolean builtFullIdle;
  private long buildCounter;
  private long invalidateCounter;
  private boolean capped;

  /**
   * exposedMask: faces not covered by a neighbouring occluder. cullable: a full cube whose quads all
   * lie on its faces, so it can be skipped when no exposed face points at the camera.
   * rank: stable pseudo-random 0..65535 used to choose representatives for the coarse preview.
   * bx/by/bz is the block-space corner; scale is 1 for real blocks and 2^n for coarse preview cubes.
   */
  private record SceneBlock(int bx, int by, int bz, List<BlockStateModelPart> parts, int[] tints,
                            boolean translucent, int exposedMask, boolean cullable, int rank,
                            BlockState state, int scale) {
  }

  /** One cell of the coarse preview grid together with the block that stands in for it. */
  private record LodCell(int cx, int cy, int cz, SceneBlock rep) {
  }

  private record PreparedBlock(BlockPos pos, BlockState state, boolean full) {
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
    return !blocks.isEmpty() && blocks.size() < selectedBlockCount
        && selectedBlockCount > SURFACE_SIMPLIFY_THRESHOLD;
  }

  /** True when the max render blocks setting cut visible blocks out of the preview. */
  public boolean isCapped() {
    return capped;
  }

  /** Number of blocks that touch the outside (everything except fully enclosed interior blocks). */
  public int visibleBlockCount() {
    return visibleBlockCount;
  }

  /** Number of cubes in the coarse moving-view preview; 0 when the full scene is cheap enough. */
  public int lodBlockCount() {
    return lodBlocks.size();
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
    ClientLevel level = Minecraft.getInstance().level;
    // Uses the revision that was actually built, so deferred state changes do not force a redraw.
    return builtRevision * 31L + (level == null ? 0 : System.identityHashCode(level)) + buildCounter * 1_000_003L
        + invalidateCounter * 7_919L;
  }

  /**
   * Forces the next frame to be rendered again instead of reusing the cached picture. Needed when
   * the view was not drawn for a while (e.g. another tab was open), because the cached GPU texture
   * may no longer hold valid content.
   */
  public void invalidate() {
    invalidateCounter++;
  }

  // ---------------------------------------------------------------- scene data

  /** Rebuilds the scene if the selection (or world) changed since the last call. Cheap otherwise. */
  public void update() {
    update(false);
  }

  /**
   * Rebuilds the scene if needed. Changes of the selected set, the world or the render settings are
   * applied right away. When only block states changed (redstone, pressure plates, ...) the rebuild is
   * postponed while {@code viewBusy} is true and rate limited otherwise, because rebuilding a large
   * scene takes long enough to make rotating the view stutter.
   */
  public void update(boolean viewBusy) {
    ClientLevel level = Minecraft.getInstance().level;
    ScannedBlockManager manager = MineCadClient.getScannedBlockManager();
    if (level == null || manager == null) {
      built = false;
      blocks.clear();
      lodBlocks.clear();
      selectedBlockCount = 0;
      return;
    }

    long revision = manager.getSelectionRevision();
    long structure = manager.getStructureRevision();
    int maxBlocks = MineCadSettings.getMaxRenderBlocks();
    int interactiveBlocks = MineCadSettings.getInteractiveRenderBlocks();
    int hardLimit = MineCadSettings.getHardBlockLimit();
    boolean fullIdle = MineCadSettings.isFullDetailWhenIdle();
    boolean mustRebuild = !built || builtLevel != level || builtStructureRevision != structure
        || builtMaxBlocks != maxBlocks || builtInteractiveBlocks != interactiveBlocks
        || builtHardLimit != hardLimit || builtFullIdle != fullIdle;
    if (!mustRebuild) {
      if (builtRevision == revision) {
        return;
      }
      // Only block states changed: wait until the view is still and the last rebuild is a while ago.
      if (viewBusy || System.nanoTime() - lastBuildNanos < STATE_REBUILD_COOLDOWN_NANOS) {
        return;
      }
    }
    built = true;
    builtRevision = revision;
    builtStructureRevision = structure;
    builtLevel = level;
    builtMaxBlocks = maxBlocks;
    builtInteractiveBlocks = interactiveBlocks;
    builtHardLimit = hardLimit;
    builtFullIdle = fullIdle;
    buildCounter++;
    rebuild(level, manager, maxBlocks, interactiveBlocks, hardLimit, fullIdle);
    lastBuildNanos = System.nanoTime();
  }

  private void rebuild(ClientLevel level, ScannedBlockManager manager, int maxBlocks, int interactiveBlocks,
                       int hardLimit, boolean fullIdle) {
    blocks.clear();
    lodBlocks.clear();
    partsCache.clear();
    culledCache.clear();
    translucentCache.clear();
    selectedBlockCount = 0;
    visibleBlockCount = 0;
    capped = false;

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

    // Culling pass 1 (always on, lossless): blocks completely buried under full occluders can
    // never be seen, so they are dropped. They still count as occluders for their neighbours.
    List<PreparedBlock> preview = new ArrayList<>(prepared.size());
    Map<Long, Boolean> occluders = new HashMap<>(prepared.size() * 2);
    for (PreparedBlock candidate : prepared.values()) {
      if (isSurface(candidate.pos(), prepared)) {
        preview.add(candidate);
      } else {
        occluders.put(candidate.pos().asLong(), Boolean.TRUE);
      }
    }
    if (preview.isEmpty()) {
      preview = new ArrayList<>(prepared.values());
      occluders.clear();
    }
    visibleBlockCount = preview.size();

    // Culling pass 2: optional hard block limit setting (0 = unlimited, the default). Above it a
    // deterministic subset is kept. The max render blocks limit does not drop blocks; it merges the
    // scene into coarser cubes below (dropping blocks would leave see-through holes in the surface).
    if (hardLimit > 0 && preview.size() > hardLimit) {
      capped = true;
      preview.sort(Comparator.comparingInt(block -> positionHash(block.pos())));
      preview = new ArrayList<>(preview.subList(0, hardLimit));
    }

    // Occlusion is decided against the blocks that are really drawn (plus the buried ones that
    // were dropped above), so no holes appear when the limit removes blocks.
    for (PreparedBlock block : preview) {
      occluders.put(block.pos().asLong(), block.full());
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
          if (Boolean.TRUE.equals(occluders.get(neighbour))) {
            hiddenMask |= 1 << direction.ordinal();
          }
        }
      }

      // Every face is covered by a neighbour: nothing of this block can be seen.
      if (hiddenMask == (1 << DIRECTIONS.length) - 1) {
        continue;
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
      int exposedMask = ~hiddenMask & ((1 << DIRECTIONS.length) - 1);
      int rank = positionHash(pos) & 0xFFFF;
      blocks.add(new SceneBlock(pos.getX(), pos.getY(), pos.getZ(), parts, tints, isTranslucent(state),
          exposedMask, block.full(), rank, state, 1));
    }

    // Moving-view stand-in first (from the finest data), then shrink the full scene to the limit.
    lodBlocks.addAll(buildLod(blocks, interactiveBlocks));
    // With "full detail when idle" the resting view keeps every block and only the moving-view
    // stand-in is coarse.
    if (!fullIdle && blocks.size() > maxBlocks) {
      List<SceneBlock> coarse = buildLod(blocks, maxBlocks);
      if (!coarse.isEmpty()) {
        capped = true;
        blocks.clear();
        blocks.addAll(coarse);
      }
    }
    if (lodBlocks.size() >= blocks.size()) {
      lodBlocks.clear(); // not cheaper than the full scene
    }
    prewarmFacingParts();
  }

  /** A block's state together with its exposed faces: the inputs of the per-view face culling. */
  private record PartKey(BlockState state, int exposedMask) {
  }

  /**
   * Builds the back-face-culled part lists of both the full and the coarse scene for every possible
   * camera facing now (together with the rebuild), so that switching between full and low detail
   * only swaps which prebuilt block list is submitted and never builds parts during drawing.
   */
  private void prewarmFacingParts() {
    java.util.Set<PartKey> keys = new java.util.HashSet<>();
    for (SceneBlock block : blocks) {
      if (block.cullable()) {
        keys.add(new PartKey(block.state(), block.exposedMask()));
      }
    }
    for (SceneBlock block : lodBlocks) {
      if (block.cullable()) {
        keys.add(new PartKey(block.state(), block.exposedMask()));
      }
    }
    if (keys.isEmpty()) {
      return;
    }

    int all = (1 << DIRECTIONS.length) - 1;
    int[][] axisPairs = {
        {Direction.DOWN.ordinal(), Direction.UP.ordinal()},
        {Direction.NORTH.ordinal(), Direction.SOUTH.ordinal()},
        {Direction.WEST.ordinal(), Direction.EAST.ordinal()}};
    // Per axis a camera sees the negative face, the positive face, or both (almost edge-on).
    for (int combo = 0; combo < 27; combo++) {
      int facing = 0;
      int rest = combo;
      for (int[] pair : axisPairs) {
        int choice = rest % 3;
        rest /= 3;
        if (choice == 0 || choice == 2) {
          facing |= 1 << pair[0];
        }
        if (choice == 1 || choice == 2) {
          facing |= 1 << pair[1];
        }
      }
      for (PartKey key : keys) {
        int visible = key.exposedMask() & facing;
        if (visible != key.exposedMask()) {
          culledParts(key.state(), ~visible & all);
        }
      }
    }
  }

  /**
   * Builds the coarse stand-in used while the view is moving: the blocks are merged into cubes of
   * 2, 4, 8, ... blocks per side (one representative block scaled up per cube) until there are at
   * most {@code budget} of them. Unlike dropping random blocks this keeps the shape solid.
   */
  private List<SceneBlock> buildLod(List<SceneBlock> source, int budget) {
    List<SceneBlock> result = new ArrayList<>();
    if (source.size() <= budget) {
      return result;
    }

    List<LodCell> cells = new ArrayList<>(source.size());
    for (SceneBlock block : source) {
      cells.add(new LodCell(block.bx(), block.by(), block.bz(), block));
    }
    int level = 0;
    while (cells.size() > budget && level < MAX_LOD_LEVEL) {
      cells = coarsen(cells);
      level++;
    }
    if (level == 0) {
      return result;
    }

    int size = 1 << level;
    Map<Long, Boolean> occupied = new HashMap<>(cells.size() * 2);
    for (LodCell cell : cells) {
      occupied.put(cellKey(cell.cx(), cell.cy(), cell.cz()), cell.rep().cullable());
    }

    for (LodCell cell : cells) {
      SceneBlock rep = cell.rep();
      int hiddenMask = 0;
      if (rep.cullable()) {
        for (Direction direction : DIRECTIONS) {
          long neighbour = cellKey(cell.cx() + direction.getStepX(), cell.cy() + direction.getStepY(),
              cell.cz() + direction.getStepZ());
          if (Boolean.TRUE.equals(occupied.get(neighbour))) {
            hiddenMask |= 1 << direction.ordinal();
          }
        }
      }
      if (hiddenMask == (1 << DIRECTIONS.length) - 1) {
        continue;
      }
      List<BlockStateModelPart> parts = culledParts(rep.state(), hiddenMask);
      if (parts.isEmpty()) {
        continue;
      }
      int exposedMask = ~hiddenMask & ((1 << DIRECTIONS.length) - 1);
      result.add(new SceneBlock(cell.cx() * size, cell.cy() * size, cell.cz() * size, parts, rep.tints(),
          rep.translucent(), exposedMask, rep.cullable(), rep.rank(), rep.state(), size));
    }
    return result;
  }

  /** Merges 2x2x2 cells into one; the representative is a full cube if there is one, else the lowest rank. */
  private static List<LodCell> coarsen(List<LodCell> cells) {
    Map<Long, LodCell> merged = new HashMap<>(cells.size());
    for (LodCell cell : cells) {
      int cx = cell.cx() >> 1;
      int cy = cell.cy() >> 1;
      int cz = cell.cz() >> 1;
      long key = cellKey(cx, cy, cz);
      LodCell existing = merged.get(key);
      if (existing == null || isBetterRepresentative(cell.rep(), existing.rep())) {
        merged.put(key, new LodCell(cx, cy, cz, cell.rep()));
      }
    }
    return new ArrayList<>(merged.values());
  }

  private static boolean isBetterRepresentative(SceneBlock candidate, SceneBlock current) {
    if (candidate.cullable() != current.cullable()) {
      return candidate.cullable();
    }
    return candidate.rank() < current.rank();
  }

  /** Packs cell coordinates (21 bits each, signed range is plenty for a Minecraft world). */
  private static long cellKey(int cx, int cy, int cz) {
    return ((long) (cx & 0x1FFFFF) << 42) | ((long) (cy & 0x1FFFFF) << 21) | (cz & 0x1FFFFF);
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
    @SuppressWarnings("unchecked")
    List<BlockStateModelPart>[] byMask = culledCache.computeIfAbsent(state,
        s -> (List<BlockStateModelPart>[]) new List[1 << DIRECTIONS.length]);
    List<BlockStateModelPart> cached = byMask[hiddenMask];
    if (cached != null) {
      return cached;
    }
    List<BlockStateModelPart> culled = new ArrayList<>(parts.size());
    for (BlockStateModelPart part : parts) {
      culled.add(new CulledPart(part, hiddenMask));
    }
    byMask[hiddenMask] = culled;
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
    submit(poseStack, collector, 0.0F, 0.0F, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, 0.0F, 0.0F, 0);
  }

  /**
   * Same as {@link #submit(PoseStack, SubmitNodeCollector)} but skips everything that cannot be seen:
   * <ul>
   *   <li>blocks outside the picture: viewCenterX/viewCenterY is the picture centre and
   *       viewHalfWidth/viewHalfHeight its half extents, all in the pose stack's output pixel space
   *       (the space block positions have after the whole pose, including the base translation),</li>
   *   <li>full cubes whose exposed faces all point away from the camera (yaw/pitch are the view angles
   *       used to build the pose),</li>
   *   <li>when blockBudget is above 0 and a coarse preview exists, the coarse preview (merged
   *       cubes) is drawn instead of the full scene; used while the view is moving.</li>
   * </ul>
   */
  public void submit(PoseStack poseStack, SubmitNodeCollector collector,
                     float viewCenterX, float viewCenterY, float viewHalfWidth, float viewHalfHeight,
                     float yawDegrees, float pitchDegrees, int blockBudget) {
    var solidType = Sheets.cutoutBlockItemSheet();
    var translucentType = Sheets.translucentBlockItemSheet();

    Matrix4f viewMatrix = poseStack.last().pose();
    boolean cullToView = Float.isFinite(viewHalfWidth) && Float.isFinite(viewHalfHeight);
    float unitRadius = 0.0F;
    Vector3f projected = new Vector3f();
    if (cullToView) {
      // The depth squash makes axes scale differently; the largest one keeps the test conservative.
      Vector3f axisScale = viewMatrix.getScale(new Vector3f());
      float maxScale = Math.max(axisScale.x(), Math.max(axisScale.y(), axisScale.z()));
      unitRadius = BLOCK_BOUNDING_RADIUS * maxScale;
    }

    // Faces that point towards the camera for this view; everything else is a back face.
    int facingMask = (1 << DIRECTIONS.length) - 1;
    if (cullToView) {
      facingMask = cameraFacingMask(yawDegrees, pitchDegrees);
    }

    List<SceneBlock> source = blockBudget > 0 && !lodBlocks.isEmpty() ? lodBlocks : blocks;
    for (SceneBlock block : source) {
      // A full cube is only visible through an exposed face that points at the camera.
      if (block.cullable && (block.exposedMask & facingMask) == 0) {
        continue;
      }
      int scale = block.scale;
      float localX = (float) (block.bx - centerX);
      float localY = (float) (block.by - centerY);
      float localZ = (float) (block.bz - centerZ);
      if (cullToView) {
        float half = 0.5F * scale;
        viewMatrix.transformPosition(localX + half, localY + half, localZ + half, projected);
        float reach = unitRadius * scale + VIEW_CULL_MARGIN_PIXELS;
        if (Math.abs(projected.x() - viewCenterX) > viewHalfWidth + reach
            || Math.abs(projected.y() - viewCenterY) > viewHalfHeight + reach) {
          continue;
        }
      }
      poseStack.pushPose();
      poseStack.translate(localX, localY, localZ);
      if (scale != 1) {
        poseStack.scale(scale, scale, scale);
      }
      List<BlockStateModelPart> parts = block.parts;
      if (block.cullable) {
        // Per-face back-face culling: drop the exposed faces that point away from the camera.
        int visibleMask = block.exposedMask & facingMask;
        if (visibleMask != block.exposedMask) {
          int hiddenMask = ~visibleMask & ((1 << DIRECTIONS.length) - 1);
          parts = culledParts(block.state, hiddenMask);
        }
      }
      collector.submitBlockModel(poseStack, block.translucent ? translucentType : solidType,
          parts, block.tints, FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0);
      poseStack.popPose();
    }
  }

  /**
   * Bit mask (by Direction ordinal) of the cube faces whose outward normal points towards the
   * viewer. The depth of a world axis comes from the third row of the view rotation built in
   * ScenePipRenderer (view z points away from the viewer, so a negative depth faces the camera).
   * A small tolerance keeps faces that are almost edge-on.
   */
  private static int cameraFacingMask(float yawDegrees, float pitchDegrees) {
    double yaw = Math.toRadians(yawDegrees);
    double pitch = Math.toRadians(pitchDegrees);
    double depthOfX = -Math.sin(yaw) * Math.cos(pitch);
    double depthOfY = Math.sin(pitch);
    double depthOfZ = -Math.cos(yaw) * Math.cos(pitch);
    int mask = 0;
    for (Direction direction : DIRECTIONS) {
      double depth = direction.getStepX() * depthOfX + direction.getStepY() * depthOfY
          + direction.getStepZ() * depthOfZ;
      if (depth < FACING_TOLERANCE) {
        mask |= 1 << direction.ordinal();
      }
    }
    return mask;
  }
}

