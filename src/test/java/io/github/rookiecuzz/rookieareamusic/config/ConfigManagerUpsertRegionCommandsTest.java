package io.github.rookiecuzz.rookieareamusic.config;

import io.github.rookiecuzz.rookieareamusic.geometry.SlicedPolygonVolume;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigManagerUpsertRegionCommandsTest {
    private static final String WORLD = "world";
    private static final String AREA = "boss_gate";

    @TempDir
    Path dataRoot;

    private final Gson gson = new Gson();

    @Test
    void editAtomicallyWritesNormalizedCommandsAndPreservesMusic()
            throws IOException {
        ConfigManager manager = new ConfigManager(null, dataRoot);
        Fixture fixture = prepareFixture(manager);
        List<String> callerEnterCommands = new ArrayList<>(Arrays.asList(
                "  /say welcome %player%  ",
                "effect give %player% minecraft:glowing 10 0 true"
        ));
        List<String> callerExitCommands = new ArrayList<>(Arrays.asList(
                "  /tag %player% remove boss_gate  ",
                "effect clear %player% minecraft:glowing"
        ));
        AreaDto updated = area(callerEnterCommands, callerExitCommands);

        manager.upsertRegion(updated, false);

        RegionAreaConfig stored = gson.fromJson(
                new String(
                        Files.readAllBytes(fixture.areaFile),
                        StandardCharsets.UTF_8
                ),
                RegionAreaConfig.class
        );
        List<String> expectedEnter = Arrays.asList(
                "say welcome %player%",
                "effect give %player% minecraft:glowing 10 0 true"
        );
        List<String> expectedExit = Arrays.asList(
                "tag %player% remove boss_gate",
                "effect clear %player% minecraft:glowing"
        );
        assertEquals(expectedEnter, stored.getEnterCommands());
        assertEquals(expectedExit, stored.getExitCommands());
        assertArrayEquals(
                fixture.musicBefore,
                Files.readAllBytes(fixture.musicFile)
        );

        AreaDto published = manager.findArea(WORLD, AREA);
        assertSame(updated, published);
        assertEquals(expectedEnter, published.getEnterCommands());
        assertEquals(expectedExit, published.getExitCommands());
        assertTrue(published.getEnterCommands() instanceof CopyOnWriteArrayList);
        assertTrue(published.getExitCommands() instanceof CopyOnWriteArrayList);
        callerEnterCommands.add("say caller mutation");
        callerExitCommands.add("say caller mutation");
        assertEquals(expectedEnter, published.getEnterCommands());
        assertEquals(expectedExit, published.getExitCommands());

        assertNotSame(fixture.original, published);
        assertEquals(
                Collections.singletonList("say old %player%"),
                fixture.original.getEnterCommands()
        );
        assertEquals(
                Collections.singletonList("tag %player% remove old"),
                fixture.original.getExitCommands()
        );
    }

    @Test
    void areaWriteFailureLeavesFileMusicAndPublishedAreaUntouched()
            throws IOException {
        ConfigManager manager = new FailingWriteConfigManager(dataRoot);
        Fixture fixture = prepareFixture(manager);
        byte[] areaBefore = Files.readAllBytes(fixture.areaFile);

        assertThrows(IOException.class, () -> manager.upsertRegion(
                area(
                        Collections.singletonList("say new %player%"),
                        Collections.singletonList("tag %player% remove new")
                ),
                false
        ));

        assertArrayEquals(areaBefore, Files.readAllBytes(fixture.areaFile));
        assertArrayEquals(
                fixture.musicBefore,
                Files.readAllBytes(fixture.musicFile)
        );
        assertSame(fixture.original, manager.findArea(WORLD, AREA));
        assertEquals(
                Collections.singletonList("say old %player%"),
                fixture.original.getEnterCommands()
        );
        assertEquals(
                Collections.singletonList("tag %player% remove old"),
                fixture.original.getExitCommands()
        );
    }

    @Test
    void createAtomicallyWritesBothCommandListsAndEmptyPlaylist()
            throws IOException {
        ConfigManager manager = new ConfigManager(null, dataRoot);
        List<String> callerEnter = new ArrayList<>(Collections.singletonList(
                "  /say enter %player%  "
        ));
        List<String> callerExit = new ArrayList<>(Collections.singletonList(
                "  /say exit %player%  "
        ));
        AreaDto created = area(callerEnter, callerExit);

        manager.upsertRegion(created, true);

        Path directory = dataRoot.resolve("worlds")
                .resolve(WORLD)
                .resolve("regions")
                .resolve(AREA);
        RegionAreaConfig stored = gson.fromJson(
                new String(
                        Files.readAllBytes(directory.resolve("area.json")),
                        StandardCharsets.UTF_8
                ),
                RegionAreaConfig.class
        );
        RegionMusicConfig music = gson.fromJson(
                new String(
                        Files.readAllBytes(directory.resolve("music.json")),
                        StandardCharsets.UTF_8
                ),
                RegionMusicConfig.class
        );
        assertEquals(Collections.singletonList("say enter %player%"),
                stored.getEnterCommands());
        assertEquals(Collections.singletonList("say exit %player%"),
                stored.getExitCommands());
        assertTrue(music.getMusic().isEmpty());
        assertSame(created, manager.findArea(WORLD, AREA));

        callerEnter.add("say caller mutation");
        callerExit.add("say caller mutation");
        assertEquals(Collections.singletonList("say enter %player%"),
                created.getEnterCommands());
        assertEquals(Collections.singletonList("say exit %player%"),
                created.getExitCommands());
    }

    @Test
    void createWriteFailurePublishesNoDirectoryOrRuntimeArea(){
        ConfigManager manager = new FailingWriteConfigManager(dataRoot);
        AreaDto created = area(
                Collections.singletonList("say enter %player%"),
                Collections.singletonList("say exit %player%")
        );

        assertThrows(IOException.class, () -> manager.upsertRegion(created, true));

        assertTrue(manager.getAreas().isEmpty());
        assertTrue(Files.notExists(dataRoot.resolve("worlds")
                .resolve(WORLD)
                .resolve("regions")
                .resolve(AREA)));
    }

    private Fixture prepareFixture(ConfigManager manager) throws IOException {
        Path directory = dataRoot.resolve("worlds")
                .resolve(WORLD)
                .resolve("regions")
                .resolve(AREA);
        Files.createDirectories(directory);
        Path areaFile = directory.resolve("area.json");
        Path musicFile = directory.resolve("music.json");
        Files.write(
                areaFile,
                "{\"marker\":\"old-area\"}\n".getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
                musicFile,
                "{\"music\":[{\"id\":\"keep\",\"sound\":\"rookie.keep\",\"duration\":10}]}\n"
                        .getBytes(StandardCharsets.UTF_8)
        );

        AreaDto original = area(
                Collections.singletonList("say old %player%"),
                Collections.singletonList("tag %player% remove old")
        );
        Map<String, AreaDto> worldAreas = new ConcurrentHashMap<>();
        worldAreas.put(original.getUuid(), original);
        manager.getAreas().put(WORLD, worldAreas);
        return new Fixture(
                areaFile,
                musicFile,
                Files.readAllBytes(musicFile),
                original
        );
    }

    private AreaDto area(List<String> enterCommands,
                         List<String> exitCommands){
        SlicedPolygonVolume shape = new SlicedPolygonVolume(
                RegionShapeConfig.builder()
                        .slices(Collections.singletonList(
                                RegionShapeConfig.Slice.builder()
                                        .y(64.0)
                                        .polygon(Arrays.asList(
                                                point(0.0, 0.0),
                                                point(10.0, 0.0),
                                                point(0.0, 10.0)
                                        ))
                                        .build()
                        ))
                        .build()
        );
        return AreaDto.builder()
                .world(WORLD)
                .uuid(ConfigManager.createRegionUuid(WORLD, AREA))
                .areaId(AREA)
                .musicId(new CopyOnWriteArrayList<>())
                .channel("stinger")
                .order(0)
                .priority(Priority.NORMAL)
                .random(false)
                .loop(false)
                .enabled(true)
                .overWrite(false)
                .volume(1.0f)
                .pitch(1.0f)
                .enterCommands(enterCommands)
                .exitCommands(exitCommands)
                .shape(shape)
                .minPoint(shape.getMinPoint())
                .maxPoint(shape.getMaxPoint())
                .build();
    }

    private RegionShapeConfig.Point point(double x, double z){
        return RegionShapeConfig.Point.builder().x(x).z(z).build();
    }

    private static final class FailingWriteConfigManager
            extends ConfigManager {
        private FailingWriteConfigManager(Path dataRoot) {
            super(null, dataRoot);
        }

        @Override
        void writeJsonAtomically(Path file, Object value) throws IOException {
            throw new IOException("simulated disk failure");
        }
    }

    private static final class Fixture {
        private final Path areaFile;
        private final Path musicFile;
        private final byte[] musicBefore;
        private final AreaDto original;

        private Fixture(Path areaFile,
                        Path musicFile,
                        byte[] musicBefore,
                        AreaDto original) {
            this.areaFile = areaFile;
            this.musicFile = musicFile;
            this.musicBefore = musicBefore;
            this.original = original;
        }
    }
}
