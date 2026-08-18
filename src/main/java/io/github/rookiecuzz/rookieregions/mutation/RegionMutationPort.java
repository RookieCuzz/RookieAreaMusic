package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.util.Collection;

/** External persistence/publication port; implementations must commit by CAS. */
public interface RegionMutationPort {
    RegionSnapshot currentSnapshot();

    /**
     * Atomically persists and publishes the complete candidate record set if
     * the current revision still equals {@code expectedRevision}. Unchanged
     * module attachments must be published exactly as supplied.
     */
    RegionSnapshot commit(long expectedRevision,
                          Collection<RegionRecord> candidateRecords)
            throws RevisionConflictException, Exception;

    /**
     * Publishes a completely staged reload without rewriting its documents.
     * Implementations must serialize this operation with ordinary commits.
     */
    default RegionSnapshot publishStaged(
            long expectedRevision,
            Collection<RegionRecord> stagedRecords) throws Exception {
        throw new UnsupportedOperationException(
                "this mutation port does not support staged publication"
        );
    }
}
