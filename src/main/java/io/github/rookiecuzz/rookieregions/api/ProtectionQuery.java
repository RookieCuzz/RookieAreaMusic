package io.github.rookiecuzz.rookieregions.api;

import io.github.rookiecuzz.rookieregions.core.ApplicableRegionSet;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.rule.BuildAction;
import io.github.rookiecuzz.rookieregions.rule.Flag;
import io.github.rookiecuzz.rookieregions.rule.ProtectionRuleSet;
import io.github.rookiecuzz.rookieregions.rule.RuleResolution;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;

import java.util.Objects;
import java.util.Optional;

/** Pure, thread-safe protection decisions pinned to one RegionQuery. */
public final class ProtectionQuery {
    private final RegionQuery query;

    public ProtectionQuery(RegionQuery query) {
        this.query = Objects.requireNonNull(query, "region query cannot be null");
    }

    public RegionQuery query() {
        return query;
    }

    public ProtectionDecision decide(WorldId world,
                                     double x,
                                     double y,
                                     double z,
                                     Flag<State> flag,
                                     Subject subject,
                                     String bypassSuffix) {
        Objects.requireNonNull(flag, "protection flag cannot be null");
        Subject actor = subject == null ? Subject.none() : subject;
        if(canBypass(actor, bypassSuffix)) {
            return new ProtectionDecision(true, true, Optional.empty());
        }
        RuleResolution<State> resolution = query.resolve(
                world, x, y, z, flag, actor
        );
        return decision(resolution);
    }

    public ProtectionDecision decideBuild(WorldId world,
                                          double x,
                                          double y,
                                          double z,
                                          Subject subject,
                                          BuildAction action) {
        Objects.requireNonNull(action, "build action cannot be null");
        Subject actor = subject == null ? Subject.none() : subject;
        String specific = action == BuildAction.BREAK
                ? "block-break"
                : "block-place";
        if(canBypass(actor, specific) || canBypass(actor, "build")) {
            return new ProtectionDecision(true, true, Optional.empty());
        }
        ApplicableRegionSet regions = query.at(world, x, y, z);
        return decision(ProtectionRuleSet.resolveBuildAction(
                regions, actor, action
        ));
    }

    public ProtectionDecision decideContainer(WorldId world,
                                              double x,
                                              double y,
                                              double z,
                                              Subject subject) {
        Subject actor = subject == null ? Subject.none() : subject;
        if(canBypass(actor, "container")) {
            return new ProtectionDecision(true, true, Optional.empty());
        }
        return decision(ProtectionRuleSet.resolveContainer(
                query.at(world, x, y, z), actor
        ));
    }

    private static ProtectionDecision decision(RuleResolution<State> resolution) {
        return new ProtectionDecision(
                resolution.value().orElse(State.ALLOW) == State.ALLOW,
                false,
                Optional.of(resolution)
        );
    }

    private static boolean canBypass(Subject subject, String suffix) {
        if(suffix == null || suffix.trim().isEmpty()) {
            return false;
        }
        return subject.hasPermission("rookieregions.admin")
                || subject.hasPermission(
                        "rookieregions.bypass." + suffix.trim().toLowerCase(
                                java.util.Locale.ROOT
                        )
                );
    }
}
