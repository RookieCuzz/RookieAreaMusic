package io.github.rookiecuzz.rookieregions.editor.model;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.Point2D;
import io.github.rookiecuzz.rookieregions.core.shape.PolygonPrismShape;
import io.github.rookiecuzz.rookieregions.core.shape.RegionShape;
import io.github.rookiecuzz.rookieregions.core.shape.SlicedPolygonShape;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;

/** Mutable per-session geometry draft whose published shapes are immutable. */
public final class RegionDraft {
    private final WorldId world;
    private final ShapeKind kind;

    private BlockPoint cuboidPos1;
    private BlockPoint cuboidPos2;

    private final List<Point2D> polygonVertices = new ArrayList<>();
    private Double polygonMinY;
    private Double polygonMaxY;

    private final NavigableMap<Double, List<Point2D>> slicedVertices = new TreeMap<>();
    private Double currentSliceY;
    private Double slicedMinY;
    private Double slicedMaxY;

    public RegionDraft(WorldId world, ShapeKind kind) {
        this.world = Objects.requireNonNull(world, "draft world cannot be null");
        this.kind = Objects.requireNonNull(kind, "shape kind cannot be null");
    }

    public static RegionDraft cuboid(WorldId world) {
        return new RegionDraft(world, ShapeKind.CUBOID);
    }

    public static RegionDraft polygon(WorldId world) {
        return new RegionDraft(world, ShapeKind.POLYGON);
    }

    public static RegionDraft sliced(WorldId world) {
        return new RegionDraft(world, ShapeKind.SLICED);
    }

    /** Initializes a block editor draft without silently rounding geometry. */
    public static RegionDraft from(Region region) {
        Objects.requireNonNull(region, "region cannot be null");
        return fromShape(region.key().world(), region.shape());
    }

    /** Initializes a block editor draft without silently rounding geometry. */
    public static RegionDraft fromShape(WorldId world, RegionShape shape) {
        Objects.requireNonNull(shape, "shape cannot be null");
        if(shape instanceof CuboidShape cuboid) {
            Bounds3D bounds = cuboid.bounds();
            RegionDraft draft = cuboid(world);
            draft.cuboidPos1 = new BlockPoint(
                    gridCoordinate(bounds.minX(), "cuboid minX"),
                    gridCoordinate(bounds.minY(), "cuboid minY"),
                    gridCoordinate(bounds.minZ(), "cuboid minZ")
            );
            draft.cuboidPos2 = new BlockPoint(
                    inclusiveMaximum(bounds.maxX(), "cuboid maxX"),
                    inclusiveMaximum(bounds.maxY(), "cuboid maxY"),
                    inclusiveMaximum(bounds.maxZ(), "cuboid maxZ")
            );
            return draft;
        }
        if(shape instanceof PolygonPrismShape polygon) {
            RegionDraft draft = polygon(world);
            draft.polygonMinY = polygon.minY();
            draft.polygonMaxY = polygon.maxY();
            draft.polygonVertices.addAll(polygon.vertices());
            return draft;
        }
        if(shape instanceof SlicedPolygonShape sliced) {
            RegionDraft draft = sliced(world);
            for(SlicedPolygonShape.Slice slice : sliced.slices()) {
                draft.slicedVertices.put(slice.y(), new ArrayList<>(slice.vertices()));
            }
            draft.currentSliceY = sliced.slices().getFirst().y();
            draft.slicedMinY = sliced.minY();
            draft.slicedMaxY = sliced.maxY();
            return draft;
        }
        throw new IllegalArgumentException(
                "shape is not editable by the block editor: " + shape.getClass().getName()
        );
    }

    public WorldId world() {
        return world;
    }

    public ShapeKind kind() {
        return kind;
    }

    public RegionDraft setPos1(BlockPoint point) {
        requireKind(ShapeKind.CUBOID);
        cuboidPos1 = Objects.requireNonNull(point, "cuboid position 1 cannot be null");
        return this;
    }

