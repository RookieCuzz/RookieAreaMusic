package io.github.rookiecuzz.rookieregions.command;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationPort;
import io.github.rookiecuzz.rookieregions.mutation.ConfirmationStore;
import io.github.rookiecuzz.rookieregions.mutation.MutationPermissions;
import io.github.rookiecuzz.rookieregions.mutation.PlacementPolicy;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationActor;
import io.github.rookiecuzz.rookieregions.mutation.RegionMutationService;
import io.github.rookiecuzz.rookieregions.mutation.RevisionConflictException;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionAdministrationServiceTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000301"),
            "minecraft:overworld"
    );

    @Test
    void setParentValidatesWholeGraphAndPreservesAttachments(){
        Region first = box("first", globalKey(), 0, 100);
        Region second = box("second", globalKey(), -10, 110);
        Region child = box("child", first.key(), 10, 20);
        RegionMusicProfile music = blockedMusic();
        RegionCommandProfile commands = new RegionCommandProfile(
                List.of("say enter"), List.of("say leave")
        );
        FakePort port = port(
                record(first), record(second),
                new RegionRecord(child, music, commands)
        );
        RegionAdministrationService service = service(port);

        AdministrationResult result = service.setParent(
                child.key(), second.key(), actor(MutationPermissions.EDIT_ANY)
        ).toCompletableFuture().join();

        assertEquals(AdministrationStatus.SAVED, result.status());
        RegionRecord changed = result.currentRecord().orElseThrow();
        assertEquals(second.key(), changed.region().parent().orElseThrow());
        assertSame(music, changed.music());
        assertSame(commands, changed.commands());
        assertEquals(1, port.commits);
    }

    @Test
    void invalidParentContainmentDoesNotReachPort(){
        Region first = box("first", globalKey(), 0, 100);
        Region far = box("far", globalKey(), 200, 300);
        Region child = box("child", first.key(), 10, 20);
        FakePort port = port(record(first), record(far), record(child));

        AdministrationResult result = service(port).setParent(
                child.key(), far.key(), actor(MutationPermissions.EDIT_ANY)
        ).toCompletableFuture().join();

        assertEquals(AdministrationStatus.INVALID, result.status());
        assertEquals(0, port.commits);
        assertEquals(first.key(), port.snapshot.records().get(child.key())
                .region().parent().orElseThrow());
    }

    @Test
    void deleteForbidsGlobalAndRegionsWithChildren(){
        Region parent = box("parent", globalKey(), 0, 100);
        Region child = box("child", parent.key(), 10, 20);
        FakePort port = port(record(parent), record(child));
        RegionAdministrationService service = service(port);

        AdministrationResult global = service.delete(
                        globalKey(), actor(MutationPermissions.DELETE)
                )
                .toCompletableFuture().join();
        AdministrationResult hasChildren = service.delete(
                        parent.key(), actor(MutationPermissions.DELETE)
                )
                .toCompletableFuture().join();

        assertEquals(AdministrationStatus.GLOBAL_FORBIDDEN, global.status());
        assertEquals(AdministrationStatus.HAS_CHILDREN, hasChildren.status());
        assertEquals(0, port.commits);
    }

    @Test
    void deleteLeafPublishesRecordRemoval(){
        Region leaf = box("leaf", globalKey(), 0, 10);
        FakePort port = port(record(leaf));

        AdministrationResult result = service(port).delete(
                        leaf.key(), actor(MutationPermissions.DELETE)
                )
                .toCompletableFuture().join();

        assertEquals(AdministrationStatus.SAVED, result.status());
        assertTrue(result.currentRecord().isEmpty());
        assertFalse(result.currentSnapshot().records().containsKey(leaf.key()));
    }

    @Test
    void musicUpdateChangesOnlyMusicAttachment(){
        Region region = box("music", globalKey(), 0, 10);
        RegionCommandProfile commands = new RegionCommandProfile(
                List.of("say enter"), List.of()
        );
        RegionRecord source = new RegionRecord(
                region, RegionMusicProfile.empty(), commands
        );
        FakePort port = port(source);
        RegionMusicProfile music = blockedMusic();

        AdministrationResult result = service(port).updateMusic(
                region.key(), ignored -> music,
                actor(MutationPermissions.MUSIC)
        ).toCompletableFuture().join();

        RegionRecord changed = result.currentRecord().orElseThrow();
        assertSame(region, changed.region());
        assertSame(commands, changed.commands());
        assertSame(music, changed.music());
    }

    @Test
    void commitRaceAndPersistenceFailureAreDistinguished(){
        Region region = box("music", globalKey(), 0, 10);
        FakePort race = port(record(region));
        race.conflict = true;
        AdministrationResult stale = service(race).updateMusic(
                region.key(), ignored -> blockedMusic(),
                actor(MutationPermissions.MUSIC)
        ).toCompletableFuture().join();

        FakePort failed = port(record(region));
        failed.failure = new IOException("disk unavailable");
        AdministrationResult failure = service(failed).updateMusic(
                region.key(), ignored -> blockedMusic(),
                actor(MutationPermissions.MUSIC)
        ).toCompletableFuture().join();

        assertEquals(AdministrationStatus.STALE, stale.status());
        assertEquals(AdministrationStatus.FAILED, failure.status());
        assertEquals(0, race.commits);
        assertEquals(0, failed.commits);
    }

    private RegionAdministrationService service(FakePort port){
        return new RegionAdministrationService(
                new RegionMutationService(
                        port, new PlacementPolicy(), new ConfirmationStore()
                ),
                Runnable::run
        );
    }

    private RegionMutationActor actor(String... permissions){
        return new RegionMutationActor(
                "administrator", null, List.of(), List.of(permissions)
        );
    }

    private FakePort port(RegionRecord... locals){
        ArrayList<RegionRecord> records = new ArrayList<>();
        records.add(record(Region.builder(
                globalKey(), GlobalShape.INSTANCE
        ).build()));
        records.addAll(List.of(locals));
        return new FakePort(RegionSnapshot.ofRecords(5L, records));
    }

    private RegionRecord record(Region region){
        return RegionRecord.coreOnly(region);
    }

    private Region box(String id, RegionKey parent, double min, double max){
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .build();
    }

    private RegionKey globalKey(){
        return RegionKey.global(world);
    }

    private RegionMusicProfile blockedMusic(){
        return new RegionMusicProfile(Map.of(
                "current",
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .build()
        ));
    }

    private static final class FakePort implements RegionMutationPort {
        private RegionSnapshot snapshot;
        private boolean conflict;
        private Exception failure;
        private int commits;

        private FakePort(RegionSnapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public RegionSnapshot currentSnapshot() {
            return snapshot;
        }

        @Override
        public RegionSnapshot commit(
                long expectedRevision,
                Collection<RegionRecord> candidateRecords) throws Exception {
            if(failure != null){
                throw failure;
            }
            if(conflict || snapshot.revision() != expectedRevision){
                throw new RevisionConflictException("CAS conflict");
            }
            snapshot = RegionSnapshot.ofRecords(
                    expectedRevision + 1L, candidateRecords
            );
            commits++;
            return snapshot;
        }
    }
}
