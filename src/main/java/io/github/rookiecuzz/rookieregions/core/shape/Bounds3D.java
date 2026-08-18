package io.github.rookiecuzz.rookieregions.core.shape;

/**
 * Immutable axis-aligned closure bounds.
 *
 * <p>Finite shapes must use finite coordinates. The sole unbounded instance
 * is exposed for {@link GlobalShape}; callers can distinguish it with
 * {@link #isFinite()}.</p>
 */
public record Bounds3D(double minX,
                       double minY,
                       double minZ,
                       double maxX,
                       double maxY,
                       double maxZ) {
    private static final Bounds3D UNBOUNDED = new Bounds3D(
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.NEGATIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY,
            Double.POSITIVE_INFINITY
    );

    public Bounds3D {
        requireNotNaN(minX, "minX");
        requireNotNaN(minY, "minY");
        requireNotNaN(minZ, "minZ");
        requireNotNaN(maxX, "maxX");
        requireNotNaN(maxY, "maxY");
        requireNotNaN(maxZ, "maxZ");
        if(minX > maxX || minY > maxY || minZ > maxZ){
            throw new IllegalArgumentException("bounds minimum cannot exceed maximum");
        }
        minX = normalizeZero(minX);
        minY = normalizeZero(minY);
        minZ = normalizeZero(minZ);
        maxX = normalizeZero(maxX);
        maxY = normalizeZero(maxY);
        maxZ = normalizeZero(maxZ);
    }

    public static Bounds3D unbounded(){
        return UNBOUNDED;
    }

    public boolean isFinite(){
        return Double.isFinite(minX)
                && Double.isFinite(minY)
                && Double.isFinite(minZ)
                && Double.isFinite(maxX)
                && Double.isFinite(maxY)
                && Double.isFinite(maxZ);
    }

    /** Whether the closures of the two bounds share at least one point. */
    public boolean touchesOrIntersects(Bounds3D other){
        requireOther(other);
        return maxX >= other.minX && other.maxX >= minX
                && maxY >= other.minY && other.maxY >= minY
                && maxZ >= other.minZ && other.maxZ >= minZ;
    }

    /** Whether the bounds overlap with positive length on all three axes. */
    public boolean hasPositiveVolumeIntersection(Bounds3D other){
        requireOther(other);
        return maxX > other.minX && other.maxX > minX
                && maxY > other.minY && other.maxY > minY
                && maxZ > other.minZ && other.maxZ > minZ;
    }

    public boolean contains(Bounds3D other){
        requireOther(other);
        return minX <= other.minX && maxX >= other.maxX
                && minY <= other.minY && maxY >= other.maxY
                && minZ <= other.minZ && maxZ >= other.maxZ;
    }

    private static void requireNotNaN(double value, String name){
        if(Double.isNaN(value)){
            throw new IllegalArgumentException(name + " cannot be NaN");
        }
    }

    private static void requireOther(Bounds3D other){
        if(other == null){
            throw new IllegalArgumentException("other bounds cannot be null");
        }
    }

    private static double normalizeZero(double value){
        return value == 0.0d ? 0.0d : value;
    }
}
