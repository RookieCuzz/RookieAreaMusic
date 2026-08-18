package io.github.rookiecuzz.rookieregions.core.shape;

import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Envelope;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.LinearRing;
import org.locationtech.jts.geom.Polygon;
import org.locationtech.jts.operation.overlayng.OverlayNG;
import org.locationtech.jts.operation.overlayng.OverlayNGRobust;
import org.locationtech.jts.operation.valid.IsValidOp;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Set;
import java.util.TreeSet;

/** Central relation engine for every built-in region shape. */
public final class ShapeRelations {
    private static final GeometryFactory GEOMETRY_FACTORY = new GeometryFactory();

    private ShapeRelations(){
    }

    /**
     * Classifies {@code first} relative to {@code second}.
     *
     * <p>Containment is proper containment: a contained shape may share some
     * boundary with its container, but geometrically equal shapes are reported
     * as {@link ShapeRelation#EQUAL}. A positive shared 3-D volume is required
     * for containment and overlap; closure-only contact is
     * {@link ShapeRelation#TOUCHING}.</p>
     */
    public static ShapeRelation classify(RegionShape first, RegionShape second){
        if(first == null || second == null){
            throw new IllegalArgumentException("shapes cannot be null");
        }
        if(first == second){
            return ShapeRelation.EQUAL;
        }
        if(first instanceof GlobalShape){
            return second instanceof GlobalShape
                    ? ShapeRelation.EQUAL
                    : ShapeRelation.CONTAINS;
        }
        if(second instanceof GlobalShape){
            return ShapeRelation.INSIDE;
        }
        if(!isFiniteBuiltIn(first) || !isFiniteBuiltIn(second)){
            throw new IllegalArgumentException(
                    "unsupported region shape pair: "
                            + first.getClass().getName() + " / " + second.getClass().getName()
            );
        }
        if(!first.bounds().touchesOrIntersects(second.bounds())){
            return ShapeRelation.DISJOINT;
        }

        List<Slab> firstSlabs = slabsOf(first);
        List<Slab> secondSlabs = slabsOf(second);
        NavigableSet<Double> eventSet = new TreeSet<>();
        addEvents(eventSet, firstSlabs);
        addEvents(eventSet, secondSlabs);
        List<Double> events = new ArrayList<>(eventSet);
        PlanarRelationCache cache = new PlanarRelationCache();

        boolean firstContainsSecond = true;
        boolean secondContainsFirst = true;
        boolean positiveVolumeIntersection = false;
        boolean closureContact = false;

        for(int index = 0; index + 1 < events.size(); index++){
            double bottom = events.get(index);
            double top = events.get(index + 1);
            if(top <= bottom){
                continue;
            }
            double sampleY = bottom + (top - bottom) / 2.0d;
            Slab firstActive = activeSlabAt(firstSlabs, sampleY);
            Slab secondActive = activeSlabAt(secondSlabs, sampleY);

            if(secondActive != null){
                if(firstActive == null){
                    firstContainsSecond = false;
                } else if(!cache.relation(firstActive.footprint(), secondActive.footprint())
                        .firstContainsSecond()){
                    firstContainsSecond = false;
                }
            }
            if(firstActive != null){
                if(secondActive == null){
                    secondContainsFirst = false;
                } else if(!cache.relation(firstActive.footprint(), secondActive.footprint())
                        .secondContainsFirst()){
                    secondContainsFirst = false;
                }
            }
            if(firstActive != null && secondActive != null){
                PlanarRelation planar = cache.relation(
                        firstActive.footprint(),
                        secondActive.footprint()
                );
                if(planar.positiveAreaIntersection()){
                    positiveVolumeIntersection = true;
                } else if(planar.intersectsClosure()){
                    closureContact = true;
                }
            }
        }

        // At an event plane, both the outgoing and incoming slab contribute
        // to the volume's closure. This detects faces at Y transitions as well
        // as top-to-bottom contact between otherwise disjoint shapes.
        for(double event : events){
            List<Slab> firstAtEvent = closureSlabsAt(firstSlabs, event);
            List<Slab> secondAtEvent = closureSlabsAt(secondSlabs, event);
            for(Slab firstSlab : firstAtEvent){
                for(Slab secondSlab : secondAtEvent){
                    if(cache.relation(firstSlab.footprint(), secondSlab.footprint())
                            .intersectsClosure()){
                        closureContact = true;
                    }
                }
            }
        }

        if(firstContainsSecond && secondContainsFirst){
            return ShapeRelation.EQUAL;
        }
        if(positiveVolumeIntersection){
            if(firstContainsSecond){
                return ShapeRelation.CONTAINS;
            }
            if(secondContainsFirst){
                return ShapeRelation.INSIDE;
            }
            return ShapeRelation.OVERLAP;
        }
        return closureContact ? ShapeRelation.TOUCHING : ShapeRelation.DISJOINT;
    }

