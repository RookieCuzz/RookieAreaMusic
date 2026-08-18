package io.github.rookiecuzz.rookieregions.rule;

import io.github.rookiecuzz.rookieregions.core.Region;

/** Context supplied when no local or global value was configured. */
public record DefaultContext(
        Region leaf,
        Region global,
        Subject subject,
        Association association
) {
    public boolean isWilderness() {
        return leaf == null;
    }
}
