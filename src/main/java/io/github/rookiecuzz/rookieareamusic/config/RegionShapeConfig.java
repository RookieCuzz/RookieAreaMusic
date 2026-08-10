package io.github.rookiecuzz.rookieareamusic.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionShapeConfig {
    @Builder.Default
    private String type = "sliced_polygon";
    @Builder.Default
    private List<Slice> slices = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Slice {
        private Double y;
        @Builder.Default
        private List<Point> polygon = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        private Double x;
        private Double z;
    }
}
