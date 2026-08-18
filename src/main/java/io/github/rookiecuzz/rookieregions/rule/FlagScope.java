package io.github.rookiecuzz.rookieregions.rule;

import io.github.rookiecuzz.rookieregions.core.Region;

public enum FlagScope {
    ANY_REGION,
    GLOBAL_REGION,
    LOCAL_REGION;

    public boolean accepts(Region region) {
        return switch(this){
            case ANY_REGION -> true;
            case GLOBAL_REGION -> region.key().isGlobal();
            case LOCAL_REGION -> !region.key().isGlobal();
        };
    }
}
