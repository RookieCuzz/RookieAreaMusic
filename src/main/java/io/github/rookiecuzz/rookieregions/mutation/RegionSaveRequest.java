package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;

import java.util.Objects;
import java.util.Optional;

/** One editor save attempt. Optional values are normalized, never null. */
public record RegionSaveRequest(String sessionId,
                                SaveMode mode,
                                Region candidate,
                                long expectedSnapshotRevision,
                                Optional<String> expectedTargetFingerprint,
                                Optional<String> confirmationToken) {
    public RegionSaveRequest {
        if(sessionId == null || sessionId.trim().isEmpty()){
            throw new IllegalArgumentException("save session ID must not be blank");
        }
        sessionId = sessionId.trim();
        Objects.requireNonNull(mode, "save mode cannot be null");
        if(mode == SaveMode.DELETE){
            throw new IllegalArgumentException(
                    "DELETE is a publication mode, not an editor save mode"
            );
        }
        Objects.requireNonNull(candidate, "save candidate cannot be null");
        if(expectedSnapshotRevision < 0L){
            throw new IllegalArgumentException(
                    "expected snapshot revision cannot be negative"
            );
        }
        expectedTargetFingerprint = normalizeOptional(
                expectedTargetFingerprint, "target fingerprint"
        );
        confirmationToken = normalizeOptional(
                confirmationToken, "confirmation token"
        );
        if(mode == SaveMode.CREATE && expectedTargetFingerprint.isPresent()){
            throw new IllegalArgumentException(
                    "CREATE must not provide an expected target fingerprint"
            );
        }
        if(mode == SaveMode.EDIT && expectedTargetFingerprint.isEmpty()){
            throw new IllegalArgumentException(
                    "EDIT requires an expected target fingerprint"
            );
        }
    }

    private static Optional<String> normalizeOptional(Optional<String> value,
                                                      String label){
        if(value == null || value.isEmpty()){
            return Optional.empty();
        }
        String normalized = value.orElseThrow().trim();
        if(normalized.isEmpty()){
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return Optional.of(normalized);
    }
}
