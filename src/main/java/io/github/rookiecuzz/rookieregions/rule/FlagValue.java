package io.github.rookiecuzz.rookieregions.rule;

import java.util.Objects;

public record FlagValue<T>(Flag<T> flag, T value) {
    public FlagValue {
        Objects.requireNonNull(flag, "flag cannot be null");
        Objects.requireNonNull(value, "flag value cannot be null");
    }
}