    public RegionDraft setPos2(BlockPoint point) {
        requireKind(ShapeKind.CUBOID);
        cuboidPos2 = Objects.requireNonNull(point, "cuboid position 2 cannot be null");
        return this;
    }

    public RegionDraft pos1(BlockPoint point) {
        return setPos1(point);
    }

    public RegionDraft pos2(BlockPoint point) {
        return setPos2(point);
    }

    public Optional<BlockPoint> pos1() {
        return Optional.ofNullable(cuboidPos1);
    }

    public Optional<BlockPoint> pos2() {
        return Optional.ofNullable(cuboidPos2);
    }

    public BlockPoint getPos1() {
        return cuboidPos1;
    }

    public BlockPoint getPos2() {
        return cuboidPos2;
    }

    /** Sets the polygon prism's direct half-open Y range. */
    public RegionDraft setPolygonHeights(double minY, double maxY) {
        requireKind(ShapeKind.POLYGON);
        polygonMinY = finite(minY, "polygon minY");
        polygonMaxY = finite(maxY, "polygon maxY");
        return this;
    }

    public RegionDraft setHeightRange(double minY, double maxY) {
        return setPolygonHeights(minY, maxY);
    }

    public RegionDraft setMinY(double minY) {
        return switch(kind) {
            case POLYGON -> {
                polygonMinY = finite(minY, "polygon minY");
                yield this;
            }
            case SLICED -> moveFirstSliceTo(minY);
            case CUBOID -> throw wrongKind(
                    "minY", ShapeKind.POLYGON, ShapeKind.SLICED
            );
        };
    }

    /** Sets polygon maxY or sliced maxY according to this draft's kind. */
    public RegionDraft setMaxY(double maxY) {
        return switch(kind) {
            case POLYGON -> {
                polygonMaxY = finite(maxY, "polygon maxY");
                yield this;
            }
            case SLICED -> setSlicedMaxY(maxY);
            case CUBOID -> throw wrongKind("maxY", ShapeKind.POLYGON, ShapeKind.SLICED);
        };
    }

    public OptionalDouble minY() {
        Double value = kind == ShapeKind.POLYGON ? polygonMinY : slicedMinY;
        return value == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(value);
    }

    public OptionalDouble maxY() {
        Double value = kind == ShapeKind.POLYGON ? polygonMaxY : slicedMaxY;
        return value == null ? OptionalDouble.empty() : OptionalDouble.of(value);
    }

    /** Selects an existing slice or creates an empty one at this Y. */
    public RegionDraft selectSlice(double y) {
        requireKind(ShapeKind.SLICED);
        double normalized = finite(y, "slice Y");
        slicedVertices.computeIfAbsent(normalized, ignored -> new ArrayList<>());
        if(slicedMinY == null || normalized < slicedMinY){
            slicedMinY = normalized;
        }
        currentSliceY = normalized;
        return this;
    }

    public RegionDraft switchSlice(double y) {
        return selectSlice(y);
    }

    public RegionDraft setCurrentSliceY(double y) {
        return selectSlice(y);
    }

    public OptionalDouble currentSliceY() {
        return currentSliceY == null
                ? OptionalDouble.empty()
                : OptionalDouble.of(currentSliceY);
    }

    public RegionDraft setSlicedMaxY(double maxY) {
        requireKind(ShapeKind.SLICED);
        slicedMaxY = finite(maxY, "sliced maxY");
        return this;
    }

    /** Sets the explicit lower bound; it must equal the first slice at build time. */
    public RegionDraft setSlicedMinY(double minY) {
        requireKind(ShapeKind.SLICED);
        slicedMinY = finite(minY, "sliced minY");
        return this;
    }

