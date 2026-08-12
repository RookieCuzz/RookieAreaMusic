package io.github.rookiecuzz.rookieareamusic.geometry;

import io.github.rookiecuzz.rookieareamusic.config.RegionShapeConfig;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Builds and samples the complete wireframe of a sliced-polygon region.
 *
 * <p>Each slice is an independent vertical prism. Slice {@code i} starts at
 * its configured Y and ends at the next slice's Y. The last slice ends at
 * {@code lastY + 1}, matching {@link SlicedPolygonVolume#contains(double,
 * double, double)}. Polygons on adjacent slices are deliberately never
 * connected vertex-to-vertex because their vertex counts and shapes are
 * independent.</p>
 *
 * <p>This class has no Bukkit dependency. Callers can turn the returned
 * samples into particles, while tests can verify the preview geometry without
 * starting a server.</p>
 */
public final class RegionPreviewWireframe {
    private static final double TARGET_SAMPLE_SPACING = 0.5;
    private static final int MIN_STYLE_RESERVE = 24;

    /** Visual roles that a particle renderer can map to different colours. */
    public enum Style {
        /** The inclusive lower outline of every configured slice. */
        BASE,
        /** The outgoing outline immediately below a transition height. */
        TRANSITION_TOP,
        /** Vertical edges showing how far each slice remains active. */
        VERTICAL,
        /** The exclusive top outline at {@code lastSliceY + 1}. */
        FINAL_TOP
    }

    /** One immutable point selected from the wireframe. */
    public static final class Sample {
        private final double x;
        private final double y;
        private final double z;
        private final Style style;

        private Sample(double x, double y, double z, Style style) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.style = style;
        }

        public double getX(){
            return x;
        }

        public double getY(){
            return y;
        }

        public double getZ(){
            return z;
        }

