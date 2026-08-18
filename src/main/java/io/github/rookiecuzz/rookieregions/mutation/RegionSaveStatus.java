package io.github.rookiecuzz.rookieregions.mutation;

/** Stable public status vocabulary for every save attempt. */
public enum RegionSaveStatus {
    SAVED,
    CONFIRMATION_REQUIRED,
    DENIED,
    STALE,
    STORAGE_FAILURE
}
