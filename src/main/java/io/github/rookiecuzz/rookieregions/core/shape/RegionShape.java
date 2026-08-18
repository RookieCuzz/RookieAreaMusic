package io.github.rookiecuzz.rookieregions.core.shape;

/** Immutable, non-empty geometry belonging to one world. */
public interface RegionShape {
    Bounds3D bounds();

    /**
     * Tests point membership. Finite shapes include their X/Z boundary and use
     * a lower-inclusive, upper-exclusive Y interval.
     */
    boolean contains(double x, double y, double z);

    default double minY(){
        return bounds().minY();
    }

    default double maxY(){
        return bounds().maxY();
    }

    default ShapeRelation relationTo(RegionShape other){
        return ShapeRelations.classify(this, other);
    }
}
