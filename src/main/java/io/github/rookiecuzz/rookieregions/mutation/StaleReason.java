package io.github.rookiecuzz.rookieregions.mutation;

public enum StaleReason {
    SNAPSHOT_REVISION_CHANGED,
    TARGET_CHANGED,
    PLACEMENT_CHANGED,
    COMMIT_RACE
}
