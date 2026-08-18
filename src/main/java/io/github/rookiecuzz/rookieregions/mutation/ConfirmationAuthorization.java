package io.github.rookiecuzz.rookieregions.mutation;

/** Complete server-side authorization recovered from a valid opaque token. */
public record ConfirmationAuthorization(String placementPlanFingerprint,
                                        long snapshotRevision,
                                        PlacementOption option) {
    public ConfirmationAuthorization {
        if(placementPlanFingerprint == null
                || placementPlanFingerprint.trim().isEmpty()){
            throw new IllegalArgumentException(
                    "placement plan fingerprint must not be blank"
            );
        }
        if(snapshotRevision < 0L || option == null){
            throw new IllegalArgumentException("invalid confirmation authorization");
        }
    }
}
