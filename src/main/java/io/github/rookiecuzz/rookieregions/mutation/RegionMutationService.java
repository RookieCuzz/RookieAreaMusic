package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.command.AdministrationResult;
import io.github.rookiecuzz.rookieregions.command.AdministrationStatus;
import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionQuery;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.rule.FlagValue;
import io.github.rookiecuzz.rookieregions.rule.FlagRegistry;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Objects;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.locks.ReentrantLock;
import java.time.Duration;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/** Serialized, optimistic save transaction with mandatory confirmation recheck. */
public final class RegionMutationService implements RegionMutationApi {
    private final RegionMutationPort port;
    private final PlacementPolicy placementPolicy;
    private final ConfirmationStore confirmations;
    private final Executor defaultExecutor;
    private final FlagRegistry flagRegistry;
    private Consumer<RegionSnapshot> candidateValidator;
    private final ReentrantLock mutationLock = new ReentrantLock();
    private final CopyOnWriteArrayList<Consumer<RegionMutationPublication>>
            publicationListeners = new CopyOnWriteArrayList<>();

    public RegionMutationService(RegionMutationPort port,
                                 PlacementPolicy placementPolicy,
                                 ConfirmationStore confirmations) {
        this(
                port,
                placementPolicy,
                confirmations,
                Runnable::run,
                ignored -> { },
                ProtectionFlags.REGISTRY
        );
    }

    public RegionMutationService(RegionMutationPort port,
                                 PlacementPolicy placementPolicy,
                                 ConfirmationStore confirmations,
                                 Executor defaultExecutor) {
        this(
                port,
                placementPolicy,
                confirmations,
                defaultExecutor,
                ignored -> { },
                ProtectionFlags.REGISTRY
        );
    }

    public RegionMutationService(RegionMutationPort port,
                                 PlacementPolicy placementPolicy,
                                 ConfirmationStore confirmations,
                                 Executor defaultExecutor,
                                 Consumer<RegionSnapshot> candidateValidator) {
        this(
                port,
                placementPolicy,
                confirmations,
                defaultExecutor,
                candidateValidator,
                ProtectionFlags.REGISTRY
        );
    }

    public RegionMutationService(RegionMutationPort port,
                                 PlacementPolicy placementPolicy,
                                 ConfirmationStore confirmations,
                                 Executor defaultExecutor,
                                 Consumer<RegionSnapshot> candidateValidator,
                                 FlagRegistry flagRegistry) {
        if(port == null || placementPolicy == null || confirmations == null){
            throw new IllegalArgumentException(
                    "mutation port, policy, and confirmation store cannot be null"
            );
        }
        this.port = port;
        this.placementPolicy = placementPolicy;
        this.confirmations = confirmations;
        this.defaultExecutor = java.util.Objects.requireNonNull(
                defaultExecutor, "default mutation executor cannot be null"
        );
        this.candidateValidator = Objects.requireNonNull(
                candidateValidator, "candidate validator cannot be null"
        );
        this.flagRegistry = Objects.requireNonNull(
                flagRegistry, "flag registry cannot be null"
        );
    }

    RegionSaveOutcome attemptSave(RegionSaveRequest request,
                                  RegionMutationActor actor){
        if(request == null || actor == null){
            throw new IllegalArgumentException("save request and actor cannot be null");
        }
        mutationLock.lock();
        try {
            return attemptLocked(request, actor);
        } finally {
            mutationLock.unlock();
        }
    }

    /** Public API shape: asynchronous save using an immutable query Subject. */
    @Override
    public CompletionStage<RegionSaveOutcome> attemptSave(
            RegionSaveRequest request,
            Subject subject) {
        if(subject == null) {
            throw new IllegalArgumentException("save subject cannot be null");
        }
        return attemptSaveAsync(request, actor(subject), defaultExecutor);
    }

    /** Asynchronous, permission-checked deletion for third-party integrations. */
    @Override
    public CompletionStage<RegionDeleteOutcome> delete(
            RegionKey key,
            Subject subject) {
        Objects.requireNonNull(key, "region key cannot be null");
        Objects.requireNonNull(subject, "delete subject cannot be null");
        return CompletableFuture.supplyAsync(
                () -> deleteOutcome(delete(key, actor(subject))),
                defaultExecutor
        );
    }

    /** Runs the same serialized transaction away from a caller-owned thread. */
    CompletionStage<RegionSaveOutcome> attemptSaveAsync(
            RegionSaveRequest request,
            RegionMutationActor actor,
            Executor executor){
        if(executor == null){
            throw new IllegalArgumentException("save executor cannot be null");
        }
        return CompletableFuture.supplyAsync(
                () -> attemptSave(request, actor), executor
        );
    }