    /** Moves the first slice together with the sliced prism's lower bound. */
    private RegionDraft moveFirstSliceTo(double minY) {
        requireKind(ShapeKind.SLICED);
        double normalized = finite(minY, "sliced minY");
        if(slicedVertices.isEmpty()) {
            slicedMinY = normalized;
            return this;
        }
        double first = slicedVertices.firstKey();
        if(Double.compare(first, normalized) == 0) {
            slicedMinY = normalized;
            return this;
        }
        Double second = slicedVertices.higherKey(first);
        if(second != null && normalized >= second) {
            throw new IllegalArgumentException(
                    "sliced minY must stay below the second slice Y"
            );
        }
        if(slicedMaxY != null && normalized >= slicedMaxY) {
            throw new IllegalArgumentException(
                    "sliced minY must stay below maxY"
            );
        }
        List<Point2D> vertices = slicedVertices.remove(first);
        slicedVertices.put(normalized, vertices);
        if(currentSliceY != null && Double.compare(currentSliceY, first) == 0) {
            currentSliceY = normalized;
        }
        slicedMinY = normalized;
        return this;
    }

    /** Adds an X/Z vertex; BlockPoint Y never changes the active height. */
    public RegionDraft addPoint(BlockPoint point) {
        Objects.requireNonNull(point, "editor point cannot be null");
        return addPoint(point.x(), point.z());
    }

    public RegionDraft addPoint(int x, int z) {
        return addPoint(new Point2D(x, z));
    }

    public RegionDraft addVertex(BlockPoint point) {
        return addPoint(point);
    }

    public RegionDraft addVertex(int x, int z) {
        return addPoint(x, z);
    }

    public RegionDraft addPoint(Point2D point) {
        Objects.requireNonNull(point, "editor point cannot be null");
        mutableCurrentVertices().add(point);
        return this;
    }

