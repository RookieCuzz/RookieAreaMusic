package io.github.rookiecuzz.rookieregions.persistence;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionContainer;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.Bounds3D;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepositoryMutationPortTest {
    @TempDir
    Path temporary;

    @Test
    void persistsOneRecordBeforePublishingTheNextSnapshot() throws Exception {
        WorldId world = new WorldId(UUID.randomUUID(), "minecraft:test");
        Region global = Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .priority(Integer.MIN_VALUE)
                .build();
        RegionRecord globalRecord = RegionRecord.coreOnly(global);
        RegionContainer container = new RegionContainer(
                RegionSnapshot.ofRecords(0L, List.of(globalRecord))
        );
        RegionRepository repository = new RegionRepository(temporary);
        RepositoryMutationPort port = new RepositoryMutationPort(repository, container);
        Region local = Region.builder(
                        new RegionKey(world, "spawn"),
                        new CuboidShape(new Bounds3D(0, 0, 0, 10, 10, 10))
                )
                .parent(global.key())
                .build();

        RegionSnapshot committed = port.commit(
                0L,
                List.of(globalRecord, RegionRecord.coreOnly(local))
        );

        assertEquals(1L, committed.revision());
        assertTrue(committed.graph().region(local.key()).isPresent());
        assertTrue(Files.isRegularFile(repository.pathFor(local.key())));
        assertEquals(1L, container.snapshot().revision());
    }

    @Test
    void publicationRaceRollsBackNewlyCreatedLiveDocument() throws Exception {
        WorldId world = new WorldId(UUID.randomUUID(), "minecraft:race");
        Region global = Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .priority(Integer.MIN_VALUE)
                .build();
        RegionRecord globalRecord = RegionRecord.coreOnly(global);
        RegionContainer container = new RegionContainer(
                RegionSnapshot.ofRecords(0L, List.of(globalRecord))
        );
        AtomicBoolean raced = new AtomicBoolean();
        RegionRepository repository = new RegionRepository(
                temporary,
                new RegionDocumentCodec(),
                (source, target) -> {
                    if(raced.compareAndSet(false, true)) {
                        container.recordPublication()
                                .compareAndPublish(0L, List.of(globalRecord))
                                .orElseThrow();
                    }
                    Files.move(
                            source,
                            target,
                            StandardCopyOption.ATOMIC_MOVE,
                            StandardCopyOption.REPLACE_EXISTING
                    );
                }
        );
        RepositoryMutationPort port = new RepositoryMutationPort(repository, container);
        Region local = Region.builder(
                        new RegionKey(world, "raced"),
                        new CuboidShape(new Bounds3D(0, 0, 0, 10, 10, 10))
                )
                .parent(global.key())
                .build();

        assertThrows(
                io.github.rookiecuzz.rookieregions.mutation.RevisionConflictException.class,
                () -> port.commit(
                        0L,
                        List.of(globalRecord, RegionRecord.coreOnly(local))
                )
        );

        assertEquals(1L, container.snapshot().revision());
        assertFalse(Files.exists(repository.pathFor(local.key())));
        assertTrue(Files.isDirectory(temporary.resolve(".trash")));
    }
}
