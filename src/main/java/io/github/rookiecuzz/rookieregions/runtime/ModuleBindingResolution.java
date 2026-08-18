package io.github.rookiecuzz.rookieregions.runtime;

import java.util.List;

/** Immutable point resolution plus non-fatal external-provider diagnostics. */
public record ModuleBindingResolution(List<BoundModuleRegion> regions,
                                      List<ModuleBindingIssue> issues) {
    public ModuleBindingResolution {
        regions = regions == null ? List.of() : List.copyOf(regions);
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
