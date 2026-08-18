package io.github.rookiecuzz.rookieregions.rule;

import java.util.Optional;

@FunctionalInterface
public interface FlagDefault<T> {
    Optional<T> value(DefaultContext context);

    static <T> FlagDefault<T> unset() {
        return ignored -> Optional.empty();
    }

    static <T> FlagDefault<T> constant(T value) {
        return ignored -> Optional.of(value);
    }
}
