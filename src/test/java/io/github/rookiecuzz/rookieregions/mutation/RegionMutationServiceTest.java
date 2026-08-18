package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionDomain;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.command.AdministrationResult;
import io.github.rookiecuzz.rookieregions.command.AdministrationStatus;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import io.github.rookiecuzz.rookieregions.rule.Subject;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionMutationServiceTest {
    private static final UUID PLAYER = UUID.fromString(
            "00000000-0000-0000-0000-000000000201"
    );

    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000202"),
            "minecraft:overworld"
    );

    @Test
    void asynchronousDirectCreateCommitsOneCoreOnlyRecord() throws Exception {
        FakePort port = port(7L);
        RegionMutationService service = service(port);
        List<RegionMutationPublication> publications = new ArrayList<>();
        service.addPublicationListener(publications::add);
        Region candidate = box("new-region", globalKey(), 20, 30);

        CompletionStage<RegionSaveOutcome> stage = service.attemptSaveAsync(
                createRequest("direct", candidate, 7L, null),
                actor(MutationPermissions.CREATE),
                Runnable::run
        );

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                stage.toCompletableFuture().get()
        );
        assertEquals(SaveChoice.DIRECT, saved.choice());
        assertEquals(8L, saved.snapshot().revision());
        RegionRecord created = saved.snapshot().records().get(candidate.key());
        assertTrue(created.music().isEmpty());
        assertTrue(created.commands().isEmpty());
        assertEquals(1, port.commits);
        assertEquals(1, publications.size());
        assertEquals(SaveMode.CREATE, publications.getFirst().mode());
        assertTrue(publications.getFirst().previousRegion().isEmpty());
        assertEquals(candidate.key(), publications.getFirst()
                .currentRegion().orElseThrow().key());
    }

    @Test
    void ordinaryContainedCreateRequiresAndAppliesSetParentConfirmation(){
        Region parent = allowedBox("parent", globalKey(), 0, 100);
        FakePort port = port(4L, RegionRecord.coreOnly(parent));
        ConfirmationStore confirmations = new ConfirmationStore();
        RegionMutationService service = service(port, confirmations);
        Region candidate = box("child", globalKey(), 10, 20);
        RegionMutationActor actor = actor(MutationPermissions.CREATE);

        RegionSaveOutcome.ConfirmationRequired required = assertInstanceOf(
                RegionSaveOutcome.ConfirmationRequired.class,
                service.attemptSave(
                        createRequest("parent-choice", candidate, 4L, null), actor
                )
        );
        assertEquals(0, port.commits);
        ConfirmationOption option = required.options().get(0);
        assertEquals(PlacementOption.setParent(parent.key()), option.option());

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service.attemptSave(
                        createRequest(
                                "parent-choice", candidate, 4L, option.token()
                        ),
                        actor
                )
        );

        assertEquals(SaveChoice.SET_PARENT, saved.choice());
        assertEquals(Optional.of(parent.key()), saved.region().parent());
        assertEquals(0, confirmations.size());
    }

    @Test
    void administratorOverlapRequiresAndConsumesKeepOverlapConfirmation(){
        Region peer = box("peer", globalKey(), 0, 10);
        FakePort port = port(2L, RegionRecord.coreOnly(peer));
        RegionMutationService service = service(port);
        Region candidate = box("overlap", globalKey(), 5, 15);
        RegionMutationActor administrator = actor(
                MutationPermissions.CREATE,
                MutationPermissions.OVERLAP
        );

        RegionSaveOutcome.ConfirmationRequired required = assertInstanceOf(
                RegionSaveOutcome.ConfirmationRequired.class,
                service.attemptSave(
                        createRequest("overlap", candidate, 2L, null),
                        administrator
                )
        );
        assertEquals(PlacementOption.keepOverlap(),
                required.options().get(0).option());

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service.attemptSave(
                        createRequest(
                                "overlap", candidate, 2L,
                                required.options().get(0).token()
                        ),
                        administrator
                )
        );
        assertEquals(SaveChoice.KEEP_OVERLAP, saved.choice());
    }

    @Test
    void confirmationCannotCrossSnapshotRevision(){
        Region peer = box("peer", globalKey(), 0, 10);
        FakePort port = port(9L, RegionRecord.coreOnly(peer));
        ConfirmationStore confirmations = new ConfirmationStore();
        RegionMutationService service = service(port, confirmations);
        Region candidate = box("overlap", globalKey(), 5, 15);
        RegionMutationActor administrator = actor(
                MutationPermissions.CREATE,
                MutationPermissions.OVERLAP
        );
        RegionSaveOutcome.ConfirmationRequired required = assertInstanceOf(
                RegionSaveOutcome.ConfirmationRequired.class,
                service.attemptSave(
                        createRequest("stale", candidate, 9L, null), administrator
                )
        );

        port.advanceExternally();
        RegionSaveOutcome.Stale stale = assertInstanceOf(
                RegionSaveOutcome.Stale.class,
                service.attemptSave(
                        createRequest(
                                "stale", candidate, 9L,
                                required.options().get(0).token()
                        ),
                        administrator
                )
        );

        assertEquals(StaleReason.SNAPSHOT_REVISION_CHANGED, stale.reason());
        assertEquals(0, confirmations.size());
        assertEquals(0, port.commits);
    }

    @Test
    void confirmationIsBoundToTheExactCandidateFingerprint(){
        Region peer = box("peer", globalKey(), 0, 10);
        FakePort port = port(3L, RegionRecord.coreOnly(peer));
        ConfirmationStore confirmations = new ConfirmationStore();
        RegionMutationService service = service(port, confirmations);
        RegionMutationActor administrator = actor(
                MutationPermissions.CREATE,
                MutationPermissions.OVERLAP
        );
        Region original = box("overlap", globalKey(), 5, 15);
        RegionSaveOutcome.ConfirmationRequired required = assertInstanceOf(
                RegionSaveOutcome.ConfirmationRequired.class,
                service.attemptSave(
                        createRequest("draft", original, 3L, null), administrator
                )
        );

        Region changed = box("overlap", globalKey(), 6, 16);
        RegionSaveOutcome.Rejected rejected = assertInstanceOf(
                RegionSaveOutcome.Rejected.class,
                service.attemptSave(
                        createRequest(
                                "draft", changed, 3L,
                                required.options().get(0).token()
                        ),
                        administrator
                )
        );

        assertEquals(RegionSaveRejection.CONFIRMATION_BINDING_MISMATCH,
                rejected.reason());
        assertEquals(1, confirmations.size());
        assertEquals(0, port.commits);
    }

    @Test
    void confirmationReevaluatesCurrentOverlapPermission(){
        Region peer = box("peer", globalKey(), 0, 10);
        FakePort port = port(6L, RegionRecord.coreOnly(peer));
        ConfirmationStore confirmations = new ConfirmationStore();
        RegionMutationService service = service(port, confirmations);
        Region candidate = box("overlap", globalKey(), 5, 15);
        RegionSaveOutcome.ConfirmationRequired required = assertInstanceOf(
                RegionSaveOutcome.ConfirmationRequired.class,
                service.attemptSave(
                        createRequest("permission", candidate, 6L, null),
                        actor(
                                MutationPermissions.CREATE,
                                MutationPermissions.OVERLAP
                        )
                )
        );

        RegionSaveOutcome.Stale stale = assertInstanceOf(
                RegionSaveOutcome.Stale.class,
                service.attemptSave(
                        createRequest(
                                "permission", candidate, 6L,
                                required.options().get(0).token()
                        ),
                        actor(MutationPermissions.CREATE)
                )
        );

        assertEquals(StaleReason.PLACEMENT_CHANGED, stale.reason());
        assertEquals(0, confirmations.size());
        assertEquals(0, port.commits);
    }

    @Test
    void editPreservesTargetAndUnchangedRegionModuleAttachments(){
        Region target = box("target", globalKey(), 0, 10);
        Region other = box("other", globalKey(), 30, 40);
        RegionMusicProfile targetMusic = musicProfile("target");
        RegionCommandProfile targetCommands = commandProfile("target");
        RegionMusicProfile otherMusic = musicProfile("other");
        RegionCommandProfile otherCommands = commandProfile("other");
        RegionRecord targetRecord = new RegionRecord(
                target, targetMusic, targetCommands
        );
        RegionRecord otherRecord = new RegionRecord(
                other, otherMusic, otherCommands
        );
        FakePort port = port(11L, targetRecord, otherRecord);
        RegionMutationService service = service(port);
        Region edited = box("target", globalKey(), 0, 12);

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service.attemptSave(
                        editRequest("edit", target, edited, 11L),
                        actor(MutationPermissions.EDIT_ANY)
                )
        );

        RegionRecord publishedTarget = saved.snapshot().records().get(target.key());
        RegionRecord publishedOther = saved.snapshot().records().get(other.key());
        assertSame(targetMusic, publishedTarget.music());
        assertSame(targetCommands, publishedTarget.commands());
        assertSame(otherMusic, publishedOther.music());
        assertSame(otherCommands, publishedOther.commands());
        assertEquals(RegionFingerprints.region(edited),
                RegionFingerprints.region(publishedTarget.region()));
    }

    @Test
    void editRejectsAChangedTargetFingerprintWithoutCommit(){
        Region target = box("target", globalKey(), 0, 10);
        FakePort port = port(5L, RegionRecord.coreOnly(target));
        RegionMutationService service = service(port);
        Region edited = box("target", globalKey(), 0, 12);
        RegionSaveRequest request = new RegionSaveRequest(
                "target-stale",
                SaveMode.EDIT,
                edited,
                5L,
                Optional.of(RegionFingerprints.region(
                        box("target", globalKey(), 1, 10)
                )),
                Optional.empty()
        );

        RegionSaveOutcome.Stale stale = assertInstanceOf(
                RegionSaveOutcome.Stale.class,
                service.attemptSave(
                        request, actor(MutationPermissions.EDIT_ANY)
                )
        );

        assertEquals(StaleReason.TARGET_CHANGED, stale.reason());
        assertEquals(0, port.commits);
    }

    @Test
    void commitRevisionRaceReturnsStaleAndDoesNotPublish(){
        FakePort port = port(12L);
        port.conflict = true;
        RegionMutationService service = service(port);

        RegionSaveOutcome.Stale stale = assertInstanceOf(
                RegionSaveOutcome.Stale.class,
                service.attemptSave(
                        createRequest(
                                "race",
                                box("candidate", globalKey(), 20, 30),
                                12L,
                                null
                        ),
                        actor(MutationPermissions.CREATE)
                )
        );

        assertEquals(StaleReason.COMMIT_RACE, stale.reason());
        assertEquals(12L, port.currentSnapshot().revision());
        assertEquals(0, port.commits);
    }

    @Test
    void persistenceFailureReturnsFailedAndLeavesSnapshotUntouched(){
        FakePort port = port(14L);
        IOException failure = new IOException("disk unavailable");
        port.failure = failure;
        RegionMutationService service = service(port);

        RegionSaveOutcome.Failed failed = assertInstanceOf(
                RegionSaveOutcome.Failed.class,
                service.attemptSave(
                        createRequest(
                                "failure",
                                box("candidate", globalKey(), 20, 30),
                                14L,
                                null
                        ),
                        actor(MutationPermissions.CREATE)
                )
        );

        assertSame(failure, failed.cause());
        assertEquals(14L, port.currentSnapshot().revision());
        assertEquals(0, port.commits);
    }

    @Test
    void concurrentAttemptsAgainstOneRevisionPublishAtMostOne() throws Exception {
        FakePort port = port(20L);
        RegionMutationService service = service(port);
        RegionMutationActor actor = actor(MutationPermissions.CREATE);

        RegionSaveOutcome first;
        RegionSaveOutcome second;
        try(ExecutorService executor = Executors.newFixedThreadPool(2)){
            CompletionStage<RegionSaveOutcome> firstStage = service.attemptSaveAsync(
                    createRequest(
                            "first", box("first", globalKey(), 10, 20),
                            20L, null
                    ),
                    actor,
                    executor
            );
            CompletionStage<RegionSaveOutcome> secondStage = service.attemptSaveAsync(
                    createRequest(
                            "second", box("second", globalKey(), 30, 40),
                            20L, null
                    ),
                    actor,
                    executor
            );
            first = firstStage.toCompletableFuture().get();
            second = secondStage.toCompletableFuture().get();
        }

        long saved = List.of(first, second).stream()
                .filter(RegionSaveOutcome.Saved.class::isInstance)
                .count();
        long stale = List.of(first, second).stream()
                .filter(RegionSaveOutcome.Stale.class::isInstance)
                .count();
        assertEquals(1L, saved);
        assertEquals(1L, stale);
        assertEquals(1, port.commits);
        assertEquals(21L, port.currentSnapshot().revision());
    }

    @Test
    void createPermissionIsCheckedBeforePlacementOrCommit(){
        FakePort port = port(1L);
        RegionMutationService service = service(port);

        RegionSaveOutcome.Rejected rejected = assertInstanceOf(
                RegionSaveOutcome.Rejected.class,
                service.attemptSave(
                        createRequest(
                                "denied",
                                box("candidate", globalKey(), 20, 30),
                                1L,
                                null
                        ),
                        actor()
                )
        );

        assertEquals(RegionSaveRejection.PERMISSION_DENIED,
                rejected.reason());
        assertEquals(0, port.commits);
    }

    @Test
    void editOwnPermissionIsInheritedFromFiniteAncestors(){
        Region parent = ownedBox("parent", globalKey(), 0, 100, PLAYER);
        Region child = box("child", parent.key(), 10, 20);
        FakePort port = port(
                5L,
                RegionRecord.coreOnly(parent),
                RegionRecord.coreOnly(child)
        );
        Region changed = Region.builder(child.key(), child.shape())
                .priority(7)
                .parent(parent.key())
                .build();

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service(port).attemptSave(
                        editRequest("inherited-owner", child, changed, 5L),
                        actor(MutationPermissions.EDIT_OWN)
                )
        );

        assertEquals(7, saved.region().priority());
        assertEquals(1, port.commits);
    }

    @Test
    void everyFlagAdditionChangeAndRemovalRequiresItsPermission(){
        Region base = box("target", globalKey(), 0, 10);
        Region denied = withPvp(base, State.DENY);
        Region allowed = withPvp(base, State.ALLOW);

        assertFlagMutationDenied(base, denied);
        assertFlagMutationDenied(denied, allowed);
        assertFlagMutationDenied(denied, base);
    }

    @Test
    void allowPlayerRegionsCannotUseTheGenericFlagPermissionButAdminCan(){
        Region base = box("target", globalKey(), 0, 10);
        Region changed = withAllowPlayerRegions(base);
        FakePort deniedPort = port(8L, RegionRecord.coreOnly(base));

        RegionSaveOutcome.Rejected denied = assertInstanceOf(
                RegionSaveOutcome.Rejected.class,
                service(deniedPort).attemptSave(
                        editRequest("flag-admin-only", base, changed, 8L),
                        actor(
                                MutationPermissions.EDIT_ANY,
                                "rookieregions.region.flag"
                        )
                )
        );
        assertEquals(RegionSaveRejection.PERMISSION_DENIED, denied.reason());
        assertEquals(0, deniedPort.commits);

        FakePort adminPort = port(8L, RegionRecord.coreOnly(base));
        assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service(adminPort).attemptSave(
                        editRequest("flag-admin-only", base, changed, 8L),
                        actor(MutationPermissions.ADMIN)
                )
        );
        assertEquals(1, adminPort.commits);
    }

    @Test
    void administratorCanPersistAnInPlaceGlobalFlagUpdate(){
        FakePort port = port(3L);
        Region original = port.currentSnapshot().graph()
                .global(world).orElseThrow();
        Region changed = Region.builder(original.key(), GlobalShape.INSTANCE)
                .priority(original.priority())
                .owners(original.owners())
                .members(original.members())
                .flag(ProtectionFlags.PVP, State.DENY)
                .build();

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service(port).attemptSave(
                        editRequest("global", original, changed, 3L),
                        actor(
                                MutationPermissions.EDIT_ANY,
                                "rookieregions.region.flag"
                        )
                )
        );

        assertEquals(State.DENY, saved.region()
                .flag(ProtectionFlags.PVP).orElseThrow().value());
        assertEquals(4L, saved.snapshot().revision());
    }

    @Test
    void administratorCanMaterializeAnUnchangedSynthesizedGlobal(){
        FakePort port = port(12L);
        Region global = port.currentSnapshot().graph()
                .global(world).orElseThrow();

        RegionSaveOutcome.Saved saved = assertInstanceOf(
                RegionSaveOutcome.Saved.class,
                service(port).attemptSave(
                        editRequest("global-command", global, global, 12L),
                        actor(MutationPermissions.EDIT_ANY)
                )
        );

        assertSame(global, saved.region());
        assertEquals(13L, saved.snapshot().revision());
        assertEquals(1, port.commits);
    }

    @Test
    void administrativeModuleUpdateAndDeleteUseCanonicalPublications(){
        Region region = box("published", globalKey(), 0, 10);
        FakePort port = port(20L, RegionRecord.coreOnly(region));
        RegionMutationService service = service(port);
        List<RegionMutationPublication> publications = new ArrayList<>();
        service.addPublicationListener(publications::add);

        AdministrationResult music = service.updateMusic(
                region.key(), ignored -> musicProfile("current"),
                actor(MutationPermissions.MUSIC)
        );
        AdministrationResult deleted = service.delete(
                region.key(), actor(MutationPermissions.DELETE)
        );

        assertEquals(AdministrationStatus.SAVED, music.status());
        assertEquals(AdministrationStatus.SAVED, deleted.status());
        assertEquals(2, publications.size());
        RegionMutationPublication updated = publications.get(0);
        assertEquals(SaveMode.EDIT, updated.mode());
        assertTrue(updated.previousRegion().isPresent());
        assertTrue(updated.currentRegion().isPresent());
        assertEquals(20L, updated.previousSnapshot().revision());
        assertEquals(21L, updated.currentSnapshot().revision());
        RegionMutationPublication removed = publications.get(1);
        assertEquals(SaveMode.DELETE, removed.mode());
        assertTrue(removed.previousRegion().isPresent());
        assertTrue(removed.currentRegion().isEmpty());
        assertEquals(21L, removed.previousSnapshot().revision());
        assertEquals(22L, removed.currentSnapshot().revision());
    }

    @Test
    void administrativePermissionsAreEnforcedInsideTheMutationService(){
        Region region = box("secured", globalKey(), 0, 10);
        FakePort port = port(30L, RegionRecord.coreOnly(region));
        RegionMutationService service = service(port);

        AdministrationResult delete = service.delete(region.key(), actor());
        AdministrationResult music = service.updateMusic(
                region.key(), ignored -> musicProfile("denied"), actor()
        );
        AdministrationResult commands = service.updateCommands(
                region.key(), ignored -> commandProfile("denied"), actor()
        );

        assertEquals(AdministrationStatus.PERMISSION_DENIED, delete.status());
        assertEquals(AdministrationStatus.PERMISSION_DENIED, music.status());
        assertEquals(AdministrationStatus.PERMISSION_DENIED, commands.status());
        assertEquals(0, port.commits);
    }

    @Test
    void coreAdministrationRetainsEditAndFlagAuthorization(){
        Region region = box("core", globalKey(), 0, 10);
        FakePort port = port(40L, RegionRecord.coreOnly(region));
        RegionMutationService service = service(port);

        AdministrationResult deniedEdit = service.updateCore(
                region.key(), source -> withPvp(source, State.DENY), actor()
        );
        AdministrationResult deniedFlag = service.updateCore(
                region.key(), source -> withPvp(source, State.DENY),
                actor(MutationPermissions.EDIT_ANY)
        );
        AdministrationResult saved = service.updateCore(
                region.key(), source -> withPvp(source, State.DENY),
                actor(
                        MutationPermissions.EDIT_ANY,
                        ProtectionFlags.PVP.modificationPermission()
                )
        );

        assertEquals(AdministrationStatus.PERMISSION_DENIED, deniedEdit.status());
        assertEquals(AdministrationStatus.PERMISSION_DENIED, deniedFlag.status());
        assertEquals(AdministrationStatus.SAVED, saved.status());
        assertEquals(1, port.commits);
    }

    @Test
    void runtimeCandidateValidatorRejectsUnknownMusicBeforeCommit(){
        Region region = box("music-validation", globalKey(), 0, 10);
        FakePort port = port(41L, RegionRecord.coreOnly(region));
        RegionMutationService service = new RegionMutationService(
                port,
                new PlacementPolicy(),
                new ConfirmationStore(),
                Runnable::run,
                snapshot -> {
                    boolean unknown = snapshot.records().values().stream()
                            .anyMatch(record -> record.music().getChannels()
                                    .containsKey("unknown"));
                    if(unknown) {
                        throw new IllegalArgumentException(
                                "unknown music channel: unknown"
                        );
                    }
                }
        );

        AdministrationResult result = service.updateMusic(
                region.key(), ignored -> musicProfile("unknown"),
                actor(MutationPermissions.MUSIC)
        );

        assertEquals(AdministrationStatus.INVALID, result.status());
        assertEquals(0, port.commits);
        assertTrue(port.snapshot.records().get(region.key()).music().isEmpty());
    }

    @Test
    void publicMutationApiDeletesAsynchronouslyWithSubjectPermissions() {
        Region region = box("api-delete", globalKey(), 0, 10);
        FakePort port = port(45L, RegionRecord.coreOnly(region));
        RegionMutationApi api = service(port);

        RegionDeleteOutcome outcome = api.delete(
                region.key(),
                new Subject(
                        PLAYER,
                        List.of(),
                        List.of(MutationPermissions.DELETE)
                )
        ).toCompletableFuture().join();

        assertEquals(RegionDeleteStatus.DELETED, outcome.status());
        assertEquals(region.key(), outcome.deletedRegion().orElseThrow().key());
        assertEquals(46L, outcome.snapshot().orElseThrow().revision());
    }

    @Test
    void stagedReloadAtomicallyInstallsItsReplacementValidator() throws Exception {
        Region region = box("reload-validation", globalKey(), 0, 10);
        FakePort port = port(42L, RegionRecord.coreOnly(region));
        RegionMutationService service = service(port);
        ArrayList<RegionRecord> staged = new ArrayList<>(
                port.snapshot.records().values()
        );

        RegionSnapshot published = service.publishStagedReload(
                42L,
                staged,
                snapshot -> {
                    boolean oldChannel = snapshot.records().values().stream()
                            .anyMatch(record -> record.music().getChannels()
                                    .containsKey("old"));
                    if(oldChannel) {
                        throw new IllegalArgumentException(
                                "old channel is no longer configured"
                        );
                    }
                }
        );
        AdministrationResult staleConfigWrite = service.updateMusic(
                region.key(), ignored -> musicProfile("old"),
                actor(MutationPermissions.MUSIC)
        );

        assertEquals(43L, published.revision());
        assertEquals(1, port.reloads);
        assertEquals(AdministrationStatus.INVALID, staleConfigWrite.status());
        assertEquals(0, port.commits);
    }

    private RegionMutationService service(FakePort port){
        return service(port, new ConfirmationStore());
    }

    private RegionMutationService service(FakePort port,
                                          ConfirmationStore confirmations){
        return new RegionMutationService(
                port, new PlacementPolicy(), confirmations
        );
    }

    private RegionSaveRequest createRequest(String session,
                                            Region candidate,
                                            long revision,
                                            String token){
        return new RegionSaveRequest(
                session,
                SaveMode.CREATE,
                candidate,
                revision,
                Optional.empty(),
                Optional.ofNullable(token)
        );
    }

    private RegionSaveRequest editRequest(String session,
                                          Region original,
                                          Region candidate,
                                          long revision){
        return new RegionSaveRequest(
                session,
                SaveMode.EDIT,
                candidate,
                revision,
                Optional.of(RegionFingerprints.region(original)),
                Optional.empty()
        );
    }

    private RegionMutationActor actor(String... permissions){
        return new RegionMutationActor(
                "actor", PLAYER, List.of(), List.of(permissions)
        );
    }

    private FakePort port(long revision, RegionRecord... locals){
        ArrayList<RegionRecord> records = new ArrayList<>();
        records.add(RegionRecord.coreOnly(global()));
        records.addAll(List.of(locals));
        return new FakePort(RegionSnapshot.ofRecords(revision, records));
    }

    private Region global(){
        return Region.builder(globalKey(), GlobalShape.INSTANCE).build();
    }

    private RegionKey globalKey(){
        return RegionKey.global(world);
    }

    private Region box(String id, RegionKey parent, double min, double max){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .build();
    }

    private Region allowedBox(String id,
                              RegionKey parent,
                              double min,
                              double max){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .flag(ProtectionFlags.ALLOW_PLAYER_REGIONS, State.ALLOW)
                .build();
    }

    private Region ownedBox(String id,
                            RegionKey parent,
                            double min,
                            double max,
                            UUID owner){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .owners(RegionDomain.builder().player(owner).build())
                .build();
    }

    private Region withPvp(Region source, State state){
        Region.Builder builder = copyCore(source);
        if(state != null){
            builder.flag(ProtectionFlags.PVP, state);
        }
        return builder.build();
    }

    private Region withAllowPlayerRegions(Region source){
        return copyCore(source)
                .flag(ProtectionFlags.ALLOW_PLAYER_REGIONS, State.ALLOW)
                .build();
    }

    private Region.Builder copyCore(Region source){
        Region.Builder builder = Region.builder(source.key(), source.shape())
                .priority(source.priority())
                .owners(source.owners())
                .members(source.members());
        source.parent().ifPresent(builder::parent);
        return builder;
    }

    private void assertFlagMutationDenied(Region original, Region changed){
        FakePort port = port(12L, RegionRecord.coreOnly(original));
        RegionSaveOutcome.Rejected denied = assertInstanceOf(
                RegionSaveOutcome.Rejected.class,
                service(port).attemptSave(
                        editRequest("flag-change", original, changed, 12L),
                        actor(MutationPermissions.EDIT_ANY)
                )
        );
        assertEquals(RegionSaveRejection.PERMISSION_DENIED, denied.reason());
        assertEquals(0, port.commits);
    }

    private RegionMusicProfile musicProfile(String id){
        RegionMusicChannel blocked = RegionMusicChannel.builder()
                .policy(MusicPolicyMode.BLOCK)
                .build();
        return new RegionMusicProfile(Map.of(id, blocked));
    }

    private RegionCommandProfile commandProfile(String id){
        return new RegionCommandProfile(
                List.of("say enter-" + id),
                List.of("say leave-" + id)
        );
    }

    private static final class FakePort implements RegionMutationPort {
        private RegionSnapshot snapshot;
        private int commits;
        private int reloads;
        private boolean conflict;
        private Exception failure;

        private FakePort(RegionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public synchronized RegionSnapshot currentSnapshot() {
            return snapshot;
        }

        @Override
        public synchronized RegionSnapshot commit(
                long expectedRevision,
                Collection<RegionRecord> candidateRecords) throws Exception {
            if(failure != null){
                throw failure;
            }
            if(conflict || snapshot.revision() != expectedRevision){
                throw new RevisionConflictException("snapshot changed before CAS");
            }
            RegionSnapshot committed = RegionSnapshot.ofRecords(
                    Math.addExact(expectedRevision, 1L), candidateRecords
            );
            snapshot = committed;
            commits++;
            return committed;
        }

        @Override
        public synchronized RegionSnapshot publishStaged(
                long expectedRevision,
                Collection<RegionRecord> stagedRecords) throws Exception {
            if(failure != null){
                throw failure;
            }
            if(conflict || snapshot.revision() != expectedRevision){
                throw new RevisionConflictException("snapshot changed before CAS");
            }
            snapshot = RegionSnapshot.ofRecords(
                    Math.addExact(expectedRevision, 1L), stagedRecords
            );
            reloads++;
            return snapshot;
        }

        private synchronized void advanceExternally(){
            snapshot = RegionSnapshot.ofRecords(
                    snapshot.revision() + 1L,
                    snapshot.records().values()
            );
        }
    }
}
