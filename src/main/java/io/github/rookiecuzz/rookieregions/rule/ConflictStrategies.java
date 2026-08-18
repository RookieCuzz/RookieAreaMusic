package io.github.rookiecuzz.rookieregions.rule;

import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

public final class ConflictStrategies {
    public static <T> ConflictStrategy<T> reject() {
        return ignored -> Optional.empty();
    }

    public static ConflictStrategy<State> denyWins() {
        return values -> Optional.of(
                values.contains(State.DENY) ? State.DENY : State.ALLOW
        );
    }

    public static <E> ConflictStrategy<Set<E>> union() {
        return values -> {
            LinkedHashSet<E> merged = new LinkedHashSet<>();
            for(Set<E> value : values){
                merged.addAll(value);
            }
            return Optional.of(Set.copyOf(merged));
        };
    }

    private ConflictStrategies() {
    }
}
