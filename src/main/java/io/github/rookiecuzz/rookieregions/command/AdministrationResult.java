package io.github.rookiecuzz.rookieregions.command;

import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.util.Optional;

/** Result of one direct administrative record transaction. */
public record AdministrationResult(
        AdministrationStatus status,
        RegionSnapshot previousSnapshot,
        RegionSnapshot currentSnapshot,
        Optional<RegionRecord> previousRecord,
        Optional<RegionRecord> currentRecord,
        String message,
        Optional<Exception> cause
) {
    public AdministrationResult {
        if(status == null){
            throw new IllegalArgumentException("administration status cannot be null");
        }
        previousRecord = previousRecord == null
                ? Optional.empty()
                : previousRecord;
        currentRecord = currentRecord == null
                ? Optional.empty()
                : currentRecord;
        cause = cause == null ? Optional.empty() : cause;
        message = message == null ? "" : message;
        if(status == AdministrationStatus.SAVED
                && (previousSnapshot == null || currentSnapshot == null)){
            throw new IllegalArgumentException(
                    "saved administration result requires both snapshots"
            );
        }
    }

    public boolean saved(){
        return status == AdministrationStatus.SAVED;
    }
}
