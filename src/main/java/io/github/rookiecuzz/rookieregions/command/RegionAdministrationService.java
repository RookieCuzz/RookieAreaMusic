package io.github.rookiecuzz.rookieregions.command;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationActor;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationService;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;

/**
 * Asynchronous command facade for administrative mutations.
 *
 * <p>It deliberately owns neither a mutation port nor a lock. Every operation
 * delegates to {@link RegionMutationService}, which is the sole native-region
 * read/validate/commit/publication transaction boundary.</p>
 */
public final class RegionAdministrationService {
    private final RegionMutationService mutations;
    private final Executor executor;

    public RegionAdministrationService(RegionMutationService mutations,
                                       Executor executor) {
        this.mutations = Objects.requireNonNull(
                mutations, "mutation service cannot be null"
        );
        this.executor = Objects.requireNonNull(
                executor, "administration executor cannot be null"
        );
    }

    public CompletionStage<AdministrationResult> setParent(
            RegionKey key,
            RegionKey parent,
            RegionMutationActor actor) {
        return submit(() -> mutations.setParent(key, parent, actor));
    }

    public CompletionStage<AdministrationResult> delete(
            RegionKey key,
            RegionMutationActor actor) {
        return submit(() -> mutations.delete(key, actor));
    }

    public CompletionStage<AdministrationResult> updateCore(
            RegionKey key,
            UnaryOperator<Region> update,
            RegionMutationActor actor) {
        return submit(() -> mutations.updateCore(key, update, actor));
    }

    public CompletionStage<AdministrationResult> updateMusic(
            RegionKey key,
            UnaryOperator<RegionMusicProfile> update,
            RegionMutationActor actor) {
        return submit(() -> mutations.updateMusic(key, update, actor));
    }

    public CompletionStage<AdministrationResult> updateCommands(
            RegionKey key,
            UnaryOperator<RegionCommandProfile> update,
            RegionMutationActor actor) {
        return submit(() -> mutations.updateCommands(key, update, actor));
    }

    private CompletionStage<AdministrationResult> submit(
            Supplier<AdministrationResult> operation) {
        return CompletableFuture.supplyAsync(operation, executor);
    }
}
