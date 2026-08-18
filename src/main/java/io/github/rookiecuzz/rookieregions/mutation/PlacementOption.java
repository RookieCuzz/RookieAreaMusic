package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.RegionKey;

import java.util.Optional;

/** One exact action that can be authorized by a confirmation token. */
public record PlacementOption(SaveChoice choice, Optional<RegionKey> parent) {
    public PlacementOption {
        if(choice == null){
            throw new IllegalArgumentException("save choice cannot be null");
        }
        parent = parent == null ? Optional.empty() : parent;
        if(choice == SaveChoice.SET_PARENT && parent.isEmpty()){
            throw new IllegalArgumentException("SET_PARENT requires a parent key");
        }
        if(choice != SaveChoice.SET_PARENT && parent.isPresent()){
            throw new IllegalArgumentException(
                    choice + " must not include a parent key"
            );
        }
    }

    public static PlacementOption direct(){
        return new PlacementOption(SaveChoice.DIRECT, Optional.empty());
    }

    public static PlacementOption keepOverlap(){
        return new PlacementOption(SaveChoice.KEEP_OVERLAP, Optional.empty());
    }

    public static PlacementOption setParent(RegionKey parent){
        return new PlacementOption(SaveChoice.SET_PARENT, Optional.of(parent));
    }
}
