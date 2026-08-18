package io.github.rookiecuzz.rookieregions.rule;

import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;

import java.util.Objects;

/** Cross-flag fallback rules used by the Paper protection listeners. */
public final class ProtectionRuleSet {
    public static RuleResolution<State> resolveBuildAction(
            ApplicableRegionSet regions,
            Subject subject,
            BuildAction action) {
        Objects.requireNonNull(regions, "applicable regions cannot be null");
        Objects.requireNonNull(action, "build action cannot be null");
        Flag<State> specific = action == BuildAction.BREAK
                ? ProtectionFlags.BLOCK_BREAK
                : ProtectionFlags.BLOCK_PLACE;
        return regions.resolveWithFallback(
                specific, ProtectionFlags.BUILD, subject
        );
    }

    /** Resolves container first, then falls back to the general use flag. */
    public static RuleResolution<State> resolveContainer(
            ApplicableRegionSet regions,
            Subject subject) {
        Objects.requireNonNull(regions, "applicable regions cannot be null");
        return regions.resolveWithFallback(
                ProtectionFlags.CONTAINER,
                ProtectionFlags.USE,
                subject
        );
    }

    private ProtectionRuleSet() {
    }
}
