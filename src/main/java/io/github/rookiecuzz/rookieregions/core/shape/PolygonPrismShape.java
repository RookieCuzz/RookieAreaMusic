package io.github.rookiecuzz.rookieregions.core.shape;

import org.locationtech.jts.geom.Polygon;

import java.util.List;

/** A finite simple X/Z polygon extruded over one half-open Y interval. */
public final class PolygonPrismShape implements RegionShape {
    private final double minY;
    private final double maxY;
    private final List<Point2D> vertices;
    private final Polygon polygon;
    private final Bounds3D bounds;

    public PolygonPrismShape(double minY,
                             double maxY,
                             List<Point2D> vertices) {
        requireFiniteHeight(minY, "minY");
        requireFiniteHeight(maxY, "maxY");
        if(minY >= maxY){
            throw new IllegalArgumentException("polygon prism must have positive height");
        }
        if(!Double.isFinite(maxY - minY)){
            throw new IllegalArgumentException("polygon prism height must have finite extent");
        }
        ShapeRelations.ValidatedPolygon validated = ShapeRelations.validatePolygon(vertices);
        this.minY = normalizeZero(minY);
        this.maxY = normalizeZero(maxY);
        this.vertices = validated.vertices();
        this.polygon = validated.polygon();
        this.bounds = ShapeRelations.boundsOf(polygon, this.minY, this.maxY);
    }

    @Override
    public Bounds3D bounds(){
        return bounds;
    }

    @Override
    public double minY(){
        return minY;
    }

    @Override
    public double maxY(){
        return maxY;
    }

    public List<Point2D> vertices(){
        return vertices;
    }

    @Override
    public boolean contains(double x, double y, double z){
        return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && y >= minY && y < maxY
                && ShapeRelations.coversPoint(polygon, x, z);
    }

    Polygon polygon(){
        return polygon;
    }

    private static void requireFiniteHeight(double value, String name){
        if(!Double.isFinite(value)){
            throw new IllegalArgumentException(name + " must be finite");
        }
    }

    private static double normalizeZero(double value){
        return value == 0.0d ? 0.0d : value;
    }
}
