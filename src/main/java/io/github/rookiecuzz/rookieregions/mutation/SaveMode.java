package io.github.rookiecuzz.rookieregions.mutation;

public enum SaveMode {
    CREATE,
    EDIT,
    /** Publication-only mode for a committed region removal. */
    DELETE
}