    public void invalidateConfirmations(String actorId, String sessionId){
        confirmations.invalidateSession(actorId, sessionId);
    }

    public void invalidateAllConfirmations(){
        confirmations.invalidateAll();
    }

    public void updateConfirmationLifetime(Duration lifetime){
        confirmations.setLifetime(lifetime);
    }

    public void addPublicationListener(
            Consumer<RegionMutationPublication> listener) {
        publicationListeners.add(java.util.Objects.requireNonNull(
                listener, "mutation publication listener cannot be null"
        ));
    }

    /**
     * Atomically publishes a fully staged reload and installs the validator
     * for the same runtime-config epoch. Ordinary mutations cannot interleave
     * between publication and validator replacement.
     */
    public RegionSnapshot publishStagedReload(
            long expectedRevision,
            Collection<RegionRecord> stagedRecords,
            Consumer<RegionSnapshot> replacementValidator) throws Exception {
        Objects.requireNonNull(stagedRecords, "staged records cannot be null");
        Objects.requireNonNull(
                replacementValidator, "replacement validator cannot be null"
        );
        mutationLock.lock();
        try {
            long nextRevision = Math.addExact(expectedRevision, 1L);
            RegionSnapshot candidate = RegionSnapshot.ofRecords(
                    nextRevision, stagedRecords
            );
            replacementValidator.accept(candidate);
            RegionSnapshot published = port.publishStaged(
                    expectedRevision, candidate.records().values()
            );
            if(published == null || published.revision() != nextRevision
                    || !publicationMatches(
                            candidate.records().values(), published
                    )) {
                throw new IllegalStateException(
                        "staged publication returned an inconsistent snapshot"
                );
            }
            candidateValidator = replacementValidator;
            return published;
        } finally {
            mutationLock.unlock();
        }
    }

