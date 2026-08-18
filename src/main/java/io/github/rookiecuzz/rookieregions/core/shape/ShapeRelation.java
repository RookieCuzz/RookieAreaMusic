package io.github.rookiecuzz.rookieregions.core.shape;

/** Directional topological relation of one non-empty 3-D shape to another. */
public enum ShapeRelation {
    DISJOINT,
    TOUCHING,
    INSIDE,
    CONTAINS,
    EQUAL,
    OVERLAP;

    public ShapeRelation inverse(){
        return switch(this){
            case INSIDE -> CONTAINS;
            case CONTAINS -> INSIDE;
            default -> this;
        };
    }

    public boolean hasPositiveVolumeIntersection(){
        return switch(this){
            case INSIDE, CONTAINS, EQUAL, OVERLAP -> true;
            case DISJOINT, TOUCHING -> false;
        };
    }
}
