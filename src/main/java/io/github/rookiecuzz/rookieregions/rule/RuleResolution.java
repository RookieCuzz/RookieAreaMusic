package io.github.rookiecuzz.rookieregions.rule;

import java.util.List;
import java.util.Optional;

public final class RuleResolution<T> {
    private final Flag<T> flag;
    private final ResolutionStatus status;
    private final T value;
    private final List<RuleContribution<T>> contributions;

    RuleResolution(Flag<T> flag,
                   ResolutionStatus status,
                   T value,
                   List<RuleContribution<T>> contributions) {
        this.flag = flag;
        this.status = status;
        this.value = value;
        this.contributions = List.copyOf(contributions);
    }

    public Flag<T> flag() {
        return flag;
    }

    public ResolutionStatus status() {
        return status;
    }

    public Optional<T> value() {
        return Optional.ofNullable(value);
    }

    public List<RuleContribution<T>> contributions() {
        return contributions;
    }
}
