package name.minecad.stl;

import java.math.BigInteger;
import java.nio.file.Path;
import java.util.LinkedList;
import net.minecraft.world.level.block.Block;

public class STLManager {
    long multiplier;
    STL stl;
    boolean setMultiplierCap = false;
    long multiplierCap;

    /**
     * Initializes a new instance of the STLManager class with the specified multiplier.
     * @param multiplier: The multiplier to be used for STL calculations.
     *                  This value can be used to scale STL values. E.g. 0 means no scaling and 1 block equals 1 meter
     *                  1 means 1 block equals 1 meter, 2 means 1 block equals 2 meters, 0.01 means 1 block equals 0.01 meters, etc.
     *
     *                  It is recommended to keep this number within a reasonable range to avoid computational strains.
     */
    public STLManager(long multiplier) {
        if (multiplier <= 0) {
            throw new IllegalArgumentException("Multiplier must be a positive value.");
        }
        if (multiplier > BigInteger.valueOf(Long.MAX_VALUE).longValue()) {
            throw new IllegalArgumentException("Multiplier exceeds maximum allowed value.");
        }
        if (multiplier < BigInteger.valueOf(Long.MIN_VALUE).longValue()) {
            throw new IllegalArgumentException("Multiplier is below minimum allowed value.");
        }

        this.multiplier = multiplier;

        // amy remaining constructor logic can go below here now.
    }

    /**
     * Initializes a new instance of the STLManager class with the specified multiplier.
     * @param multiplier: The multiplier to be used for STL calculations.
     *                  This value can be used to scale STL values. E.g. 0 means no scaling and 1 block equals 1 meter
     *                  1 means 1 block equals 1 meter, 2 means 1 block equals 2 meters, 0.01 means 1 block equals 0.01 meters, etc.
     *
     *                  It is recommended to keep this number within a reasonable range to avoid computational strains.
     *
     * @param setMultiplierCap: If true, sets a cap on the multiplier.
     *                        If false, the multiplier can be changed without any cap.
     *
     * @param multiplierCap: The cap on the multiplier. This value can be used to limit the maximum multiplier that can be set.
     * If setMultiplierCap is true, this value must be greater than 0.
     * If setMultiplierCap is false, this value is ignored.
     * This value can be used to limit the maximum multiplier that can be set.
     */
    public STLManager(long multiplier, boolean setMultiplierCap, long multiplierCap) {
        this(multiplier);
        if (setMultiplierCap) {
            this.setMultiplierCap(multiplierCap);
        }
    }

    /**
     * Returns the STL object created by this STLManager.
     * @return The STL object created by this STLManager.
     * @throws IllegalStateException if the STL has not been created yet.
     */
    public STL getSTL() {
        if (this.stl == null) throw new IllegalStateException("STL has not been created yet.");
        return this.stl;
    }

    /**
     * Returns the current multiplier used by this STLManager.
     * @return The current multiplier.
     */
    public void setMultiplierCap(long multiplierCap) {
        if (multiplierCap <= 0) {
            throw new IllegalArgumentException("Multiplier cap must be a positive value.");
        }
        if (multiplierCap > BigInteger.valueOf(Long.MAX_VALUE).longValue()) {
            throw new IllegalArgumentException("Multiplier cap exceeds maximum allowed value.");
        }
        if (multiplierCap < BigInteger.valueOf(Long.MIN_VALUE).longValue()) {
            throw new IllegalArgumentException("Multiplier cap is below minimum allowed value.");
        }

        this.multiplierCap = multiplierCap;
        this.setMultiplierCap = true;
    }

    /**
     * Returns the current multiplier cap used by this STLManager.
     */
    public void disableMultiplierCap() { this.setMultiplierCap = false; }

    /**
     * Changes the multiplier used by this STLManager.
     * This method can be used to change the multiplier at runtime.
     */
    public void changeMultiplier(long newMultiplier) {

        if (this.setMultiplierCap && (newMultiplier > this.multiplierCap)) {
            throw new IllegalArgumentException("New multiplier exceeds the set multiplier cap.");
        }
        this.multiplier = newMultiplier;
    }
}
