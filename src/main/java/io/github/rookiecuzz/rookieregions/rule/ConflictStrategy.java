package io.github.rookiecuzz.rookieregions.rule;

import java.util.List;
import java.util.Optional;

@FunctionalInterface
public interface ConflictStrategy<T> {
    /** Empty means the conflicting values are intentionally unresolved. */
    Optional<T> resolve(List<T> distinctValues);
}
