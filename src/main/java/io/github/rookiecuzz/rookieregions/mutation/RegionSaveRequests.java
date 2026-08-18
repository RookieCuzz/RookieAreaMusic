package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Safe builders for third-party create and edit transactions. */
public final class RegionSaveRequests {
    public static Builder create(RegionSnapshot snapshot, Region candidate) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        return new Builder(
                SaveMode.CREATE,
                candidate,
                snapshot.revision(),
                Optional.empty()
        );
    }

    public static Builder edit(RegionSnapshot snapshot,
                               RegionKey target,
                               Region candidate) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(target, "edit target cannot be null");
        Region current = snapshot.graph().region(target).orElseThrow(() ->
                new IllegalArgumentException("edit target does not exist: " + target)
        );
        return edit(snapshot, current, candidate);
    }

    public static Builder edit(RegionSnapshot snapshot,
                               Region current,
                               Region candidate) {
        Objects.requireNonNull(snapshot, "snapshot cannot be null");
        Objects.requireNonNull(current, "current region cannot be null");
        if(snapshot.graph().region(current.key()).orElse(null) != current) {
            throw new IllegalArgumentException(
                    "current region is not from the supplied snapshot"
            );
        }
        return new Builder(
                SaveMode.EDIT,
                candidate,
                snapshot.revision(),
                Optional.of(RegionFingerprints.region(current))
        );
    }

    public static final class Builder {
        private final SaveMode mode;
        private final Region candidate;
        private final long revision;
        private final Optional<String> targetFingerprint;
        private String sessionId = "api-" + UUID.randomUUID();
        private Optional<String> confirmationToken = Optional.empty();

        private Builder(SaveMode mode,
                        Region candidate,
                        long revision,
                        Optional<String> targetFingerprint) {
            this.mode = mode;
            this.candidate = Objects.requireNonNull(
                    candidate, "candidate region cannot be null"
            );
            this.revision = revision;
            this.targetFingerprint = targetFingerprint;
        }

        public Builder sessionId(String requested) {
            if(requested == null || requested.trim().isEmpty()) {
                throw new IllegalArgumentException("session ID cannot be blank");
            }
            sessionId = requested.trim();
            return this;
        }

        public Builder confirmationToken(String requested) {
            if(requested == null || requested.trim().isEmpty()) {
                throw new IllegalArgumentException(
                        "confirmation token cannot be blank"
                );
            }
            confirmationToken = Optional.of(requested.trim());
            return this;
        }

        public RegionSaveRequest build() {
            return new RegionSaveRequest(
                    sessionId,
                    mode,
                    candidate,
                    revision,
                    targetFingerprint,
                    confirmationToken
            );
        }
    }

    private RegionSaveRequests() {
    }
}
