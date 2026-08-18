package io.github.rookiecuzz.rookieregions.core.shape;

import org.locationtech.jts.geom.Polygon;

/** A finite axis-aligned cuboid with closed X/Z and half-open Y membership. */
public final class CuboidShape implements RegionShape {
    private final Bounds3D bounds;
    private final Polygon polygon;

    public CuboidShape(Bounds3D bounds) {
        if(bounds == null || !bounds.isFinite()){
            throw new IllegalArgumentException("cuboid bounds must be finite");
        }
        if(bounds.minX() >= bounds.maxX()
                || bounds.minY() >= bounds.maxY()
                || bounds.minZ() >= bounds.maxZ()){
            throw new IllegalArgumentException("cuboid must have positive volume");
        }
        if(!Double.isFinite(bounds.maxX() - bounds.minX())
                || !Double.isFinite(bounds.maxY() - bounds.minY())
                || !Double.isFinite(bounds.maxZ() - bounds.minZ())){
            throw new IllegalArgumentException("cuboid extents must be finite");
        }
        this.bounds = bounds;
        this.polygon = ShapeRelations.rectangle(bounds);
    }

    public CuboidShape(double minX,
                        double minY,
                        double minZ,
                        double maxX,
                        double maxY,
                        double maxZ) {
        this(new Bounds3D(minX, minY, minZ, maxX, maxY, maxZ));
    }

    @Override
    public Bounds3D bounds(){
        return bounds;
    }

    @Override
    public boolean contains(double x, double y, double z){
        return coordinatesAreFinite(x, y, z)
                && x >= bounds.minX() && x <= bounds.maxX()
                && y >= bounds.minY() && y < bounds.maxY()
                && z >= bounds.minZ() && z <= bounds.maxZ();
    }

    Polygon polygon(){
        return polygon;
    }

    private static boolean coordinatesAreFinite(double x, double y, double z){
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z);
    }
}
