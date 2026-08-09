package com.gitee.niocho.areamusic.geometry;

import com.gitee.niocho.areamusic.config.AreaDto;
import com.gitee.niocho.areamusic.config.RegionShapeConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SlicedPolygonVolume {
    private static final double EPSILON = 1.0E-7;

    private final List<SliceData> slices;
    private final double[] sliceHeights;
    private final double minX;
    private final double maxX;
    private final double minY;
    private final double maxY;
    private final double minZ;
    private final double maxZ;

    public SlicedPolygonVolume(RegionShapeConfig source){
        if(source == null
                || source.getType() == null
                || !"sliced_polygon".equalsIgnoreCase(source.getType())
                || source.getSlices() == null
                || source.getSlices().isEmpty()){
            throw new IllegalArgumentException("shape 必须包含至少一个 sliced_polygon 切片");
        }

        List<SliceData> normalized = new ArrayList<>();
        for(RegionShapeConfig.Slice slice : source.getSlices()){
            normalized.add(validateAndConvert(slice));
        }
        normalized.sort(Comparator.comparingDouble(item -> item.y));
        for(int index = 1; index < normalized.size(); index++){
            if(Math.abs(normalized.get(index).y - normalized.get(index - 1).y) <= EPSILON){
                throw new IllegalArgumentException("切片 y 坐标不能重复");
            }
        }
        this.slices = normalized;
        this.sliceHeights = new double[normalized.size()];
        for(int index = 0; index < normalized.size(); index++){
            this.sliceHeights[index] = normalized.get(index).y;
        }

        double computedMinX = Double.POSITIVE_INFINITY;
        double computedMaxX = Double.NEGATIVE_INFINITY;
        double computedMinZ = Double.POSITIVE_INFINITY;
        double computedMaxZ = Double.NEGATIVE_INFINITY;
        for(SliceData slice : normalized){
            for(Point2 point : slice.points){
                computedMinX = Math.min(computedMinX, point.x);
                computedMaxX = Math.max(computedMaxX, point.x);
                computedMinZ = Math.min(computedMinZ, point.z);
                computedMaxZ = Math.max(computedMaxZ, point.z);
            }
        }
        this.minX = computedMinX;
        this.maxX = computedMaxX;
        this.minY = normalized.get(0).y;
        this.maxY = normalized.get(normalized.size() - 1).y;
        this.minZ = computedMinZ;
        this.maxZ = computedMaxZ;
    }

    public boolean contains(double x, double y, double z){
        if(x < minX - EPSILON || x > maxX + EPSILON
                || y < minY - EPSILON || y >= maxY + 1.0 - EPSILON
                || z < minZ - EPSILON || z > maxZ + EPSILON){
            return false;
        }

        // 阶梯式体素生长：选择当前高度以下最近的切片，
        // 直接复制其 Polygon，直到到达下一张切片。
        int lower = 0;
        int upper = sliceHeights.length - 1;
        int activeIndex = 0;
        while(lower <= upper){
            int middle = (lower + upper) >>> 1;
            if(sliceHeights[middle] <= y + EPSILON){
                activeIndex = middle;
                lower = middle + 1;
            } else {
                upper = middle - 1;
            }
        }
        return isPointInsidePolygon(slices.get(activeIndex), x, z);
    }

    public RegionShapeConfig getConfig(){
        return toConfig(slices);
    }

    public AreaDto.Point getMinPoint(){
        return AreaDto.Point.builder().x(minX).y(minY).z(minZ).build();
    }

    public AreaDto.Point getMaxPoint(){
        return AreaDto.Point.builder().x(maxX).y(maxY).z(maxZ).build();
    }

    public double getMinX(){
        return minX;
    }

    public double getMaxX(){
        return maxX;
    }

    public double getMinZ(){
        return minZ;
    }

    public double getMaxZ(){
        return maxZ;
    }

    private SliceData validateAndConvert(RegionShapeConfig.Slice slice){
        if(slice == null || !isFinite(slice.getY())
                || slice.getPolygon() == null || slice.getPolygon().size() < 3){
            throw new IllegalArgumentException("每个切片必须包含 y 和至少三个 Polygon 顶点");
        }

        List<Point2> points = new ArrayList<>();
        for(RegionShapeConfig.Point point : slice.getPolygon()){
            if(point == null || !isFinite(point.getX()) || !isFinite(point.getZ())){
                throw new IllegalArgumentException("Polygon 顶点坐标必须是有限数字");
            }
            points.add(new Point2(point.getX(), point.getZ()));
        }
        if(Math.abs(signedArea(points)) <= EPSILON){
            throw new IllegalArgumentException("Polygon 面积不能为 0");
        }
        if(hasSelfIntersection(points)){
            throw new IllegalArgumentException("Polygon 不能自相交");
        }
        return new SliceData(slice.getY(), points);
    }

    private boolean isPointInsidePolygon(SliceData slice, double x, double z){
        if(x < slice.minX - EPSILON || x > slice.maxX + EPSILON
                || z < slice.minZ - EPSILON || z > slice.maxZ + EPSILON){
            return false;
        }

        boolean inside = false;
        for(Edge edge : slice.edges){
            if(distanceToSegment(x, z, edge) <= EPSILON){
                return true;
            }

            boolean crosses = (edge.endZ > z) != (edge.startZ > z);
            if(crosses){
                double intersectionX = edge.startX
                        + (z - edge.startZ) * edge.deltaX / edge.deltaZ;
                if(x < intersectionX){
                    inside = !inside;
                }
            }
        }
        return inside;
    }

    private double distanceToSegment(double x, double z, Edge edge){
        if(edge.lengthSquared <= EPSILON){
            return Math.hypot(x - edge.startX, z - edge.startZ);
        }
        double factor = ((x - edge.startX) * edge.deltaX
                + (z - edge.startZ) * edge.deltaZ) / edge.lengthSquared;
        factor = Math.max(0.0, Math.min(1.0, factor));
        double projectedX = edge.startX + factor * edge.deltaX;
        double projectedZ = edge.startZ + factor * edge.deltaZ;
        return Math.hypot(x - projectedX, z - projectedZ);
    }

    private boolean hasSelfIntersection(List<Point2> polygon){
        int size = polygon.size();
        for(int first = 0; first < size; first++){
            int firstNext = (first + 1) % size;
            for(int second = first + 1; second < size; second++){
                int secondNext = (second + 1) % size;
                if(first == second || firstNext == second || secondNext == first){
                    continue;
                }
                if(segmentsIntersect(
                        polygon.get(first),
                        polygon.get(firstNext),
                        polygon.get(second),
                        polygon.get(secondNext)
                )){
                    return true;
                }
            }
        }
        return false;
    }

    private boolean segmentsIntersect(Point2 firstStart,
                                      Point2 firstEnd,
                                      Point2 secondStart,
                                      Point2 secondEnd){
        double first = cross(firstStart, firstEnd, secondStart);
        double second = cross(firstStart, firstEnd, secondEnd);
        double third = cross(secondStart, secondEnd, firstStart);
        double fourth = cross(secondStart, secondEnd, firstEnd);
        if(((first > EPSILON && second < -EPSILON) || (first < -EPSILON && second > EPSILON))
                && ((third > EPSILON && fourth < -EPSILON)
                || (third < -EPSILON && fourth > EPSILON))){
            return true;
        }
        return (Math.abs(first) <= EPSILON && onSegment(firstStart, firstEnd, secondStart))
                || (Math.abs(second) <= EPSILON && onSegment(firstStart, firstEnd, secondEnd))
                || (Math.abs(third) <= EPSILON && onSegment(secondStart, secondEnd, firstStart))
                || (Math.abs(fourth) <= EPSILON && onSegment(secondStart, secondEnd, firstEnd));
    }

    private boolean onSegment(Point2 start, Point2 end, Point2 point){
        return point.x >= Math.min(start.x, end.x) - EPSILON
                && point.x <= Math.max(start.x, end.x) + EPSILON
                && point.z >= Math.min(start.z, end.z) - EPSILON
                && point.z <= Math.max(start.z, end.z) + EPSILON;
    }

    private double cross(Point2 start, Point2 end, Point2 point){
        return (end.x - start.x) * (point.z - start.z)
                - (end.z - start.z) * (point.x - start.x);
    }

    private double signedArea(List<Point2> polygon){
        double result = 0.0;
        for(int index = 0; index < polygon.size(); index++){
            Point2 current = polygon.get(index);
            Point2 next = polygon.get((index + 1) % polygon.size());
            result += current.x * next.z - next.x * current.z;
        }
        return result / 2.0;
    }

    private RegionShapeConfig toConfig(List<SliceData> source){
        List<RegionShapeConfig.Slice> result = new ArrayList<>();
        for(SliceData slice : source){
            List<RegionShapeConfig.Point> polygon = new ArrayList<>();
            for(Point2 point : slice.points){
                polygon.add(RegionShapeConfig.Point.builder().x(point.x).z(point.z).build());
            }
            result.add(RegionShapeConfig.Slice.builder()
                    .y(slice.y)
                    .polygon(polygon)
                    .build());
        }
        return RegionShapeConfig.builder().slices(result).build();
    }

    private boolean isFinite(Double value){
        return value != null && !value.isNaN() && !value.isInfinite();
    }

    private static final class SliceData {
        private final double y;
        private final List<Point2> points;
        private final List<Edge> edges;
        private final double minX;
        private final double maxX;
        private final double minZ;
        private final double maxZ;

        private SliceData(double y, List<Point2> points) {
            this.y = y;
            this.points = points;
            this.edges = new ArrayList<>();
            double computedMinX = Double.POSITIVE_INFINITY;
            double computedMaxX = Double.NEGATIVE_INFINITY;
            double computedMinZ = Double.POSITIVE_INFINITY;
            double computedMaxZ = Double.NEGATIVE_INFINITY;
            for(int index = 0; index < points.size(); index++){
                Point2 start = points.get(index);
                Point2 end = points.get((index + 1) % points.size());
                this.edges.add(new Edge(start, end));
                computedMinX = Math.min(computedMinX, start.x);
                computedMaxX = Math.max(computedMaxX, start.x);
                computedMinZ = Math.min(computedMinZ, start.z);
                computedMaxZ = Math.max(computedMaxZ, start.z);
            }
            this.minX = computedMinX;
            this.maxX = computedMaxX;
            this.minZ = computedMinZ;
            this.maxZ = computedMaxZ;
        }
    }

    private static final class Edge {
        private final double startX;
        private final double startZ;
        private final double endZ;
        private final double deltaX;
        private final double deltaZ;
        private final double lengthSquared;

        private Edge(Point2 start, Point2 end) {
            this.startX = start.x;
            this.startZ = start.z;
            this.endZ = end.z;
            this.deltaX = end.x - start.x;
            this.deltaZ = end.z - start.z;
            this.lengthSquared = deltaX * deltaX + deltaZ * deltaZ;
        }
    }

    private static final class Point2 {
        private final double x;
        private final double z;

        private Point2(double x, double z) {
            this.x = x;
            this.z = z;
        }
    }
}
