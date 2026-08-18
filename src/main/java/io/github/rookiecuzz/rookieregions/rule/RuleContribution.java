package io.github.rookiecuzz.rookieregions.rule;

import io.github.rookiecuzz.rookieregions.core.RegionKey;

/** Provenance for one winning-priority branch. */
public record RuleContribution<T>(
        RegionKey leaf,
        RegionKey source,
        T value,
        ValueOrigin origin,
        Association association,
        int priority
) {
}
