package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.ShapeRelation;

import java.util.Objects;

public record RegionRelation(Region region, ShapeRelation relation) {
    public RegionRelation {
        Objects.requireNonNull(region, "related region cannot be null");
        Objects.requireNonNull(relation, "shape relation cannot be null");
    }
}
