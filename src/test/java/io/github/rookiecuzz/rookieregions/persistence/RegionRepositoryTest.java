package io.github.rookiecuzz.rookieregions.persistence;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import io.github.rookiecuzz.rookieregions.runtime.ModuleRegionBinding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionRepositoryTest {
    private static final WorldId WORLD = new WorldId(
            UUID.fromString("40000000-0000-0000-0000-000000000004"),
            "minecraft:overworld"
    );

    @TempDir
    Path temporaryDirectory;

    @Test
    void savesAtCanonicalPathAndLoadsModulesWithSynthesizedGlobal() throws Exception {
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        RegionRecord record = localRecord(9);

        Path stored = repository.save(record);
        RegionSnapshot snapshot = repository.load(23, List.of(WORLD));

        assertEquals(
                temporaryDirectory.toAbsolutePath().normalize()
                        .resolve("worlds")
                        .resolve(WORLD.uuid().toString())
                        .resolve("regions")
                        .resolve("town.json"),
                stored
        );
        assertEquals(23, snapshot.revision());
        assertEquals(2, snapshot.records().size());
        assertEquals(record.commands(), snapshot.records().get(record.region().key()).commands());
        assertEquals(record.music(), snapshot.records().get(record.region().key()).music());

        Region global = snapshot.records().get(RegionKey.global(WORLD)).region();
        assertEquals(GlobalShape.INSTANCE, global.shape());
        assertEquals(Integer.MIN_VALUE, global.priority());
        assertTrue(global.parent().isEmpty());
    }

    @Test
    void emptyWorldDirectoriesStillProduceOneGlobalPerLoadedWorld() throws Exception {
        WorldId nether = new WorldId(
                UUID.fromString("50000000-0000-0000-0000-000000000005"),
                "minecraft:the_nether"
        );
        RegionSnapshot snapshot = new RegionRepository(temporaryDirectory)
                .load(List.of(nether, WORLD), 4);

        assertEquals(2, snapshot.records().size());
        assertTrue(snapshot.records().containsKey(RegionKey.global(WORLD)));
        assertTrue(snapshot.records().containsKey(RegionKey.global(nether)));
    }

    @Test
    void malformedDocumentAbortsTheWholeStagingLoad() throws Exception {
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        RegionRecord valid = localRecord(1);
        repository.save(valid);
        RegionSnapshot previouslyPublished = repository.load(1, List.of(WORLD));

        Path broken = repository.pathFor(new RegionKey(WORLD, "broken"));
        Files.writeString(
                broken,
                "{\"schemaVersion\":1,\"schemaVersion\":1}",
                StandardCharsets.UTF_8
        );

        RegionLoadException exception = assertThrows(
                RegionLoadException.class,
                () -> repository.load(2, List.of(WORLD))
        );
        assertEquals(broken, exception.source());
        assertEquals("/schemaVersion", exception.pointer());
        assertEquals(1, previouslyPublished.revision());
        assertEquals(2, previouslyPublished.records().size());
        assertNotNull(previouslyPublished.records().get(valid.region().key()));
    }

    @Test
    void stagesDocumentsForWorldsThatAreNotCurrentlyLoaded() throws Exception {
        WorldId unloaded = new WorldId(
                UUID.fromString("70000000-0000-0000-0000-000000000007"),
                "custom:unloaded"
        );
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        RegionRecord record = localRecord(unloaded, "archive", 3);
        repository.save(record);

        RegionSnapshot snapshot = repository.load(5, List.of(WORLD));

        RegionRecord loaded = snapshot.records().get(record.region().key());
        assertNotNull(loaded);
        assertEquals(record.region().key(), loaded.region().key());
        assertEquals(record.region().priority(), loaded.region().priority());
        assertEquals(record.music(), loaded.music());
        assertEquals(record.commands(), loaded.commands());
        assertTrue(snapshot.records().containsKey(RegionKey.global(unloaded)));
        assertTrue(snapshot.records().containsKey(RegionKey.global(WORLD)));
    }

    @Test
    void malformedDocumentInUnloadedWorldAbortsCompleteStaging() throws Exception {
        WorldId unloaded = new WorldId(
                UUID.fromString("80000000-0000-0000-0000-000000000008"),
                "custom:unloaded"
        );
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        Path broken = repository.pathFor(new RegionKey(unloaded, "broken"));
        Files.createDirectories(broken.getParent());
        Files.writeString(
                broken,
                "{\"schemaVersion\":1,\"schemaVersion\":1}",
                StandardCharsets.UTF_8
        );

        RegionLoadException exception = assertThrows(
                RegionLoadException.class,
                () -> repository.load(1, List.of(WORLD))
        );

        assertEquals(broken, exception.source());
        assertEquals("/schemaVersion", exception.pointer());
    }

    @Test
    void duplicateExternalModuleTargetsAbortCompleteStaging() throws Exception {
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        ModuleRegionBinding target = ModuleRegionBinding.toProvider(
                "worldguard",
                "shared"
        );
        RegionRecord first = localRecord(WORLD, "first", 0);
        RegionRecord second = localRecord(WORLD, "second", 0);
        repository.save(new RegionRecord(
                first.region(),
                first.music().withBinding(target),
                first.commands()
        ));
        repository.save(new RegionRecord(
                second.region(),
                second.music().withBinding(target),
                second.commands()
        ));

        RegionLoadException failure = assertThrows(
                RegionLoadException.class,
                () -> repository.load(1, List.of(WORLD))
        );
        assertTrue(failure.getMessage().contains("duplicate music module binding"));
    }

    @Test
    void refreshesReadableWorldMetadataAndRejectsFilenameMismatch() throws Exception {
        RegionDocumentCodec codec = new RegionDocumentCodec();
        RegionRepository repository = new RegionRepository(temporaryDirectory, codec);
        Path expectedDirectory = repository.pathFor(new RegionKey(WORLD, "town")).getParent();
        Files.createDirectories(expectedDirectory);

        WorldId wrongKey = new WorldId(WORLD.uuid(), "minecraft:different");
        Files.writeString(
                expectedDirectory.resolve("town.json"),
                codec.encodeToString(localRecord(wrongKey, "town", 0)),
                StandardCharsets.UTF_8
        );
        RegionSnapshot refreshed = repository.load(1, List.of(WORLD));
        RegionRecord refreshedRecord = refreshed.records().get(
                new RegionKey(WORLD, "town")
        );
        assertNotNull(refreshedRecord);
        assertEquals(WORLD, refreshedRecord.region().key().world());
        assertEquals(
                WORLD.namespacedKey(),
                refreshedRecord.region().key().world().namespacedKey()
        );

        Files.writeString(
                expectedDirectory.resolve("town.json"),
                codec.encodeToString(localRecord(0)),
                StandardCharsets.UTF_8
        );
        Files.move(
                expectedDirectory.resolve("town.json"),
                expectedDirectory.resolve("renamed.json")
        );
        RegionLoadException filenameError = assertThrows(
                RegionLoadException.class,
                () -> repository.load(1, List.of(WORLD))
        );
        assertEquals("/id", filenameError.pointer());
    }

    @Test
    void rejectsNonDocumentNodesInsteadOfPublishingPartialStaging() throws Exception {
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        Path regions = repository.pathFor(new RegionKey(WORLD, "town")).getParent();
        Files.createDirectories(regions.resolve("not-a-document.json"));

        RegionLoadException documentError = assertThrows(
                RegionLoadException.class,
                () -> repository.load(1, List.of(WORLD))
        );
        assertEquals(regions.resolve("not-a-document.json"), documentError.source());

        Path otherRoot = temporaryDirectory.resolve("other");
        Path worlds = otherRoot.resolve("worlds");
        Files.createDirectories(worlds);
        Path invalidWorldNode = worlds.resolve(
                "90000000-0000-0000-0000-000000000009"
        );
        Files.writeString(invalidWorldNode, "not a directory", StandardCharsets.UTF_8);

        RegionLoadException worldError = assertThrows(
                RegionLoadException.class,
                () -> new RegionRepository(otherRoot).load(1, List.of(WORLD))
        );
        assertEquals(invalidWorldNode, worldError.source());
        assertEquals("/world/uuid", worldError.pointer());

        Path misplacedRoot = temporaryDirectory.resolve("misplaced");
        Path worldDirectory = misplacedRoot.resolve("worlds")
                .resolve(WORLD.uuid().toString());
        Files.createDirectories(worldDirectory);
        Path misplaced = worldDirectory.resolve("typo.json");
        Files.writeString(misplaced, "{}", StandardCharsets.UTF_8);

        RegionLoadException misplacedError = assertThrows(
                RegionLoadException.class,
                () -> new RegionRepository(misplacedRoot).load(1, List.of(WORLD))
        );
        assertEquals(misplaced, misplacedError.source());
    }

    @Test
    void rejectsDocumentWorldUuidThatDisagreesWithDirectory() throws Exception {
        RegionDocumentCodec codec = new RegionDocumentCodec();
        RegionRepository repository = new RegionRepository(temporaryDirectory, codec);
        Path target = repository.pathFor(new RegionKey(WORLD, "town"));
        Files.createDirectories(target.getParent());
        WorldId other = new WorldId(
                UUID.fromString("60000000-0000-0000-0000-000000000006"),
                WORLD.namespacedKey()
        );
        Files.writeString(
                target,
                codec.encodeToString(localRecord(other, "town", 0)),
                StandardCharsets.UTF_8
        );

        RegionLoadException exception = assertThrows(
                RegionLoadException.class,
                () -> repository.load(1, List.of(WORLD))
        );
        assertEquals("/world/uuid", exception.pointer());
    }

    @Test
    void atomicMoveFailureDoesNotFallBackOrReplaceOldDocument() throws Exception {
        RegionDocumentCodec codec = new RegionDocumentCodec();
        RegionRepository baseline = new RegionRepository(temporaryDirectory, codec);
        Path target = baseline.save(localRecord(1));
        String original = Files.readString(target);
        AtomicInteger attempts = new AtomicInteger();

        RegionRepository failing = new RegionRepository(
                temporaryDirectory,
                codec,
                (source, destination) -> {
                    attempts.incrementAndGet();
                    assertTrue(Files.exists(source));
                    assertEquals(target, destination);
                    throw new AtomicMoveNotSupportedException(
                            source.toString(), destination.toString(), "test"
                    );
                }
        );

        assertThrows(IOException.class, () -> failing.save(localRecord(2)));
        assertEquals(1, attempts.get());
        assertEquals(original, Files.readString(target));
        try(Stream<Path> files = Files.list(target.getParent())) {
            assertFalse(files.anyMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void successfulSaveAtomicallyReplacesAndDeleteMovesToTrash() throws Exception {
        RegionRepository repository = new RegionRepository(temporaryDirectory);
        Path target = repository.save(localRecord(1));
        String first = Files.readString(target);

        repository.save(localRecord(2));
        String second = Files.readString(target);
        assertFalse(first.equals(second));
        assertTrue(second.contains("\"priority\":2"));

        Path trashed = repository.delete(new RegionKey(WORLD, "town"));
        assertFalse(Files.exists(target));
        assertTrue(Files.isRegularFile(trashed));
        assertEquals(".trash", trashed.getParent().getParent().getFileName().toString());
        assertEquals(WORLD.uuid().toString(), trashed.getParent().getFileName().toString());
        assertEquals(second, Files.readString(trashed));
    }

    private static RegionRecord localRecord(int priority) {
        return localRecord(WORLD, "town", priority);
    }

    private static RegionRecord localRecord(WorldId world,
                                            String id,
                                            int priority) {
        Region region = Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(0, 0, 0, 10, 10, 10)
                )
                .parent(RegionKey.global(world))
                .priority(priority)
                .build();
        return new RegionRecord(
                region,
                RegionMusicProfile.empty(),
                new RegionCommandProfile(List.of("say enter"), List.of("say leave"))
        );
    }
}