    public Optional<Point2D> undoPoint() {
        List<Point2D> points = mutableCurrentVertices();
        if(points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(points.removeLast());
    }

    public Optional<Point2D> undoVertex() {
        return undoPoint();
    }

    public int clearPoints() {
        List<Point2D> points = mutableCurrentVertices();
        int removed = points.size();
        points.clear();
        return removed;
    }

    public int clearVertices() {
        return clearPoints();
    }

    public List<Point2D> points() {
        return List.copyOf(currentVertices());
    }

    public List<Point2D> polygonVertices() {
        requireKind(ShapeKind.POLYGON);
        return List.copyOf(polygonVertices);
    }

    public List<SlicedPolygonShape.Slice> slices() {
        requireKind(ShapeKind.SLICED);
        return slicedVertices.entrySet().stream()
                .map(entry -> new SlicedPolygonShape.Slice(
                        entry.getKey(), List.copyOf(entry.getValue())
                ))
                .toList();
    }

    public RegionShape buildShape() {
        return switch(kind) {
            case CUBOID -> buildCuboid();
            case POLYGON -> buildPolygon();
            case SLICED -> buildSliced();
        };
    }

    public RegionShape toShape() {
        return buildShape();
    }

    public RegionDraft copy() {
        RegionDraft copy = new RegionDraft(world, kind);
        copy.cuboidPos1 = cuboidPos1;
        copy.cuboidPos2 = cuboidPos2;
        copy.polygonVertices.addAll(polygonVertices);
        copy.polygonMinY = polygonMinY;
        copy.polygonMaxY = polygonMaxY;
        for(Map.Entry<Double, List<Point2D>> entry : slicedVertices.entrySet()) {
            copy.slicedVertices.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        copy.currentSliceY = currentSliceY;
        copy.slicedMinY = slicedMinY;
        copy.slicedMaxY = slicedMaxY;
        return copy;
    }

    private RegionShape buildCuboid() {
        if(cuboidPos1 == null || cuboidPos2 == null) {
            throw new IllegalStateException("cuboid requires both position 1 and position 2");
        }
        double minX = Math.min(cuboidPos1.x(), cuboidPos2.x());
        double minY = Math.min(cuboidPos1.y(), cuboidPos2.y());
        double minZ = Math.min(cuboidPos1.z(), cuboidPos2.z());
        double maxX = (double) Math.max(cuboidPos1.x(), cuboidPos2.x()) + 1.0d;
        double maxY = (double) Math.max(cuboidPos1.y(), cuboidPos2.y()) + 1.0d;
        double maxZ = (double) Math.max(cuboidPos1.z(), cuboidPos2.z()) + 1.0d;
        return new CuboidShape(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private RegionShape buildPolygon() {
        if(polygonMinY == null || polygonMaxY == null) {
            throw new IllegalStateException("polygon requires explicit minY and maxY");
        }
        if(polygonMaxY <= polygonMinY) {
            throw new IllegalStateException("polygon maxY must be above minY");
        }
        requireMinimumVertices(polygonVertices, "polygon");
        return new PolygonPrismShape(
                polygonMinY, polygonMaxY, List.copyOf(polygonVertices)
        );
    }

    private RegionShape buildSliced() {
        if(slicedVertices.isEmpty()) {
            throw new IllegalStateException("sliced polygon requires at least one slice");
        }
        if(slicedMinY == null) {
            throw new IllegalStateException("sliced polygon requires explicit minY");
        }
        if(slicedMaxY == null) {
            throw new IllegalStateException("sliced polygon requires explicit maxY");
        }
        if(Double.compare(slicedVertices.firstKey(), slicedMinY) != 0) {
            throw new IllegalStateException("sliced minY must equal the first slice Y");
        }
        double finalSliceY = slicedVertices.lastKey();
        if(slicedMaxY <= finalSliceY) {
            throw new IllegalStateException("sliced maxY must be above the final slice Y");
        }
        List<SlicedPolygonShape.Slice> slices = new ArrayList<>();
        for(Map.Entry<Double, List<Point2D>> entry : slicedVertices.entrySet()) {
            requireMinimumVertices(entry.getValue(), "slice at Y " + entry.getKey());
            slices.add(new SlicedPolygonShape.Slice(
                    entry.getKey(), List.copyOf(entry.getValue())
            ));
        }
        return new SlicedPolygonShape(slicedMinY, slicedMaxY, slices);
    }

    private List<Point2D> mutableCurrentVertices() {
        return switch(kind) {
            case POLYGON -> polygonVertices;
            case SLICED -> {
                if(currentSliceY == null) {
                    throw new IllegalStateException(
                            "select a sliced-polygon Y before editing points"
                    );
                }
                yield slicedVertices.get(currentSliceY);
            }
            case CUBOID -> throw wrongKind(
                    "point editing", ShapeKind.POLYGON, ShapeKind.SLICED
            );
        };
    }

    private List<Point2D> currentVertices() {
        return switch(kind) {
            case POLYGON -> polygonVertices;
            case SLICED -> currentSliceY == null
                    ? Collections.emptyList()
                    : slicedVertices.get(currentSliceY);
            case CUBOID -> Collections.emptyList();
        };
    }

    private void requireKind(ShapeKind expected) {
        if(kind != expected) {
            throw wrongKind(expected.name().toLowerCase(), expected);
        }
    }

    private IllegalStateException wrongKind(String operation,
                                            ShapeKind... supported) {
        return new IllegalStateException(
                operation + " is only valid for " + List.of(supported)
                        + " drafts, not " + kind
        );
    }

    private static void requireMinimumVertices(List<Point2D> vertices,
                                               String label) {
        if(vertices.size() < 3) {
            throw new IllegalStateException(label + " requires at least three X/Z vertices");
        }
    }

    private static double finite(double value, String label) {
        if(!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
        return value == 0.0d ? 0.0d : value;
    }

    private static int gridCoordinate(double value, String label) {
        if(!Double.isFinite(value) || value != Math.rint(value)
                || value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " is not an exact block coordinate");
        }
        return (int) value;
    }

    private static int inclusiveMaximum(double exclusive, String label) {
        if(!Double.isFinite(exclusive) || exclusive != Math.rint(exclusive)) {
            throw new IllegalArgumentException(label + " is not an exact block boundary");
        }
        double inclusive = exclusive - 1.0d;
        if(inclusive < Integer.MIN_VALUE || inclusive > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(label + " is outside block coordinate range");
        }
        return (int) inclusive;
    }
}
