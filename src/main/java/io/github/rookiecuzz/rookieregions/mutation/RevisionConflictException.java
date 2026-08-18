package io.github.rookiecuzz.rookieregions.mutation;

/** Atomic mutation port rejected an obsolete expected revision. */
public final class RevisionConflictException extends Exception {
    public RevisionConflictException(String message) {
        super(message);
    }
}