    /**
     * Returns the finite positive-volume intersection measure of two finite
     * built-in shapes. The result is symmetric and is exactly zero for
     * disjoint or closure-only contact.
     *
     * <p>The vertical integration uses {@link BigDecimal} so comparing an
     * edited overlap with its previous value cannot overflow merely because
     * otherwise-valid finite coordinates have a very large magnitude. JTS is
     * still the single source of truth for each planar intersection area.</p>
     */
    public static BigDecimal positiveIntersectionVolume(RegionShape first,
                                                        RegionShape second){
        if(first == null || second == null){
            throw new IllegalArgumentException("shapes cannot be null");
        }
        if(!isFiniteBuiltIn(first) || !isFiniteBuiltIn(second)){
            throw new IllegalArgumentException(
                    "intersection volume requires two finite built-in shapes"
            );
        }
        if(!first.bounds().touchesOrIntersects(second.bounds())){
            return BigDecimal.ZERO;
        }

        List<Slab> firstSlabs = slabsOf(first);
        List<Slab> secondSlabs = slabsOf(second);
        PlanarRelationCache cache = new PlanarRelationCache();
        BigDecimal volume = BigDecimal.ZERO;
        int firstIndex = 0;
        int secondIndex = 0;
        while(firstIndex < firstSlabs.size() && secondIndex < secondSlabs.size()){
            Slab firstSlab = firstSlabs.get(firstIndex);
            Slab secondSlab = secondSlabs.get(secondIndex);
            double bottom = Math.max(firstSlab.minY(), secondSlab.minY());
            double top = Math.min(firstSlab.maxY(), secondSlab.maxY());
            if(top > bottom){
                double area = cache.relation(
                        firstSlab.footprint(), secondSlab.footprint()
                ).intersectionArea();
                if(!Double.isFinite(area)){
                    throw new IllegalStateException(
                            "planar intersection area is not finite"
                    );
                }
                if(area > 0.0d){
                    BigDecimal height = BigDecimal.valueOf(top).subtract(
                            BigDecimal.valueOf(bottom)
                    );
                    volume = volume.add(BigDecimal.valueOf(area).multiply(height));
                }
            }
            int endComparison = Double.compare(
                    firstSlab.maxY(), secondSlab.maxY()
            );
            if(endComparison <= 0){
                firstIndex++;
            }
            if(endComparison >= 0){
                secondIndex++;
            }
        }
        return volume.signum() == 0
                ? BigDecimal.ZERO
                : volume.stripTrailingZeros();
    }

    static boolean coversPoint(Polygon polygon, double x, double z){
        return polygon.covers(GEOMETRY_FACTORY.createPoint(new Coordinate(x, z)));
    }

    static Bounds3D boundsOf(Polygon polygon, double minY, double maxY){
        Envelope envelope = polygon.getEnvelopeInternal();
        return new Bounds3D(
                envelope.getMinX(), minY, envelope.getMinY(),
                envelope.getMaxX(), maxY, envelope.getMaxY()
        );
    }

    static ValidatedPolygon validatePolygon(List<Point2D> source){
        if(source == null){
            throw new IllegalArgumentException("polygon vertices cannot be null");
        }
        List<Point2D> vertices;
        try {
            vertices = new ArrayList<>(List.copyOf(source));
        } catch(NullPointerException exception){
            throw new IllegalArgumentException("polygon vertices cannot contain null", exception);
        }
        if(vertices.size() > 1 && vertices.get(0).equals(vertices.get(vertices.size() - 1))){
            vertices.remove(vertices.size() - 1);
        }
        if(vertices.size() < 3){
            throw new IllegalArgumentException("polygon must contain at least three vertices");
        }
        for(int index = 0; index < vertices.size(); index++){
            Point2D current = vertices.get(index);
            Point2D next = vertices.get((index + 1) % vertices.size());
            if(current.equals(next)){
                throw new IllegalArgumentException("polygon cannot contain consecutive duplicate vertices");
            }
        }
        Set<Point2D> distinct = new LinkedHashSet<>(vertices);
        if(distinct.size() < 3){
            throw new IllegalArgumentException("polygon must contain at least three distinct vertices");
        }

        Coordinate[] coordinates = new Coordinate[vertices.size() + 1];
        for(int index = 0; index < vertices.size(); index++){
            Point2D vertex = vertices.get(index);
            coordinates[index] = new Coordinate(vertex.x(), vertex.z());
        }
        coordinates[vertices.size()] = new Coordinate(vertices.get(0).x(), vertices.get(0).z());
        LinearRing shell = GEOMETRY_FACTORY.createLinearRing(coordinates);
        Polygon polygon = GEOMETRY_FACTORY.createPolygon(shell);
        IsValidOp validation = new IsValidOp(polygon);
        if(!validation.isValid()){
            String reason = validation.getValidationError() == null
                    ? "unknown topology error"
                    : validation.getValidationError().getMessage();
            throw new IllegalArgumentException("polygon is not valid: " + reason);
        }
        if(polygon.isEmpty() || polygon.getArea() <= 0.0d
                || !Double.isFinite(polygon.getArea())){
            throw new IllegalArgumentException("polygon must have finite positive area");
        }
        return new ValidatedPolygon(List.copyOf(vertices), polygon);
    }

