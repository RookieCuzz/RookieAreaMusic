package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.RegionRelation;

import java.util.List;
import java.util.Optional;

/** Immutable, fingerprinted result of one placement-policy evaluation. */
public final class PlacementPlan {
    private final PlanDisposition disposition;
    private final List<PlacementOption> options;
    private final RegionSaveRejection rejection;
    private final String message;
    private final List<RegionRelation> relevantRelations;
    private final String fingerprint;

    private PlacementPlan(PlanDisposition disposition,
                          List<PlacementOption> options,
                          RegionSaveRejection rejection,
                          String message,
                          List<RegionRelation> relevantRelations) {
        this.disposition = disposition;
        this.options = List.copyOf(options);
        this.rejection = rejection;
        this.message = message == null ? "" : message;
        this.relevantRelations = List.copyOf(relevantRelations);
        this.fingerprint = RegionFingerprints.placementPlan(
                disposition,
                this.options,
                rejection,
                this.relevantRelations
        );
    }

    public static PlacementPlan direct(){
        return new PlacementPlan(
                PlanDisposition.DIRECT,
                List.of(PlacementOption.direct()),
                null,
                "",
                List.of()
        );
    }

    public static PlacementPlan confirmation(List<PlacementOption> options,
                                             List<RegionRelation> relations){
        if(options == null || options.isEmpty()){
            throw new IllegalArgumentException(
                    "confirmation plan requires at least one option"
            );
        }
        return new PlacementPlan(
                PlanDisposition.CONFIRMATION_REQUIRED,
                options,
                null,
                "",
                relations == null ? List.of() : relations
        );
    }

    public static PlacementPlan rejected(RegionSaveRejection rejection,
                                         String message,
                                         List<RegionRelation> relations){
        if(rejection == null){
            throw new IllegalArgumentException("rejection cannot be null");
        }
        return new PlacementPlan(
                PlanDisposition.REJECTED,
                List.of(),
                rejection,
                message,
                relations == null ? List.of() : relations
        );
    }

    public PlanDisposition disposition() {
        return disposition;
    }

    public List<PlacementOption> options() {
        return options;
    }

    public Optional<RegionSaveRejection> rejection() {
        return Optional.ofNullable(rejection);
    }

    public String message() {
        return message;
    }

    public List<RegionRelation> relevantRelations() {
        return relevantRelations;
    }

    public String fingerprint() {
        return fingerprint;
    }
}
