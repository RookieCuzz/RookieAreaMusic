package io.github.rookiecuzz.rookieregions.persistence;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.persistence.codec.DocumentFormatException;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/** Filesystem persistence for complete, one-file-per-region documents. */
public final class RegionRepository {
    private static final String WORLDS_DIRECTORY = "worlds";
    private static final String REGIONS_DIRECTORY = "regions";
    private static final String TRASH_DIRECTORY = ".trash";

    private final Path root;
    private final RegionDocumentCodec codec;
    private final AtomicMover mover;

    public RegionRepository(Path root) {
        this(root, new RegionDocumentCodec());
    }

    public RegionRepository(Path root, RegionDocumentCodec codec) {
        this(root, codec, RegionRepository::atomicReplace);
    }

    RegionRepository(Path root, RegionDocumentCodec codec, AtomicMover mover) {
        this.root = Objects.requireNonNull(root, "repository root cannot be null")
                .toAbsolutePath()
                .normalize();
        this.codec = Objects.requireNonNull(codec, "region codec cannot be null");
        this.mover = Objects.requireNonNull(mover, "atomic mover cannot be null");
    }

    /**
     * Stages every requested world and publishes one snapshot only after every
     * document and the complete region graph have validated.
     */
    public RegionSnapshot load(long revision,
                               Collection<WorldId> loadedWorlds)
            throws RegionLoadException {
        if(revision < 0L) {
            throw new IllegalArgumentException("snapshot revision cannot be negative");
        }
        List<WorldId> loaded = normalizeWorlds(loadedWorlds);
        Map<UUID, WorldId> loadedByUuid = new LinkedHashMap<>();
        for(WorldId world : loaded) {
            loadedByUuid.put(world.uuid(), world);
        }
        LinkedHashMap<RegionKey, RegionRecord> staged = new LinkedHashMap<>();

        Map<UUID, WorldId> stagedWorlds = new LinkedHashMap<>(loadedByUuid);
        for(Path worldDirectory : listWorldDirectories()) {
            UUID directoryUuid = parseWorldDirectory(worldDirectory);
            validateWorldDirectory(worldDirectory);
            WorldId world = loadedByUuid.get(directoryUuid);
            boolean authoritativeWorldMetadata = world != null;
            Path directory = worldDirectory.resolve(REGIONS_DIRECTORY);
            for(Path document : listDocuments(directory)) {
                RegionRecord record = readDocument(document);
                WorldId documentWorld = record.region().key().world();
                if(!documentWorld.uuid().equals(directoryUuid)) {
                    throw new RegionLoadException(
                            document,
                            "/world/uuid",
                            "document world UUID does not match its directory"
                    );
                }
                if(world == null) {
                    world = documentWorld;
                    stagedWorlds.put(directoryUuid, world);
                } else if(!authoritativeWorldMetadata
                        && !documentWorld.namespacedKey().equals(world.namespacedKey())) {
                    throw new RegionLoadException(
                            document,
                            "/world/key",
                            "documents for world UUID " + directoryUuid
                                    + " disagree on the world key"
                    );
                }
                if(authoritativeWorldMetadata
                        && !documentWorld.namespacedKey().equals(world.namespacedKey())) {
                    record = remapWorldMetadata(record, world);
                }
                validateDocumentFilename(document, record);
                RegionKey key = record.region().key();
                if(staged.putIfAbsent(key, record) != null) {
                    throw new RegionLoadException(
                            document,
                            "/id",
                            "duplicate region ID '" + key.id() + "' in loaded worlds"
                    );
                }
            }
        }
        for(WorldId world : stagedWorlds.values()) {
            RegionKey globalKey = RegionKey.global(world);
            staged.computeIfAbsent(globalKey, ignored -> synthesizedGlobal(world));
        }

        try {
            return RegionSnapshot.ofRecords(revision, staged.values());
        } catch(IllegalArgumentException exception) {
            throw new RegionLoadException(
                    root,
                    "",
                    "staged region graph is invalid: " + exception.getMessage(),
                    exception
            );
        }
    }

    public RegionSnapshot load(Collection<WorldId> loadedWorlds,
                               long revision)
            throws RegionLoadException {
        return load(revision, loadedWorlds);
    }

