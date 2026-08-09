package com.gitee.niocho.areamusic.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerReplaceRegionMusicTest {
    private static final String WORLD = "world";
    private static final String TARGET_AREA = "spawn";
    private static final String OTHER_AREA = "dungeon";

    @TempDir
    Path dataRoot;

    private final Gson gson = new Gson();

    @Test
    void successfulReplaceUpdatesOnlyTargetFileAndPublishesReplacementArea()
            throws IOException {
        ConfigManager manager = new ConfigManager(null, dataRoot);
        Fixture fixture = prepareFixture(manager);

        List<MusicDto> requested = Arrays.asList(
                track(" day ", " rookie.spawn_day ", 120L),
                track("night", "rookie.spawn_night", 180L)
        );

        manager.replaceRegionMusic(WORLD, TARGET_AREA, requested);

        RegionMusicConfig stored = gson.fromJson(
                new String(Files.readAllBytes(fixture.targetMusic), StandardCharsets.UTF_8),
                RegionMusicConfig.class
        );
        assertEquals(2, stored.getMusic().size());
        assertEquals("day", stored.getMusic().get(0).getId());
        assertEquals("rookie.spawn_day", stored.getMusic().get(0).getSound());
        assertEquals(Long.valueOf(120L), stored.getMusic().get(0).getDuration());
        assertEquals("night", stored.getMusic().get(1).getId());

        // An in-flight runtime snapshot may still own the old AreaDto. It must
        // remain internally consistent while new lookups observe a replacement.
        assertEquals(
                Collections.singletonList(fixture.oldTargetTrackUuid),
                fixture.oldTargetArea.getMusicId()
        );
        AreaDto published = manager.findArea(WORLD, TARGET_AREA);
        assertNotSame(fixture.oldTargetArea, published);
        assertEquals(Arrays.asList(
                ConfigManager.createTrackUuid(WORLD, TARGET_AREA, "day"),
                ConfigManager.createTrackUuid(WORLD, TARGET_AREA, "night")
        ), published.getMusicId());

        assertFalse(manager.getMusics().containsKey(fixture.oldTargetTrackUuid));
        MusicDto day = manager.getMusics().get(
                ConfigManager.createTrackUuid(WORLD, TARGET_AREA, "day")
        );
        assertEquals("day", day.getMusicId());
        assertEquals("rookie.spawn_day", day.getMusicURL());
        assertEquals(Long.valueOf(120L), day.getMusicDuration());
        assertSame(fixture.otherArea, manager.findArea(WORLD, OTHER_AREA));
        assertSame(
                fixture.otherMusic,
                manager.getMusics().get(fixture.otherTrackUuid)
        );
        assertFilesUnchanged(fixture.untouchedFiles);
    }

    @Test
    void duplicateIdsAreRejectedBeforeFileOrMemoryChanges() throws IOException {
        ConfigManager manager = new ConfigManager(null, dataRoot);
        Fixture fixture = prepareFixture(manager);
        byte[] targetBefore = Files.readAllBytes(fixture.targetMusic);
        Map<String, MusicDto> musicsBefore = new HashMap<>(manager.getMusics());

        assertThrows(IOException.class, () -> manager.replaceRegionMusic(
                WORLD,
                TARGET_AREA,
                Arrays.asList(
                        track("duplicate", "rookie.one", 10L),
                        track(" duplicate ", "rookie.two", 20L)
                )
        ));

        assertArrayEquals(targetBefore, Files.readAllBytes(fixture.targetMusic));
        assertSame(fixture.oldTargetArea, manager.findArea(WORLD, TARGET_AREA));
        assertEquals(
                Collections.singletonList(fixture.oldTargetTrackUuid),
                fixture.oldTargetArea.getMusicId()
        );
        assertEquals(musicsBefore, manager.getMusics());
        assertFilesUnchanged(fixture.untouchedFiles);
    }

    @Test
    void writeFailureLeavesTargetFileAndAllRuntimeStateUntouched()
            throws IOException {
        ConfigManager manager = new FailingWriteConfigManager(dataRoot);
        Fixture fixture = prepareFixture(manager);
        byte[] targetBefore = Files.readAllBytes(fixture.targetMusic);
        Map<String, MusicDto> musicsBefore = new HashMap<>(manager.getMusics());
        Map<String, AreaDto> areasBefore = new HashMap<>(manager.getAreas().get(WORLD));

        assertThrows(IOException.class, () -> manager.replaceRegionMusic(
                WORLD,
                TARGET_AREA,
                Collections.singletonList(track("new", "rookie.new", 30L))
        ));

        assertArrayEquals(targetBefore, Files.readAllBytes(fixture.targetMusic));
        assertEquals(areasBefore, manager.getAreas().get(WORLD));
        assertSame(fixture.oldTargetArea, manager.findArea(WORLD, TARGET_AREA));
        assertEquals(musicsBefore, manager.getMusics());
        assertFilesUnchanged(fixture.untouchedFiles);
    }

    private Fixture prepareFixture(ConfigManager manager) throws IOException {
        Path targetDirectory = regionDirectory(TARGET_AREA);
        Path otherDirectory = regionDirectory(OTHER_AREA);
        Files.createDirectories(targetDirectory);
        Files.createDirectories(otherDirectory);

        Path targetArea = write(targetDirectory.resolve("area.json"),
                "{\"channel\":\"bgm\",\"marker\":\"target\"}\n");
        Path targetMusic = write(targetDirectory.resolve("music.json"),
                "{\"music\":[{\"id\":\"old\",\"sound\":\"rookie.old\","
                        + "\"duration\":60}]}\n");
        Path otherArea = write(otherDirectory.resolve("area.json"),
                "{\"channel\":\"ambience\",\"marker\":\"other\"}\n");
        Path otherMusic = write(otherDirectory.resolve("music.json"),
                "{\"music\":[{\"id\":\"keep\",\"sound\":\"rookie.keep\","
                        + "\"duration\":90}]}\n");

        String targetUuid = ConfigManager.createRegionUuid(WORLD, TARGET_AREA);
        String oldTargetTrackUuid = ConfigManager.createTrackUuid(
                WORLD,
                TARGET_AREA,
                "old"
        );
        AreaDto oldTargetArea = area(
                TARGET_AREA,
                targetUuid,
                Collections.singletonList(oldTargetTrackUuid)
        );

        String otherUuid = ConfigManager.createRegionUuid(WORLD, OTHER_AREA);
        String otherTrackUuid = ConfigManager.createTrackUuid(
                WORLD,
                OTHER_AREA,
                "keep"
        );
        AreaDto unrelatedArea = area(
                OTHER_AREA,
                otherUuid,
                Collections.singletonList(otherTrackUuid)
        );
        MusicDto oldTargetMusic = MusicDto.builder()
                .uuid(oldTargetTrackUuid)
                .musicId("old")
                .musicURL("rookie.old")
                .musicDuration(60L)
                .build();
        MusicDto unrelatedMusic = MusicDto.builder()
                .uuid(otherTrackUuid)
                .musicId("keep")
                .musicURL("rookie.keep")
                .musicDuration(90L)
                .build();

        Map<String, AreaDto> worldAreas = new ConcurrentHashMap<>();
        worldAreas.put(targetUuid, oldTargetArea);
        worldAreas.put(otherUuid, unrelatedArea);
        manager.getAreas().put(WORLD, worldAreas);
        manager.getMusics().put(oldTargetTrackUuid, oldTargetMusic);
        manager.getMusics().put(otherTrackUuid, unrelatedMusic);

        Map<Path, byte[]> untouched = new HashMap<>();
        untouched.put(targetArea, Files.readAllBytes(targetArea));
        untouched.put(otherArea, Files.readAllBytes(otherArea));
        untouched.put(otherMusic, Files.readAllBytes(otherMusic));
        return new Fixture(
                targetMusic,
                oldTargetTrackUuid,
                oldTargetArea,
                otherTrackUuid,
                unrelatedArea,
                unrelatedMusic,
                untouched
        );
    }

    private AreaDto area(String areaId, String uuid, List<String> musicUuids) {
        return AreaDto.builder()
                .world(WORLD)
                .uuid(uuid)
                .areaId(areaId)
                .musicId(new CopyOnWriteArrayList<>(musicUuids))
                .channel("bgm")
                .order(0)
                .priority(Priority.NORMAL)
                .random(false)
                .loop(true)
                .enabled(true)
                .overWrite(true)
                .volume(1.0f)
                .pitch(1.0f)
                .build();
    }

    private MusicDto track(String id, String sound, long duration) {
        return MusicDto.builder()
                .uuid("caller-controlled-value-must-not-be-published")
                .musicId(id)
                .musicURL(sound)
                .musicDuration(duration)
                .build();
    }

    private Path regionDirectory(String areaId) {
        return dataRoot.resolve("worlds")
                .resolve(WORLD)
                .resolve("regions")
                .resolve(areaId);
    }

    private Path write(Path path, String value) throws IOException {
        Files.write(path, value.getBytes(StandardCharsets.UTF_8));
        return path;
    }

    private void assertFilesUnchanged(Map<Path, byte[]> expected) throws IOException {
        for(Map.Entry<Path, byte[]> entry : expected.entrySet()){
            assertTrue(Files.isRegularFile(entry.getKey()), entry.getKey().toString());
            assertArrayEquals(
                    entry.getValue(),
                    Files.readAllBytes(entry.getKey()),
                    entry.getKey().toString()
            );
        }
    }

    private static final class FailingWriteConfigManager extends ConfigManager {
        private FailingWriteConfigManager(Path dataRoot) {
            super(null, dataRoot);
        }

        @Override
        void writeJsonAtomically(Path file, Object value) throws IOException {
            throw new IOException("simulated disk failure");
        }
    }

    private static final class Fixture {
        private final Path targetMusic;
        private final String oldTargetTrackUuid;
        private final AreaDto oldTargetArea;
        private final String otherTrackUuid;
        private final AreaDto otherArea;
        private final MusicDto otherMusic;
        private final Map<Path, byte[]> untouchedFiles;

        private Fixture(Path targetMusic,
                        String oldTargetTrackUuid,
                        AreaDto oldTargetArea,
                        String otherTrackUuid,
                        AreaDto otherArea,
                        MusicDto otherMusic,
                        Map<Path, byte[]> untouchedFiles) {
            this.targetMusic = targetMusic;
            this.oldTargetTrackUuid = oldTargetTrackUuid;
            this.oldTargetArea = oldTargetArea;
            this.otherTrackUuid = otherTrackUuid;
            this.otherArea = otherArea;
            this.otherMusic = otherMusic;
            this.untouchedFiles = untouchedFiles;
        }
    }
}
