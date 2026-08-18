package io.github.rookiecuzz.rookieregions.core.shape;

import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Polygon;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A finite stack of independent polygon slabs.
 *
 * <p>Each slice starts at its {@link Slice#y()} and remains active until the
 * next slice. The final slice remains active until the shape's explicit
 * {@code maxY}. Both vertical bounds are explicit. Slice heights must be
 * strictly increasing and the first slice must start exactly at
 * {@code minY}, so every height in {@code [minY,maxY)} has a defined
 * polygon.</p>
 */
public final class SlicedPolygonShape implements RegionShape {
    public static final int MAX_SLICE_COUNT = 512;
    public static final int MAX_VERTICES_PER_SLICE = 512;
    public static final int MAX_TOTAL_VERTICES = 32768;

    public record Slice(double y, List<Point2D> vertices) {
        public Slice {
            if(!Double.isFinite(y)){
                throw new IllegalArgumentException("slice Y must be finite");
            }
            if(vertices == null){
                throw new IllegalArgumentException("slice vertices cannot be null");
            }
            y = y == 0.0d ? 0.0d : y;
            try {
                vertices = List.copyOf(vertices);
            } catch(NullPointerException exception){
                throw new IllegalArgumentException("slice vertices cannot contain null", exception);
            }
        }
    }

    private final double minY;
    private final double maxY;
    private final List<Slice> slices;
    private final List<Polygon> polygons;
    private final double[] sliceHeights;
    private final Bounds3D bounds;

    public SlicedPolygonShape(double minY,
                              double maxY,
                              List<Slice> sourceSlices) {
        if(!Double.isFinite(minY) || !Double.isFinite(maxY)){
            throw new IllegalArgumentException("minY and maxY must be finite");
        }
        minY = minY == 0.0d ? 0.0d : minY;
        maxY = maxY == 0.0d ? 0.0d : maxY;
        if(maxY <= minY || !Double.isFinite(maxY - minY)){
            throw new IllegalArgumentException(
                    "maxY must be greater than minY with finite extent"
            );
        }
        if(sourceSlices == null || sourceSlices.isEmpty()){
            throw new IllegalArgumentException("sliced polygon must contain at least one slice");
        }
        if(sourceSlices.size() > MAX_SLICE_COUNT){
            throw new IllegalArgumentException("slice count cannot exceed " + MAX_SLICE_COUNT);
        }

        List<Slice> normalizedSlices = new ArrayList<>(sourceSlices.size());
        List<Polygon> normalizedPolygons = new ArrayList<>(sourceSlices.size());
        long totalVertices = 0L;
        double previousY = Double.NEGATIVE_INFINITY;
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minZ = Double.POSITIVE_INFINITY;
        double maxZ = Double.NEGATIVE_INFINITY;

        for(int index = 0; index < sourceSlices.size(); index++){
            Slice source = sourceSlices.get(index);
            if(source == null){
                throw new IllegalArgumentException("slices cannot contain null");
            }
            if(index > 0 && source.y() <= previousY){
                throw new IllegalArgumentException("slice Y coordinates must be strictly increasing");
            }
            if(source.vertices().size() > MAX_VERTICES_PER_SLICE){
                throw new IllegalArgumentException(
                        "vertices per slice cannot exceed " + MAX_VERTICES_PER_SLICE
                );
            }
            totalVertices += source.vertices().size();
            if(totalVertices > MAX_TOTAL_VERTICES){
                throw new IllegalArgumentException(
                        "total vertices cannot exceed " + MAX_TOTAL_VERTICES
                );
            }

            ShapeRelations.ValidatedPolygon validated =
                    ShapeRelations.validatePolygon(source.vertices());
            normalizedSlices.add(new Slice(source.y(), validated.vertices()));
            normalizedPolygons.add(validated.polygon());
            Envelope envelope = validated.polygon().getEnvelopeInternal();
            minX = Math.min(minX, envelope.getMinX());
            maxX = Math.max(maxX, envelope.getMaxX());
            minZ = Math.min(minZ, envelope.getMinY());
            maxZ = Math.max(maxZ, envelope.getMaxY());
            previousY = source.y();
        }

        if(Double.compare(normalizedSlices.getFirst().y(), minY) != 0){
            throw new IllegalArgumentException(
                    "first slice Y must equal explicit minY"
            );
        }
        if(maxY <= previousY){
            throw new IllegalArgumentException("maxY must be above the final slice Y");
        }
        this.minY = minY;
        this.maxY = maxY;
        this.slices = Collections.unmodifiableList(normalizedSlices);
        this.polygons = Collections.unmodifiableList(normalizedPolygons);
        this.sliceHeights = new double[normalizedSlices.size()];
        for(int index = 0; index < normalizedSlices.size(); index++){
            this.sliceHeights[index] = normalizedSlices.get(index).y();
        }
        this.bounds = new Bounds3D(minX, minY, minZ, maxX, this.maxY, maxZ);
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

    public List<Slice> slices(){
        return slices;
    }

    @Override
    public boolean contains(double x, double y, double z){
        if(!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || y < minY || y >= maxY){
            return false;
        }
        int lower = 0;
        int upper = sliceHeights.length - 1;
        int active = 0;
        while(lower <= upper){
            int middle = (lower + upper) >>> 1;
            if(sliceHeights[middle] <= y){
                active = middle;
                lower = middle + 1;
            } else {
                upper = middle - 1;
            }
        }
        return ShapeRelations.coversPoint(polygons.get(active), x, z);
    }

    List<Polygon> polygons(){
        return polygons;
    }
}