    /** Writes one complete document through a forced same-directory temp file. */
    public Path save(RegionRecord record) throws IOException {
        Objects.requireNonNull(record, "region record cannot be null");
        String document = codec.encodeToString(record);
        Path target = pathFor(record.region().key());
        Path directory = target.getParent();
        Files.createDirectories(directory);

        Path temporary = Files.createTempFile(
                directory,
                "." + record.region().key().id() + ".",
                ".tmp"
        );
        boolean moved = false;
        try {
            byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
            try(FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING
            )) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes);
                while(buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            mover.move(temporary, target);
            moved = true;
            return target;
        } finally {
            if(!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    /** Moves a region document into the recoverable repository-level trash. */
    public Path delete(RegionKey key) throws IOException {
        Objects.requireNonNull(key, "region key cannot be null");
        Path source = pathFor(key);
        if(!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new NoSuchFileException(source.toString());
        }
        if(!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("region document is not a regular file: " + source);
        }

        Path trash = root.resolve(TRASH_DIRECTORY)
                .resolve(key.world().uuid().toString());
        Files.createDirectories(trash);
        String stamp = Long.toUnsignedString(Instant.now().toEpochMilli());
        Path destination = trash.resolve(
                key.id() + "." + stamp + "." + UUID.randomUUID() + ".json"
        );
        mover.move(source, destination);
        return destination;
    }

    /** Restores a document moved by {@link #delete(RegionKey)} after publication fails. */
    public void restoreDeleted(Path trashed, RegionKey key) throws IOException {
        Objects.requireNonNull(trashed, "trashed document cannot be null");
        Objects.requireNonNull(key, "region key cannot be null");
        Path normalized = trashed.toAbsolutePath().normalize();
        Path trashRoot = root.resolve(TRASH_DIRECTORY).toAbsolutePath().normalize();
        if(!normalized.startsWith(trashRoot)
                || !Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("invalid trashed region document: " + normalized);
        }
        Path target = pathFor(key);
        Files.createDirectories(target.getParent());
        mover.move(normalized, target);
    }

    /** Moves a newly-created document out of the live tree during rollback. */
    public Path discardCreated(RegionKey key) throws IOException {
        return delete(key);
    }

    public boolean documentExists(RegionKey key) {
        return Files.isRegularFile(pathFor(key), LinkOption.NOFOLLOW_LINKS);
    }

    public Path pathFor(RegionKey key) {
        Objects.requireNonNull(key, "region key cannot be null");
        return regionsDirectory(key.world()).resolve(key.id() + ".json");
    }

    public Path root() {
        return root;
    }

    private List<Path> listDocuments(Path directory) throws RegionLoadException {
        if(!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if(!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw new RegionLoadException(directory, "", "regions path is not a directory");
        }
        try(Stream<Path> entries = Files.list(directory)) {
            List<Path> listed = entries
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for(Path path : listed) {
                if(!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                        || !path.getFileName().toString().endsWith(".json")) {
                    throw new RegionLoadException(
                            path,
                            "",
                            "regions directory may contain only regular .json documents"
                    );
                }
            }
            return listed;
        } catch(RegionLoadException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new RegionLoadException(
                    directory,
                    "",
                    "cannot list region documents",
                    exception
            );
        }
    }

    private RegionRecord readDocument(Path document) throws RegionLoadException {
        try(BufferedReader reader = Files.newBufferedReader(document, StandardCharsets.UTF_8)) {
            return codec.decode(reader);
        } catch(DocumentFormatException exception) {
            throw new RegionLoadException(
                    document,
                    exception.pointer(),
                    "invalid region document",
                    exception
            );
        } catch(IOException exception) {
            throw new RegionLoadException(
                    document,
                    "",
                    "cannot read region document",
                    exception
            );
        }
    }

    private static void validateDocumentFilename(Path document,
                                                 RegionRecord record)
            throws RegionLoadException {
        String expectedName = record.region().key().id() + ".json";
        if(!document.getFileName().toString().equals(expectedName)) {
            throw new RegionLoadException(
                    document,
                    "/id",
                    "document ID does not match filename (expected " + expectedName + ")"
            );
        }
    }

    private List<Path> listWorldDirectories() throws RegionLoadException {
        Path worldsRoot = root.resolve(WORLDS_DIRECTORY);
        if(!Files.exists(worldsRoot, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        if(!Files.isDirectory(worldsRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new RegionLoadException(
                    worldsRoot, "", "worlds path is not a directory"
            );
        }
        try(Stream<Path> entries = Files.list(worldsRoot)) {
            List<Path> listed = entries
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for(Path path : listed) {
                if(!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
                    throw new RegionLoadException(
                            path,
                            "/world/uuid",
                            "worlds directory may contain only UUID directories"
                    );
                }
            }
            return listed;
        } catch(RegionLoadException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new RegionLoadException(
                    worldsRoot, "", "cannot list world directories", exception
            );
        }
    }

    private UUID parseWorldDirectory(Path directory) throws RegionLoadException {
        String name = directory.getFileName().toString();
        try {
            UUID uuid = UUID.fromString(name);
            if(!uuid.toString().equals(name)) {
                throw new IllegalArgumentException("world UUID is not canonical");
            }
            return uuid;
        } catch(IllegalArgumentException exception) {
            throw new RegionLoadException(
                    directory,
                    "/world/uuid",
                    "world directory name must be a canonical UUID",
                    exception
            );
        }
    }

    /** A world directory is strict too: only its canonical regions/ child is valid. */
    private void validateWorldDirectory(Path worldDirectory)
            throws RegionLoadException {
        try(Stream<Path> entries = Files.list(worldDirectory)) {
            for(Path entry : entries.toList()) {
                if(!entry.getFileName().toString().equals(REGIONS_DIRECTORY)
                        || !Files.isDirectory(entry, LinkOption.NOFOLLOW_LINKS)) {
                    throw new RegionLoadException(
                            entry,
                            "",
                            "world directory may contain only the regions directory"
                    );
                }
            }
        } catch(RegionLoadException exception) {
            throw exception;
        } catch(IOException exception) {
            throw new RegionLoadException(
                    worldDirectory,
                    "",
                    "cannot list world directory",
                    exception
            );
        }
    }

    /** Paper's namespaced key is metadata; UUID remains the region identity. */
    private static RegionRecord remapWorldMetadata(RegionRecord source,
                                                   WorldId currentWorld) {
        Region previous = source.region();
        Region.Builder builder = Region.builder(
                        new RegionKey(currentWorld, previous.key().id()),
                        previous.shape()
                )
                .priority(previous.priority())
                .owners(previous.owners())
                .members(previous.members());
        previous.parent().ifPresent(parent -> builder.parent(
                new RegionKey(currentWorld, parent.id())
        ));
        previous.flags().values().forEach(builder::flagValue);
        return new RegionRecord(builder.build(), source.music(), source.commands());
    }

    private List<WorldId> normalizeWorlds(Collection<WorldId> loadedWorlds)
            throws RegionLoadException {
        if(loadedWorlds == null) {
            throw new IllegalArgumentException("loaded worlds cannot be null");
        }
        Map<UUID, WorldId> unique = new LinkedHashMap<>();
        for(WorldId world : loadedWorlds) {
            if(world == null) {
                throw new IllegalArgumentException("loaded worlds cannot contain null");
            }
            WorldId previous = unique.putIfAbsent(world.uuid(), world);
            if(previous != null
                    && !previous.namespacedKey().equals(world.namespacedKey())) {
                throw new RegionLoadException(
                        root,
                        "/world/key",
                        "loaded world UUID " + world.uuid()
                                + " has conflicting keys '" + previous.namespacedKey()
                                + "' and '" + world.namespacedKey() + "'"
                );
            }
        }
        ArrayList<WorldId> result = new ArrayList<>(unique.values());
        result.sort(WorldId::compareTo);
        return List.copyOf(result);
    }

    private Path regionsDirectory(WorldId world) {
        return root.resolve(WORLDS_DIRECTORY)
                .resolve(world.uuid().toString())
                .resolve(REGIONS_DIRECTORY);
    }

    private static RegionRecord synthesizedGlobal(WorldId world) {
        Region region = Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .priority(Integer.MIN_VALUE)
                .build();
        return RegionRecord.coreOnly(region);
    }

    private static void atomicReplace(Path source, Path target) throws IOException {
        try {
            Files.move(
                    source,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING
            );
        } catch(AtomicMoveNotSupportedException exception) {
            // Atomicity is part of the repository contract: never fall back.
            throw exception;
        }
    }

    @FunctionalInterface
    interface AtomicMover {
        void move(Path source, Path target) throws IOException;
    }
}
