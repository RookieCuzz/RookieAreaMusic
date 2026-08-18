package io.github.rookiecuzz.rookieregions.core;

import java.util.List;

public final class RegionGraphValidationException extends IllegalArgumentException {
    public enum Reason {
        DUPLICATE_KEY,
        MISSING_GLOBAL,
        INVALID_GLOBAL,
        MISSING_PARENT,
        CROSS_WORLD_PARENT,
        SELF_PARENT,
        CYCLE,
        NOT_INSIDE_PARENT
    }

    private final Reason reason;
    private final List<RegionKey> path;

    RegionGraphValidationException(Reason reason,
                                   String message,
                                   List<RegionKey> path) {
        super(message);
        this.reason = reason;
        this.path = path == null ? List.of() : List.copyOf(path);
    }

    public Reason reason() {
        return reason;
    }

    public List<RegionKey> path() {
        return path;
    }
}
