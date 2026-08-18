package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.rule.Subject;

import java.util.concurrent.CompletionStage;

/** Stable asynchronous write contract published through Bukkit ServicesManager. */
public interface RegionMutationApi {
    CompletionStage<RegionSaveOutcome> attemptSave(
            RegionSaveRequest request,
            Subject subject
    );

    CompletionStage<RegionDeleteOutcome> delete(
            RegionKey key,
            Subject subject
    );
}
