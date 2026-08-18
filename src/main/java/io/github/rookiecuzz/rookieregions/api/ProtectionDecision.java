package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.State;

import java.util.Optional;

/** Final protection result, including whether a permission bypass was used. */
public record ProtectionDecision(boolean allowed,
                                 boolean bypassed,
                                 Optional<RuleResolution<State>> resolution) {
    public ProtectionDecision {
        resolution = resolution == null ? Optional.empty() : resolution;
        if(bypassed && !allowed) {
            throw new IllegalArgumentException("a bypassed decision must be allowed");
        }
    }
}