        public Style getStyle(){
            return style;
        }
    }

    private final EnumMap<Style, SegmentGroup> groups;
    private final double minY;
    private final double maxExclusiveY;
    private final int sliceCount;

    private RegionPreviewWireframe(EnumMap<Style, SegmentGroup> groups,
                                   double minY,
                                   double maxExclusiveY,
                                   int sliceCount) {
        this.groups = groups;
        this.minY = minY;
        this.maxExclusiveY = maxExclusiveY;
        this.sliceCount = sliceCount;
    }

    /**
     * Validates the supplied shape with the production volume validator and
     * creates its complete, normalized wireframe.
     *
     * @param source sliced-polygon configuration
     * @return immutable wireframe
     * @throws IllegalArgumentException when {@code source} is not a valid
     *                                  sliced-polygon shape
     */
    public static RegionPreviewWireframe from(RegionShapeConfig source){
        RegionShapeConfig normalized = new SlicedPolygonVolume(source).getConfig();
        List<RegionShapeConfig.Slice> slices = normalized.getSlices();

        EnumMap<Style, List<Segment>> segments = new EnumMap<>(Style.class);
        for(Style style : Style.values()){
            segments.put(style, new ArrayList<Segment>());
        }

        for(int index = 0; index < slices.size(); index++){
            RegionShapeConfig.Slice slice = slices.get(index);
            double bottomY = slice.getY();
            boolean last = index == slices.size() - 1;
            double topY = last ? bottomY + 1.0 : slices.get(index + 1).getY();

            addPolygon(segments.get(Style.BASE), slice.getPolygon(), bottomY);
            addVerticals(segments.get(Style.VERTICAL), slice.getPolygon(), bottomY, topY);
            addPolygon(
                    segments.get(last ? Style.FINAL_TOP : Style.TRANSITION_TOP),
                    slice.getPolygon(),
                    topY
            );
        }

        EnumMap<Style, SegmentGroup> groups = new EnumMap<>(Style.class);
        for(Map.Entry<Style, List<Segment>> entry : segments.entrySet()){
            SegmentGroup group = SegmentGroup.create(entry.getValue());
            if(group != null){
                groups.put(entry.getKey(), group);
            }
        }

        double firstY = slices.get(0).getY();
        double lastY = slices.get(slices.size() - 1).getY();
        return new RegionPreviewWireframe(groups, firstY, lastY + 1.0, slices.size());
    }

    /**
     * Samples all visual roles without exceeding {@code maxSamples}.
     *
     * <p>Every non-empty style receives a sample before remaining capacity is
     * distributed by line length. Up to 24 samples per style are reserved
     * first, preventing a very tall or wide part of the ROI from consuming the
     * complete particle budget. Inside each style, samples are evenly spaced
     * over the concatenated three-dimensional line length, so later slices are
     * not starved merely because their segments were appended later.</p>
     *
     * <p>{@code phase} is a cyclic value. A renderer should vary it between
     * refreshes (for example by adding the golden ratio) so sub-spacing edges
     * in extremely large regions take turns receiving samples.</p>
     *
     * @param maxSamples hard upper bound for returned points
     * @param phase cyclic sampling offset; non-finite values are treated as 0
     * @return immutable list containing at most {@code maxSamples} points
     */
    public List<Sample> sample(int maxSamples, double phase){
        if(maxSamples <= 0 || groups.isEmpty()){
            return Collections.emptyList();
        }

        double normalizedPhase = normalizePhase(phase);
        EnumMap<Style, Integer> allocations = allocate(maxSamples, normalizedPhase);
        List<Sample> result = new ArrayList<>();
        for(Style style : Style.values()){
            Integer count = allocations.get(style);
            if(count == null || count <= 0){
                continue;
            }
            groups.get(style).sample(style, count, normalizedPhase, result);
        }
        return Collections.unmodifiableList(result);
    }

    public double getMinY(){
        return minY;
    }

    public double getMaxExclusiveY(){
        return maxExclusiveY;
    }

    public int getSliceCount(){
        return sliceCount;
    }

    private EnumMap<Style, Integer> allocate(int maxSamples, double phase){
        List<Style> active = new ArrayList<>();
        long totalDesired = 0L;
        for(Style style : Style.values()){
            SegmentGroup group = groups.get(style);
            if(group != null && group.desiredSamples > 0){
                active.add(style);
                totalDesired = Math.min(
                        Integer.MAX_VALUE,
                        totalDesired + (long) group.desiredSamples
                );
            }
        }

        int target = (int) Math.min((long) maxSamples, totalDesired);
        EnumMap<Style, Integer> result = new EnumMap<>(Style.class);
        if(target <= 0 || active.isEmpty()){
            return result;
        }

        int rotation = (int) Math.floor(phase * active.size());
        if(target < active.size()){
            for(int index = 0; index < target; index++){
                result.put(active.get((rotation + index) % active.size()), 1);
            }
            return result;
        }

        for(Style style : active){
            result.put(style, 1);
        }
        int remaining = target - active.size();

        // Fill the per-style reserve in round-robin order. The phase-based
        // rotation removes a permanent first-enum advantage at tiny budgets.
        boolean reserveAvailable = true;
        while(remaining > 0 && reserveAvailable){
            reserveAvailable = false;
            for(int offset = 0; offset < active.size() && remaining > 0; offset++){
                Style style = active.get((rotation + offset) % active.size());
                int current = result.get(style);
                int reserve = Math.min(
                        MIN_STYLE_RESERVE,
                        groups.get(style).desiredSamples
                );
                if(current < reserve){
                    result.put(style, current + 1);
                    remaining--;
                    reserveAvailable = true;
                }
            }
        }

        // Distribute the rest proportionally by actual 3-D line length. Groups
        // that reach their useful 0.5-block density are removed and the quota
        // is recalculated for the remaining groups.
        while(remaining > 0){
            double totalWeight = 0.0;
            for(Style style : active){
                if(result.get(style) < groups.get(style).desiredSamples){
                    totalWeight += groups.get(style).totalLength;
                }
            }
            if(totalWeight <= 0.0 || !Double.isFinite(totalWeight)){
                break;
            }

            int roundBudget = remaining;
            int granted = 0;
            for(Style style : active){
                SegmentGroup group = groups.get(style);
                int current = result.get(style);
                int capacity = group.desiredSamples - current;
                if(capacity <= 0){
                    continue;
                }
                int share = (int) Math.floor(
                        roundBudget * (group.totalLength / totalWeight)
                );
                share = Math.min(capacity, share);
                if(share > 0){
                    result.put(style, current + share);
                    granted += share;
                }
            }
            remaining -= granted;
            if(remaining <= 0){
                break;
            }

            // At most one rounding remainder per active style is normally left.
            // Select the most under-sampled line length, rotating exact ties.
            Style selected = null;
            double bestScore = Double.NEGATIVE_INFINITY;
            for(int offset = 0; offset < active.size(); offset++){
                Style style = active.get((rotation + offset) % active.size());
                SegmentGroup group = groups.get(style);
                int current = result.get(style);
                if(current >= group.desiredSamples){
                    continue;
                }
                double score = group.totalLength / (current + 1.0);
                if(score > bestScore){
                    bestScore = score;
                    selected = style;
                }
            }
            if(selected == null){
                break;
            }
            result.put(selected, result.get(selected) + 1);
            remaining--;
        }
        return result;
    }

    private static void addPolygon(List<Segment> target,
                                   List<RegionShapeConfig.Point> polygon,
                                   double y){
        for(int index = 0; index < polygon.size(); index++){
            RegionShapeConfig.Point first = polygon.get(index);
            RegionShapeConfig.Point second = polygon.get((index + 1) % polygon.size());
            addSegment(target, new Segment(
                    first.getX(), y, first.getZ(),
                    second.getX(), y, second.getZ()
            ));
        }
    }

    private static void addVerticals(List<Segment> target,
                                     List<RegionShapeConfig.Point> polygon,
                                     double bottomY,
                                     double topY){
        for(RegionShapeConfig.Point point : polygon){
            addSegment(target, new Segment(
                    point.getX(), bottomY, point.getZ(),
                    point.getX(), topY, point.getZ()
            ));
        }
    }

    private static void addSegment(List<Segment> target, Segment segment){
        if(segment.length > 0.0){
            target.add(segment);
        }
    }

    private static double normalizePhase(double phase){
        if(!Double.isFinite(phase)){
            return 0.0;
        }
        return phase - Math.floor(phase);
    }

    private static final class SegmentGroup {
        private final List<Segment> segments;
        private final double[] cumulativeEnds;
        private final double totalLength;
        private final int desiredSamples;

        private SegmentGroup(List<Segment> segments,
                             double[] cumulativeEnds,
                             double totalLength,
                             int desiredSamples) {
            this.segments = segments;
            this.cumulativeEnds = cumulativeEnds;
            this.totalLength = totalLength;
            this.desiredSamples = desiredSamples;
        }

        private static SegmentGroup create(List<Segment> source){
            if(source == null || source.isEmpty()){
                return null;
            }
            List<Segment> copy = Collections.unmodifiableList(
                    new ArrayList<Segment>(source)
            );
            double[] cumulativeEnds = new double[copy.size()];
            double totalLength = 0.0;
            for(int index = 0; index < copy.size(); index++){
                totalLength += copy.get(index).length;
                if(!Double.isFinite(totalLength)){
                    throw new IllegalArgumentException(
                            "ROI wireframe total line length is too large"
                    );
                }
                cumulativeEnds[index] = totalLength;
            }
            if(totalLength <= 0.0){
                return null;
            }
            double rawDesired = Math.ceil(totalLength / TARGET_SAMPLE_SPACING);
            int desired = rawDesired >= Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : Math.max(1, (int) rawDesired);
            return new SegmentGroup(copy, cumulativeEnds, totalLength, desired);
        }

        private void sample(Style style,
                            int count,
                            double phase,
                            List<Sample> target){
            for(int index = 0; index < count; index++){
                double distance = ((index + phase) / count) * totalLength;
                if(distance >= totalLength){
                    distance = Math.nextDown(totalLength);
                }
                int segmentIndex = findSegment(distance);
                double segmentStart = segmentIndex == 0
                        ? 0.0
                        : cumulativeEnds[segmentIndex - 1];
                Segment segment = segments.get(segmentIndex);
                double progress = (distance - segmentStart) / segment.length;
                progress = Math.max(0.0, Math.min(1.0, progress));
                target.add(new Sample(
                        segment.startX + segment.deltaX * progress,
                        segment.startY + segment.deltaY * progress,
                        segment.startZ + segment.deltaZ * progress,
                        style
                ));
            }
        }

        private int findSegment(double distance){
            int lower = 0;
            int upper = cumulativeEnds.length - 1;
            while(lower < upper){
                int middle = (lower + upper) >>> 1;
                if(distance < cumulativeEnds[middle]){
                    upper = middle;
                } else {
                    lower = middle + 1;
                }
            }
            return lower;
        }
    }

    private static final class Segment {
        private final double startX;
        private final double startY;
        private final double startZ;
        private final double deltaX;
        private final double deltaY;
        private final double deltaZ;
        private final double length;

        private Segment(double startX,
                        double startY,
                        double startZ,
                        double endX,
                        double endY,
                        double endZ) {
            this.startX = startX;
            this.startY = startY;
            this.startZ = startZ;
            this.deltaX = endX - startX;
            this.deltaY = endY - startY;
            this.deltaZ = endZ - startZ;
            this.length = Math.hypot(Math.hypot(deltaX, deltaY), deltaZ);
            if(!Double.isFinite(length)){
                throw new IllegalArgumentException(
                        "ROI wireframe segment length is too large"
                );
            }
        }
    }
}
