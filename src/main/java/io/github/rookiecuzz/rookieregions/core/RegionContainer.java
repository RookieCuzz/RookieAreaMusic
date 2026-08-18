package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe owner of the currently published immutable region snapshot.
 *
 * <p>Readers are lock-free. Writers receive only the narrow publication
 * capability, which validates a complete replacement and assigns the next
 * revision internally.</p>
 */
public final class RegionContainer {
    private final AtomicReference<RegionSnapshot> current;
    private final RecordPublication recordPublication = this::compareAndPublishRecords;

    public RegionContainer() {
        this(RegionSnapshot.empty());
    }

    public RegionContainer(RegionSnapshot initialSnapshot) {
        current = new AtomicReference<>(Objects.requireNonNull(
                initialSnapshot,
                "initial region snapshot cannot be null"
        ));
    }

    public RegionSnapshot snapshot() {
        return current.get();
    }

    /** Creates a query pinned to exactly one published snapshot. */
    public RegionQuery query() {
        return new RegionQuery(snapshot());
    }

    public RecordPublication recordPublication() {
        return recordPublication;
    }

    private Optional<RegionSnapshot> compareAndPublishRecords(
            long expectedRevision,
            Collection<RegionRecord> records) {
        Objects.requireNonNull(records, "published records cannot be null");
        RegionSnapshot observed = observedForPublication(expectedRevision);
        if(observed == null){
            return Optional.empty();
        }
        RegionSnapshot candidate = RegionSnapshot.ofRecords(
                expectedRevision + 1L,
                records
        );
        return current.compareAndSet(observed, candidate)
                ? Optional.of(candidate)
                : Optional.empty();
    }

    private RegionSnapshot observedForPublication(long expectedRevision) {
        if(expectedRevision < 0L){
            throw new IllegalArgumentException("expected revision cannot be negative");
        }
        RegionSnapshot observed = current.get();
        if(observed.revision() != expectedRevision){
            return null;
        }
        if(expectedRevision == Long.MAX_VALUE){
            throw new IllegalStateException("region snapshot revision is exhausted");
        }
        return observed;
    }

    @FunctionalInterface
    public interface RecordPublication {
        Optional<RegionSnapshot> compareAndPublish(
                long expectedRevision,
                Collection<RegionRecord> records
        );
    }
}