    /**
     * Explicit administrative re-parenting. The complete read/validate/commit
     * transaction uses the same lock and publication path as editor saves.
     */
    public AdministrationResult setParent(RegionKey key,
                                          RegionKey parent,
                                          RegionMutationActor actor) {
        Objects.requireNonNull(key, "region key cannot be null");
        Objects.requireNonNull(parent, "parent key cannot be null");
        Objects.requireNonNull(actor, "mutation actor cannot be null");
        mutationLock.lock();
        try {
            RegionSnapshot snapshot = administrativeSnapshot();
            if(snapshot == null) {
                return administrationFailed(
                        null,
                        "unable to read the current region snapshot",
                        new IllegalStateException("mutation port returned null")
                );
            }
            RegionRecord previous = snapshot.records().get(key);
            if(previous == null) {
                return administrationResult(
                        AdministrationStatus.NOT_FOUND, snapshot, null, null,
                        "region does not exist", null
                );
            }
            if(!actor.hasPermission(MutationPermissions.EDIT_ANY)) {
                return administrationResult(
                        AdministrationStatus.PERMISSION_DENIED, snapshot,
                        previous, null,
                        "re-parenting requires " + MutationPermissions.EDIT_ANY,
                        null
                );
            }
            if(key.isGlobal()) {
                return administrationResult(
                        AdministrationStatus.GLOBAL_FORBIDDEN, snapshot,
                        previous, null,
                        "the global region cannot be re-parented", null
                );
            }
            if(!snapshot.records().containsKey(parent)) {
                return administrationResult(
                        AdministrationStatus.NOT_FOUND, snapshot, previous,
                        null, "parent region does not exist", null
                );
            }
            if(previous.region().parent().filter(parent::equals).isPresent()) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, "region already has that parent", null
                );
            }
            Region changed = copyWithParent(previous.region(), parent);
            RegionRecord current = new RegionRecord(
                    changed, previous.music(), previous.commands()
            );
            return commitAdministration(
                    snapshot, previous, current,
                    replaceRecord(snapshot, current), actor, SaveMode.EDIT
            );
        } finally {
            mutationLock.unlock();
        }
    }

    /** Deletes one childless finite region through the canonical transaction. */
    public AdministrationResult delete(RegionKey key,
                                       RegionMutationActor actor) {
        Objects.requireNonNull(key, "region key cannot be null");
        Objects.requireNonNull(actor, "mutation actor cannot be null");
        mutationLock.lock();
        try {
            RegionSnapshot snapshot = administrativeSnapshot();
            if(snapshot == null) {
                return administrationFailed(
                        null,
                        "unable to read the current region snapshot",
                        new IllegalStateException("mutation port returned null")
                );
            }
            RegionRecord previous = snapshot.records().get(key);
            if(previous == null) {
                return administrationResult(
                        AdministrationStatus.NOT_FOUND, snapshot, null, null,
                        "region does not exist", null
                );
            }
            if(!actor.hasPermission(MutationPermissions.DELETE)) {
                return administrationResult(
                        AdministrationStatus.PERMISSION_DENIED, snapshot,
                        previous, null,
                        "deleting requires " + MutationPermissions.DELETE,
                        null
                );
            }
            if(key.isGlobal()) {
                return administrationResult(
                        AdministrationStatus.GLOBAL_FORBIDDEN, snapshot,
                        previous, null,
                        "the global region cannot be deleted", null
                );
            }
            if(!snapshot.graph().children(key).isEmpty()) {
                return administrationResult(
                        AdministrationStatus.HAS_CHILDREN, snapshot, previous,
                        null, "move or delete child regions first", null
                );
            }
            ArrayList<RegionRecord> candidates = new ArrayList<>(
                    snapshot.records().size() - 1
            );
            for(RegionRecord record : snapshot.records().values()) {
                if(!record.region().key().equals(key)) {
                    candidates.add(record);
                }
            }
            return commitAdministration(
                    snapshot, previous, null, candidates, actor, SaveMode.DELETE
            );
        } finally {
            mutationLock.unlock();
        }
    }

    /** Updates only core administrative fields; geometry and parent are fixed. */
    public AdministrationResult updateCore(
            RegionKey key,
            UnaryOperator<Region> update,
            RegionMutationActor actor) {
        Objects.requireNonNull(key, "region key cannot be null");
        Objects.requireNonNull(update, "core update cannot be null");
        Objects.requireNonNull(actor, "mutation actor cannot be null");
        mutationLock.lock();
        try {
            RegionSnapshot snapshot = administrativeSnapshot();
            if(snapshot == null) {
                return administrationFailed(
                        null,
                        "unable to read the current region snapshot",
                        new IllegalStateException("mutation port returned null")
                );
            }
            RegionRecord previous = snapshot.records().get(key);
            if(previous == null) {
                return administrationResult(
                        AdministrationStatus.NOT_FOUND, snapshot, null, null,
                        "region does not exist", null
                );
            }
            if(!isAuthorized(SaveMode.EDIT, actor, previous.region(), snapshot)) {
                return administrationResult(
                        AdministrationStatus.PERMISSION_DENIED, snapshot,
                        previous, null,
                        "the actor is not permitted to edit this region", null
                );
            }
            Region changed;
            try {
                changed = update.apply(previous.region());
                if(changed == null) {
                    throw new IllegalArgumentException(
                            "core update returned a null region"
                    );
                }
            } catch(RuntimeException exception) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, safeMessage(exception), exception
                );
            }
            if(!changed.key().equals(key)
                    || !changed.shape().equals(previous.region().shape())
                    || !changed.parent().equals(previous.region().parent())) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null,
                        "core administration cannot change key, shape, or parent",
                        null
                );
            }
            RegionSaveOutcome flagRejection = flagChangeRejection(
                    previous.region(), changed, actor
            );
            if(flagRejection instanceof RegionSaveOutcome.Rejected rejected) {
                AdministrationStatus status = rejected.reason()
                        == RegionSaveRejection.PERMISSION_DENIED
                        ? AdministrationStatus.PERMISSION_DENIED
                        : AdministrationStatus.INVALID;
                return administrationResult(
                        status, snapshot, previous, null,
                        rejected.message(), null
                );
            }
            if(RegionFingerprints.region(changed).equals(
                    RegionFingerprints.region(previous.region()))) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, "region core is unchanged", null
                );
            }
            RegionRecord current = new RegionRecord(
                    changed, previous.music(), previous.commands()
            );
            return commitAdministration(
                    snapshot, previous, current,
                    replaceRecord(snapshot, current), actor, SaveMode.EDIT
            );
        } finally {
            mutationLock.unlock();
        }
    }

    /** Updates one music attachment through the canonical transaction. */
    public AdministrationResult updateMusic(
            RegionKey key,
            UnaryOperator<RegionMusicProfile> update,
            RegionMutationActor actor) {
        Objects.requireNonNull(key, "region key cannot be null");
        Objects.requireNonNull(update, "music update cannot be null");
        Objects.requireNonNull(actor, "mutation actor cannot be null");
        mutationLock.lock();
        try {
            RegionSnapshot snapshot = administrativeSnapshot();
            if(snapshot == null) {
                return administrationFailed(
                        null,
                        "unable to read the current region snapshot",
                        new IllegalStateException("mutation port returned null")
                );
            }
            RegionRecord previous = snapshot.records().get(key);
            if(previous == null) {
                return administrationResult(
                        AdministrationStatus.NOT_FOUND, snapshot, null, null,
                        "region does not exist", null
                );
            }
            if(!actor.hasPermission(MutationPermissions.MUSIC)) {
                return administrationResult(
                        AdministrationStatus.PERMISSION_DENIED, snapshot,
                        previous, null,
                        "music updates require " + MutationPermissions.MUSIC,
                        null
                );
            }
            RegionMusicProfile changed;
            try {
                changed = update.apply(previous.music());
                if(changed == null) {
                    throw new IllegalArgumentException(
                            "music update returned a null profile"
                    );
                }
            } catch(RuntimeException exception) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, safeMessage(exception), exception
                );
            }
            if(changed.equals(previous.music())) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, "music profile is unchanged", null
                );
            }
            RegionRecord current = new RegionRecord(
                    previous.region(), changed, previous.commands()
            );
            return commitAdministration(
                    snapshot, previous, current,
                    replaceRecord(snapshot, current), actor, SaveMode.EDIT
            );
        } finally {
            mutationLock.unlock();
        }
    }

    /** Updates one commands attachment through the canonical transaction. */
    public AdministrationResult updateCommands(
            RegionKey key,
            UnaryOperator<RegionCommandProfile> update,
            RegionMutationActor actor) {
        Objects.requireNonNull(key, "region key cannot be null");
        Objects.requireNonNull(update, "commands update cannot be null");
        Objects.requireNonNull(actor, "mutation actor cannot be null");
        mutationLock.lock();
        try {
            RegionSnapshot snapshot = administrativeSnapshot();
            if(snapshot == null) {
                return administrationFailed(
                        null,
                        "unable to read the current region snapshot",
                        new IllegalStateException("mutation port returned null")
                );
            }
            RegionRecord previous = snapshot.records().get(key);
            if(previous == null) {
                return administrationResult(
                        AdministrationStatus.NOT_FOUND, snapshot, null, null,
                        "region does not exist", null
                );
            }
            if(!actor.hasPermission(MutationPermissions.COMMANDS)) {
                return administrationResult(
                        AdministrationStatus.PERMISSION_DENIED, snapshot,
                        previous, null,
                        "commands updates require " + MutationPermissions.COMMANDS,
                        null
                );
            }
            RegionCommandProfile changed;
            try {
                changed = update.apply(previous.commands());
                if(changed == null) {
                    throw new IllegalArgumentException(
                            "commands update returned a null profile"
                    );
                }
            } catch(RuntimeException exception) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, safeMessage(exception), exception
                );
            }
            if(changed.equals(previous.commands())) {
                return administrationResult(
                        AdministrationStatus.INVALID, snapshot, previous,
                        null, "commands profile is unchanged", null
                );
            }
            RegionRecord current = new RegionRecord(
                    previous.region(), previous.music(), changed
            );
            return commitAdministration(
                    snapshot, previous, current,
                    replaceRecord(snapshot, current), actor, SaveMode.EDIT
            );
        } finally {
            mutationLock.unlock();
        }
    }

    private RegionSaveOutcome attemptLocked(RegionSaveRequest request,
                                            RegionMutationActor actor){
        RegionSnapshot snapshot;
        try {
            snapshot = port.currentSnapshot();
            if(snapshot == null){
                throw new IllegalStateException("mutation port returned a null snapshot");
            }
        } catch (Exception exception){
            return new RegionSaveOutcome.Failed(
                    "unable to read the current region snapshot", exception
            );
        }

        if(snapshot.revision() != request.expectedSnapshotRevision()){
            confirmations.invalidateSession(actor.actorId(), request.sessionId());
            return new RegionSaveOutcome.Stale(
                    StaleReason.SNAPSHOT_REVISION_CHANGED,
                    "the region snapshot changed after editing began"
            );
        }

        Optional<Region> currentTarget = snapshot.graph().region(
                request.candidate().key()
        );
        if(request.mode() == SaveMode.CREATE && currentTarget.isPresent()){
            return rejected(
                    RegionSaveRejection.REGION_ALREADY_EXISTS,
                    "a region with this key already exists"
            );
        }
        if(request.mode() == SaveMode.EDIT && currentTarget.isEmpty()){
            return rejected(
                    RegionSaveRejection.REGION_NOT_FOUND,
                    "the edited region no longer exists"
            );
        }
        if(!isAuthorized(
                request.mode(), actor, currentTarget.orElse(null), snapshot
        )){
            return rejected(
                    RegionSaveRejection.PERMISSION_DENIED,
                    "the actor is not permitted to save this region"
            );
        }
        RegionSaveOutcome flagRejection = flagChangeRejection(
                currentTarget.orElse(null), request.candidate(), actor
        );
        if(flagRejection != null){
            return flagRejection;
        }

        if(request.mode() == SaveMode.EDIT){
            String actualTarget = fingerprint(currentTarget.orElseThrow());
            if(actualTarget == null){
                return rejected(
                        RegionSaveRejection.INVALID_CANDIDATE,
                        "the current target cannot be fingerprinted"
                );
            }
            if(!actualTarget.equals(request.expectedTargetFingerprint().orElseThrow())){
                confirmations.invalidateSession(actor.actorId(), request.sessionId());
                return new RegionSaveOutcome.Stale(
                        StaleReason.TARGET_CHANGED,
                        "the edited target changed after the session began"
                );
            }
        }

        String candidateFingerprint = fingerprint(request.candidate());
        if(candidateFingerprint == null){
            return rejected(
                    RegionSaveRejection.INVALID_CANDIDATE,
                    "the candidate cannot be fingerprinted"
            );
        }

        if(request.candidate().key().isGlobal()){
            if(request.mode() != SaveMode.EDIT
                    || request.candidate().shape() != GlobalShape.INSTANCE
                    || request.candidate().parent().isPresent()){
                return rejected(
                        RegionSaveRejection.INVALID_CANDIDATE,
                        "the global region can only be updated in place"
                );
            }
            return commit(
                    request,
                    snapshot,
                    request.candidate(),
                    PlacementOption.direct(),
                    actor
            );
        }

        PlacementPlan plan;
        try {
            RegionKey excluded = request.mode() == SaveMode.EDIT
                    ? request.candidate().key()
                    : null;
            plan = placementPolicy.evaluate(
                    request.mode(),
                    request.candidate(),
                    snapshot,
                    new RegionQuery(snapshot).relations(
                            request.candidate(), excluded
                    ),
                    actor.hasPermission(MutationPermissions.OVERLAP)
            );
        } catch (RuntimeException exception){
            return rejected(
                    RegionSaveRejection.INVALID_CANDIDATE,
                    "placement evaluation failed: " + exception.getMessage()
            );
        }

        if(request.confirmationToken().isEmpty()){
            return handleUnconfirmed(
                    request,
                    actor,
                    snapshot,
                    candidateFingerprint,
                    plan
            );
        }
        return handleConfirmed(
                request,
                actor,
                snapshot,
                candidateFingerprint,
                plan
        );
    }

    private RegionSaveOutcome handleUnconfirmed(
            RegionSaveRequest request,
            RegionMutationActor actor,
            RegionSnapshot snapshot,
            String candidateFingerprint,
            PlacementPlan plan){
        if(plan.disposition() == PlanDisposition.REJECTED){
            return rejected(plan.rejection().orElseThrow(), plan.message());
        }
        if(plan.disposition() == PlanDisposition.DIRECT){
            return commit(
                    request,
                    snapshot,
                    request.candidate(),
                    PlacementOption.direct(),
                    actor
            );
        }
        try {
            List<ConfirmationOption> options = confirmations.issue(
                    actor.actorId(),
                    request.sessionId(),
                    candidateFingerprint,
                    plan.fingerprint(),
                    snapshot.revision(),
                    plan.options()
            );
            return new RegionSaveOutcome.ConfirmationRequired(
                    plan.fingerprint(), options
            );
        } catch (RuntimeException exception){
            return new RegionSaveOutcome.Failed(
                    "unable to issue a region confirmation", exception
            );
        }
    }

    private RegionSaveOutcome handleConfirmed(
            RegionSaveRequest request,
            RegionMutationActor actor,
            RegionSnapshot snapshot,
            String candidateFingerprint,
            PlacementPlan plan){
        ConfirmationConsumption consumed = confirmations.consume(
                request.confirmationToken().orElseThrow(),
                actor.actorId(),
                request.sessionId(),
                candidateFingerprint
        );
        if(consumed.status() == ConfirmationConsumeStatus.INVALID){
            return rejected(
                    RegionSaveRejection.CONFIRMATION_INVALID,
                    "the confirmation token is invalid or already used"
            );
        }
        if(consumed.status() == ConfirmationConsumeStatus.EXPIRED){
            return rejected(
                    RegionSaveRejection.CONFIRMATION_EXPIRED,
                    "the confirmation token expired"
            );
        }
        if(consumed.status() == ConfirmationConsumeStatus.BINDING_MISMATCH){
            return rejected(
                    RegionSaveRejection.CONFIRMATION_BINDING_MISMATCH,
                    "the confirmation token belongs to another actor, session, or draft"
            );
        }

        ConfirmationAuthorization authorization = consumed.authorization()
                .orElseThrow();
        if(authorization.snapshotRevision() != snapshot.revision()
                || plan.disposition() != PlanDisposition.CONFIRMATION_REQUIRED
                || !authorization.placementPlanFingerprint().equals(plan.fingerprint())
                || !plan.options().contains(authorization.option())){
            return new RegionSaveOutcome.Stale(
                    StaleReason.PLACEMENT_CHANGED,
                    "the overlap placement changed after confirmation was issued"
            );
        }
        Region effective = applyChoice(request.candidate(), authorization.option());
        return commit(
                request, snapshot, effective, authorization.option(), actor
        );
    }

    private RegionSaveOutcome commit(RegionSaveRequest request,
                                     RegionSnapshot snapshot,
                                     Region effective,
                                     PlacementOption option,
                                     RegionMutationActor actor){
        List<RegionRecord> candidates = new ArrayList<>(
                snapshot.graph().regions().size() + 1
        );
        if(snapshot.records().size() != snapshot.graph().regions().size()){
            return new RegionSaveOutcome.Failed(
                    "the current snapshot record set does not match its graph",
                    new IllegalStateException("inconsistent current snapshot")
            );
        }
        boolean replaced = false;
        for(Region region : snapshot.graph().regions()){
            RegionRecord record = snapshot.records().get(region.key());
            if(record == null){
                return new RegionSaveOutcome.Failed(
                        "the current snapshot has no record for " + region.key(),
                        new IllegalStateException("snapshot record set is incomplete")
                );
            }
            if(region.key().equals(effective.key())){
                candidates.add(new RegionRecord(
                        effective, record.music(), record.commands()
                ));
                replaced = true;
            } else {
                candidates.add(record);
            }
        }
        if(request.mode() == SaveMode.CREATE){
            candidates.add(RegionRecord.coreOnly(effective));
        } else if(!replaced){
            return rejected(
                    RegionSaveRejection.REGION_NOT_FOUND,
                    "the edited region disappeared before commit"
            );
        }

        long nextRevision;
        try {
            nextRevision = Math.addExact(snapshot.revision(), 1L);
        } catch (ArithmeticException exception){
            return new RegionSaveOutcome.Failed(
                    "the region snapshot revision is exhausted", exception
            );
        }
        try {
            RegionSnapshot candidateSnapshot = RegionSnapshot.ofRecords(
                    nextRevision, candidates
            );
            candidateValidator.accept(candidateSnapshot);
        } catch (RuntimeException exception){
            return rejected(
                    RegionSaveRejection.INVALID_CANDIDATE,
                    "the proposed region snapshot is invalid: "
                            + safeMessage(exception)
            );
        }

        try {
            RegionSnapshot committed = port.commit(snapshot.revision(), candidates);
            if(committed == null || committed.revision() != nextRevision){
                throw new IllegalStateException(
                        "mutation port returned an invalid committed snapshot"
                );
            }
            if(!publicationMatches(candidates, committed)){
                throw new IllegalStateException(
                        "committed snapshot changed regions or module attachments"
                );
            }
            Region published = committed.graph().region(effective.key())
                    .orElseThrow(() -> new IllegalStateException(
                            "committed snapshot omitted the saved region"
                    ));
            RegionSaveOutcome.Saved saved = new RegionSaveOutcome.Saved(
                    committed, published, option.choice()
            );
            notifyPublication(new RegionMutationPublication(
                    request.mode(),
                    snapshot,
                    committed,
                    Optional.ofNullable(snapshot.graph().region(
                            effective.key()
                    ).orElse(null)),
                    Optional.of(published),
                    actor,
                    request.sessionId(),
                    option.choice()
            ));
            return saved;
        } catch (RevisionConflictException conflict){
            return new RegionSaveOutcome.Stale(
                    StaleReason.COMMIT_RACE,
                    conflict.getMessage()
            );
        } catch (Exception exception){
            return new RegionSaveOutcome.Failed(
                    "atomic region commit failed", exception
            );
        }
    }

    private void notifyPublication(RegionMutationPublication publication){
        for(Consumer<RegionMutationPublication> listener : publicationListeners){
            try {
                listener.accept(publication);
            } catch(RuntimeException ignored){
                // Persistence and snapshot publication are already committed.
            }
        }
    }

    private boolean publicationMatches(Collection<RegionRecord> expected,
                                       RegionSnapshot actual){
        if(actual.records().size() != expected.size()
                || actual.graph().regions().size() != expected.size()){
            return false;
        }
        for(RegionRecord expectedRecord : expected){
            RegionRecord actualRecord = actual.records().get(
                    expectedRecord.region().key()
            );
            Region actualRegion = actual.graph().region(
                    expectedRecord.region().key()
            ).orElse(null);
            if(actualRecord == null
                    || actualRegion == null
                    || !RegionFingerprints.region(actualRecord.region()).equals(
                            RegionFingerprints.region(expectedRecord.region())
                    )
                    || !RegionFingerprints.region(actualRegion).equals(
                            RegionFingerprints.region(expectedRecord.region())
                    )
                    || !actualRecord.music().equals(expectedRecord.music())
                    || !actualRecord.commands().equals(expectedRecord.commands())){
                return false;
            }
        }
        return true;
    }

    private RegionSnapshot administrativeSnapshot() {
        try {
            return port.currentSnapshot();
        } catch(RuntimeException ignored) {
            return null;
        }
    }

    private RegionMutationActor actor(Subject subject) {
        String actorId = subject.playerId() == null
                ? "non-player"
                : subject.playerId().toString();
        return new RegionMutationActor(
                actorId,
                subject.playerId(),
                subject.groups(),
                subject.permissions()
        );
    }

    private RegionDeleteOutcome deleteOutcome(AdministrationResult result) {
        RegionDeleteStatus status = switch(result.status()) {
            case SAVED -> RegionDeleteStatus.DELETED;
            case NOT_FOUND -> RegionDeleteStatus.NOT_FOUND;
            case HAS_CHILDREN -> RegionDeleteStatus.HAS_CHILDREN;
            case STALE -> RegionDeleteStatus.STALE;
            case FAILED -> RegionDeleteStatus.STORAGE_FAILURE;
            case GLOBAL_FORBIDDEN, PERMISSION_DENIED, INVALID ->
                    RegionDeleteStatus.DENIED;
        };
        RegionSnapshot snapshot = result.currentSnapshot() != null
                ? result.currentSnapshot()
                : result.previousSnapshot();
        return new RegionDeleteOutcome(
                status,
                Optional.ofNullable(snapshot),
                result.previousRecord().map(RegionRecord::region),
                result.message(),
                result.cause().map(exception -> (Throwable) exception)
        );
    }

    private List<RegionRecord> replaceRecord(RegionSnapshot snapshot,
                                             RegionRecord current) {
        ArrayList<RegionRecord> candidates = new ArrayList<>(
                snapshot.records().size()
        );
        for(RegionRecord record : snapshot.records().values()) {
            candidates.add(record.region().key().equals(current.region().key())
                    ? current
                    : record);
        }
        return candidates;
    }

    private AdministrationResult commitAdministration(
            RegionSnapshot snapshot,
            RegionRecord previous,
            RegionRecord current,
            List<RegionRecord> candidates,
            RegionMutationActor actor,
            SaveMode mode) {
        long nextRevision;
        try {
            nextRevision = Math.addExact(snapshot.revision(), 1L);
            RegionSnapshot candidateSnapshot = RegionSnapshot.ofRecords(
                    nextRevision, candidates
            );
            candidateValidator.accept(candidateSnapshot);
        } catch(RuntimeException exception) {
            return administrationResult(
                    AdministrationStatus.INVALID, snapshot, previous, current,
                    "proposed region snapshot is invalid: "
                            + safeMessage(exception),
                    exception
            );
        }
        try {
            RegionSnapshot committed = port.commit(
                    snapshot.revision(), candidates
            );
            if(committed == null || committed.revision() != nextRevision) {
                throw new IllegalStateException(
                        "mutation port returned an invalid committed snapshot"
                );
            }
            if(!publicationMatches(candidates, committed)) {
                throw new IllegalStateException(
                        "committed snapshot changed regions or module attachments"
                );
            }
            RegionRecord published = current == null
                    ? null
                    : committed.records().get(current.region().key());
            if(current != null && published == null) {
                throw new IllegalStateException(
                        "committed snapshot omitted the updated record"
                );
            }
            if(current == null && committed.records().containsKey(
                    previous.region().key())) {
                throw new IllegalStateException(
                        "committed snapshot retained the deleted record"
                );
            }
            notifyPublication(new RegionMutationPublication(
                    mode,
                    snapshot,
                    committed,
                    Optional.of(previous.region()),
                    Optional.ofNullable(published).map(RegionRecord::region),
                    actor,
                    "administration-" + java.util.UUID.randomUUID(),
                    SaveChoice.DIRECT
            ));
            return new AdministrationResult(
                    AdministrationStatus.SAVED,
                    snapshot,
                    committed,
                    Optional.of(previous),
                    Optional.ofNullable(published),
                    "",
                    Optional.empty()
            );
        } catch(RevisionConflictException conflict) {
            return administrationResult(
                    AdministrationStatus.STALE, snapshot, previous, current,
                    safeMessage(conflict), conflict
            );
        } catch(Exception exception) {
            return administrationFailed(
                    snapshot, "atomic administration commit failed", exception
            );
        }
    }

    private AdministrationResult administrationResult(
            AdministrationStatus status,
            RegionSnapshot snapshot,
            RegionRecord previous,
            RegionRecord current,
            String message,
            Exception cause) {
        return new AdministrationResult(
                status,
                snapshot,
                null,
                Optional.ofNullable(previous),
                Optional.ofNullable(current),
                message,
                Optional.ofNullable(cause)
        );
    }

    private AdministrationResult administrationFailed(
            RegionSnapshot snapshot,
            String message,
            Exception cause) {
        return administrationResult(
                AdministrationStatus.FAILED, snapshot, null, null,
                message, cause
        );
    }

    private Region copyWithParent(Region source, RegionKey parent) {
        Region.Builder builder = Region.builder(source.key(), source.shape())
                .priority(source.priority())
                .parent(parent)
                .owners(source.owners())
                .members(source.members());
        for(FlagValue<?> value : source.flags().values()) {
            builder.flagValue(value);
        }
        return builder.build();
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }

    private boolean isAuthorized(SaveMode mode,
                                 RegionMutationActor actor,
                                 Region current,
                                 RegionSnapshot snapshot){
        if(mode == SaveMode.CREATE){
            return actor.hasPermission(MutationPermissions.CREATE);
        }
        if(actor.hasPermission(MutationPermissions.EDIT_ANY)){
            return true;
        }
        return actor.hasPermission(MutationPermissions.EDIT_OWN)
                && current != null
                && snapshot.graph().hasInheritedOwner(
                        current.key(), actor.playerUuid(), actor.groups()
                );
    }

    /** Returns a deterministic rejection when a changed flag is unauthorized. */
    private RegionSaveOutcome flagChangeRejection(Region current,
                                                  Region candidate,
                                                  RegionMutationActor actor){
        TreeSet<String> names = new TreeSet<>(candidate.flags().keySet());
        if(current != null){
            names.addAll(current.flags().keySet());
        }
        for(String name : names){
            FlagValue<?> previous = current == null
                    ? null
                    : current.flags().get(name);
            FlagValue<?> proposed = candidate.flags().get(name);
            io.github.rookiecuzz.rookieregions.rule.Flag<?> registered =
                    flagRegistry.find(name).orElse(null);
            if(registered == null
                    || previous != null && previous.flag() != registered
                    || proposed != null && proposed.flag() != registered){
                return rejected(
                        RegionSaveRejection.INVALID_CANDIDATE,
                        "the candidate contains an unregistered or incompatible flag: "
                                + name
                );
            }
            if(Objects.equals(previous, proposed)){
                continue;
            }
            if(!actor.hasPermission(registered.modificationPermission())){
                return rejected(
                        RegionSaveRejection.PERMISSION_DENIED,
                        "the actor cannot modify flag " + registered.name()
                                + " (requires "
                                + registered.modificationPermission() + ")"
                );
            }
        }
        return null;
    }

    private Region applyChoice(Region candidate, PlacementOption option){
        if(option.choice() != SaveChoice.SET_PARENT){
            return candidate;
        }
        Region.Builder builder = Region.builder(candidate.key(), candidate.shape())
                .priority(candidate.priority())
                .parent(option.parent().orElseThrow())
                .owners(candidate.owners())
                .members(candidate.members());
        for(FlagValue<?> flag : candidate.flags().values()){
            builder.flagValue(flag);
        }
        return builder.build();
    }

    private String fingerprint(Region region){
        try {
            return RegionFingerprints.region(region);
        } catch (RuntimeException ignored){
            return null;
        }
    }

    private RegionSaveOutcome rejected(RegionSaveRejection reason,
                                       String message){
        return new RegionSaveOutcome.Rejected(reason, message);
    }
}
