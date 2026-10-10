package name.minecad.scannedblock;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;

public class ScannedBlock {
    public final Block block;
    public final BlockPos pos;
    private final boolean isBlockEntity;
    private final boolean isNormalBlock;
    private final boolean isEntity;

    public ScannedBlock(Block block, BlockPos pos) {
        this(block, pos, false, true, false);
    }




    public ScannedBlock(Block block, BlockPos pos, boolean isEntity, boolean isNormalBlock, boolean isBlockEntity) {
        this.block = block;
        this.pos = pos;
        this.isBlockEntity = isBlockEntity;
        this.isNormalBlock = isNormalBlock;
        this.isEntity = isEntity;
    }

    public List<double[]> getGeometry() {
        if (isBlockEntity && !isEntity && !isNormalBlock) return getBlockEntityGeometry();
        if (isEntity && !isBlockEntity && !isNormalBlock) return getEntityGeometry();
        if (isNormalBlock && !isBlockEntity && !isEntity) return getNormalBlockGeometry();
        return new ArrayList<>();
    }

    private List<double[]> getBlockEntityGeometry() {
        return new ArrayList<>();
    }

    private List<double[]> getEntityGeometry() {
        return new ArrayList<>();
    }

    private List<double[]> getNormalBlockGeometry() {
        // Placeholder until client-side model extraction is reimplemented for new API.
        return new ArrayList<>();
    }

    /**
     * Returns a world-space box that can be used by client-side renderers to draw
     * an outline around this scanned block.
     */
    public AABB getOutlineBox(double inflateAmount) {
        AABB outlineBox = new AABB(
                pos.getX(), pos.getY(), pos.getZ(),
                pos.getX() + 1.0D, pos.getY() + 1.0D, pos.getZ() + 1.0D
        );
        return inflateAmount == 0.0D ? outlineBox : outlineBox.inflate(inflateAmount);
    }
}