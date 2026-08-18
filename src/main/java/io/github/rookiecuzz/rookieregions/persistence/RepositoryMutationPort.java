package io.github.rookiecuzz.rookieregions.persistence;

import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationPort;
import io.github.rookiecuzz.rookieregions.mutation.RevisionConflictException;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Serializes one-record filesystem commits with snapshot publication. */
public final class RepositoryMutationPort implements RegionMutationPort {
    private final RegionRepository repository;
    private final RegionContainer container;

    public RepositoryMutationPort(RegionRepository repository,
                                  RegionContainer container) {
        this.repository = repository;
        this.container = container;
    }

    @Override
    public RegionSnapshot currentSnapshot() {
        return container.snapshot();
    }

    @Override
    public synchronized RegionSnapshot commit(
            long expectedRevision,
            Collection<RegionRecord> candidateRecords) throws Exception {
        RegionSnapshot current = container.snapshot();
        if (current.revision() != expectedRevision) {
            throw new RevisionConflictException(
                    "expected revision " + expectedRevision
                            + " but current is " + current.revision()
            );
        }

        RegionSnapshot validated = RegionSnapshot.ofRecords(
                expectedRevision + 1L,
                candidateRecords
        );
        Map<RegionKey, RegionRecord> candidate = validated.records();
        List<RegionRecord> writes = new ArrayList<>();
        List<RegionKey> deletes = new ArrayList<>();
        for (Map.Entry<RegionKey, RegionRecord> entry : candidate.entrySet()) {
            RegionRecord previous = current.records().get(entry.getKey());
            if (previous != entry.getValue()) {
                writes.add(entry.getValue());
            }
        }
        for (RegionKey key : current.records().keySet()) {
            if (!candidate.containsKey(key)) {
                deletes.add(key);
            }
        }
        if (writes.size() + deletes.size() > 1) {
            throw new IllegalArgumentException(
                    "one mutation may change exactly one region record"
            );
        }

        RegionRecord written = writes.isEmpty() ? null : writes.getFirst();
        RegionKey deleted = deletes.isEmpty() ? null : deletes.getFirst();
        RegionRecord previousWritten = written == null
                ? null
                : current.records().get(written.region().key());
        boolean previousDocumentExisted = written != null
                && repository.documentExists(written.region().key());
        java.nio.file.Path trashed = null;

        if (written != null) {
            repository.save(written);
        } else if (deleted != null) {
            trashed = repository.delete(deleted);
        }

        try {
            return container.recordPublication()
                    .compareAndPublish(expectedRevision, candidate.values())
                    .orElseThrow(() -> new RevisionConflictException(
                            "snapshot changed during repository commit"
                    ));
        } catch (Exception publicationFailure) {
            try {
                rollbackStorage(
                        written,
                        previousWritten,
                        previousDocumentExisted,
                        deleted,
                        trashed
                );
            } catch (Exception rollbackFailure) {
                publicationFailure.addSuppressed(rollbackFailure);
            }
            throw publicationFailure;
        }
    }

    /** Publishes a fully staged reload under the same lock as filesystem commits. */
    @Override
    public synchronized RegionSnapshot publishStaged(
            long expectedRevision,
            Collection<RegionRecord> stagedRecords) throws RevisionConflictException {
        RegionSnapshot current = container.snapshot();
        if(current.revision() != expectedRevision) {
            throw new RevisionConflictException(
                    "expected revision " + expectedRevision
                            + " but current is " + current.revision()
            );
        }
        return container.recordPublication()
                .compareAndPublish(expectedRevision, stagedRecords)
                .orElseThrow(() -> new RevisionConflictException(
                        "snapshot changed during staged publication"
                ));
    }

    private void rollbackStorage(RegionRecord written,
                                 RegionRecord previousWritten,
                                 boolean previousDocumentExisted,
                                 RegionKey deleted,
                                 java.nio.file.Path trashed) throws Exception {
        if(written != null) {
            if(previousDocumentExisted && previousWritten != null) {
                repository.save(previousWritten);
            } else {
                repository.discardCreated(written.region().key());
            }
            return;
        }
        if(deleted != null && trashed != null) {
            repository.restoreDeleted(trashed, deleted);
        }
    }
}