    private static boolean isFiniteBuiltIn(RegionShape shape){
        return shape instanceof CuboidShape
                || shape instanceof PolygonPrismShape
                || shape instanceof SlicedPolygonShape;
    }

    private static List<Slab> slabsOf(RegionShape shape){
        if(shape instanceof CuboidShape cuboid){
            return Collections.singletonList(new Slab(
                    cuboid.minY(),
                    cuboid.maxY(),
                    cuboid.polygon()
            ));
        }
        if(shape instanceof PolygonPrismShape polygon){
            return Collections.singletonList(new Slab(
                    polygon.minY(),
                    polygon.maxY(),
                    polygon.polygon()
            ));
        }
        if(shape instanceof SlicedPolygonShape sliced){
            List<Slab> result = new ArrayList<>(sliced.slices().size());
            for(int index = 0; index < sliced.slices().size(); index++){
                double top = index + 1 < sliced.slices().size()
                        ? sliced.slices().get(index + 1).y()
                        : sliced.maxY();
                result.add(new Slab(
                        sliced.slices().get(index).y(),
                        top,
                        sliced.polygons().get(index)
                ));
            }
            return result;
        }
        throw new IllegalArgumentException("unsupported finite shape: " + shape.getClass().getName());
    }

    static Polygon rectangle(Bounds3D bounds){
        Coordinate[] coordinates = new Coordinate[]{
                new Coordinate(bounds.minX(), bounds.minZ()),
                new Coordinate(bounds.maxX(), bounds.minZ()),
                new Coordinate(bounds.maxX(), bounds.maxZ()),
                new Coordinate(bounds.minX(), bounds.maxZ()),
                new Coordinate(bounds.minX(), bounds.minZ())
        };
        return GEOMETRY_FACTORY.createPolygon(coordinates);
    }

    private static void addEvents(NavigableSet<Double> events, List<Slab> slabs){
        for(Slab slab : slabs){
            events.add(slab.minY());
            events.add(slab.maxY());
        }
    }

    private static Slab activeSlabAt(List<Slab> slabs, double y){
        int lower = 0;
        int upper = slabs.size() - 1;
        int candidate = -1;
        while(lower <= upper){
            int middle = (lower + upper) >>> 1;
            Slab slab = slabs.get(middle);
            if(slab.minY() <= y){
                candidate = middle;
                lower = middle + 1;
            } else {
                upper = middle - 1;
            }
        }
        if(candidate < 0){
            return null;
        }
        Slab result = slabs.get(candidate);
        return y < result.maxY() ? result : null;
    }

    private static List<Slab> closureSlabsAt(List<Slab> slabs, double y){
        List<Slab> result = new ArrayList<>(2);
        for(Slab slab : slabs){
            if(slab.minY() <= y && y <= slab.maxY()){
                result.add(slab);
            }
        }
        return result;
    }

    private static PlanarRelation computePlanarRelation(Polygon first, Polygon second){
        Envelope firstEnvelope = first.getEnvelopeInternal();
        Envelope secondEnvelope = second.getEnvelopeInternal();
        if(!firstEnvelope.intersects(secondEnvelope)){
            return PlanarRelation.DISJOINT;
        }

        boolean intersects = first.intersects(second);
        if(!intersects){
            return new PlanarRelation(
                    false,
                    0.0d,
                    firstEnvelope.covers(secondEnvelope) && first.covers(second),
                    secondEnvelope.covers(firstEnvelope) && second.covers(first)
            );
        }
        Geometry intersection = OverlayNGRobust.overlay(first, second, OverlayNG.INTERSECTION);
        double intersectionArea = intersection.getArea();
        return new PlanarRelation(
                true,
                intersectionArea > 0.0d ? intersectionArea : 0.0d,
                firstEnvelope.covers(secondEnvelope) && first.covers(second),
                secondEnvelope.covers(firstEnvelope) && second.covers(first)
        );
    }

    static record ValidatedPolygon(List<Point2D> vertices, Polygon polygon) {
    }

    private record Slab(double minY, double maxY, Polygon footprint) {
    }

    private record PlanarRelation(boolean intersectsClosure,
                                  double intersectionArea,
                                  boolean firstContainsSecond,
                                  boolean secondContainsFirst) {
        private static final PlanarRelation DISJOINT =
                new PlanarRelation(false, 0.0d, false, false);

        private boolean positiveAreaIntersection(){
            return intersectionArea > 0.0d;
        }
    }

    private static final class PlanarRelationCache {
        private final Map<Polygon, Map<Polygon, PlanarRelation>> values =
                new IdentityHashMap<>();

        private PlanarRelation relation(Polygon first, Polygon second){
            Map<Polygon, PlanarRelation> bySecond = values.computeIfAbsent(
                    first,
                    ignored -> new IdentityHashMap<>()
            );
            return bySecond.computeIfAbsent(
                    second,
                    ignored -> computePlanarRelation(first, second)
            );
        }
    }
}
