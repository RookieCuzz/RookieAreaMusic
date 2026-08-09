package com.gitee.niocho.areamusic.config;

import com.gitee.niocho.areamusic.geometry.SlicedPolygonVolume;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleConfigurationTest {
    private static final Path WORLDS = Paths.get("examples", "worlds");
    private static final Path CRAFT_ENGINE_SOUNDS = Paths.get(
            "examples",
            "craftengine",
            "rookie_music",
            "configuration",
            "sounds.yml"
    );
    private final Gson gson = new Gson();

    @Test
    void everyAreaExampleUsesAValidSlicedPolygon() throws IOException {
        List<Path> files = findFiles("area.json");
        assertFalse(files.isEmpty());

        PlaybackChannelRegistry channels = PlaybackChannelRegistry.defaults();
        for(Path file : files){
            RegionAreaConfig area = read(file, RegionAreaConfig.class);
            assertNotNull(area, file.toString());
            assertTrue(channels.contains(area.getChannel()), file.toString());
            assertNotNull(area.getPriority(), file.toString());
            assertNotNull(area.getShape(), file.toString());
            new SlicedPolygonVolume(area.getShape());
        }
    }

    @Test
    void everyMusicExampleHasCompleteTracks() throws IOException {
        List<Path> files = findFiles("music.json");
        assertFalse(files.isEmpty());

        for(Path file : files){
            RegionMusicConfig config = read(file, RegionMusicConfig.class);
            assertNotNull(config, file.toString());
            assertNotNull(config.getMusic(), file.toString());
            for(RegionMusicConfig.Track track : config.getMusic()){
                assertText(track.getId(), file);
                assertText(track.getSound(), file);
                assertTrue(track.getDuration() != null && track.getDuration() > 0,
                        file.toString());
            }
        }
    }

    @Test
    void everySoundSourceExampleIsComplete() throws IOException {
        List<Path> files = findSourceFiles();
        assertFalse(files.isEmpty());

        for(Path file : files){
            SoundSourceConfig source = read(file, SoundSourceConfig.class);
            assertNotNull(source, file.toString());
            assertNotNull(source.getPosition(), file.toString());
            assertNotNull(source.getPosition().getX(), file.toString());
            assertNotNull(source.getPosition().getY(), file.toString());
            assertNotNull(source.getPosition().getZ(), file.toString());
            assertText(source.getSound(), file);
            assertTrue(source.getDuration() != null && source.getDuration() > 0,
                    file.toString());
            assertTrue(source.getInterval() != null && source.getInterval() >= 0,
                    file.toString());
            assertTrue(source.getVolume() != null && source.getVolume() > 0,
                    file.toString());
            assertTrue(source.getPitch() != null && source.getPitch() > 0,
                    file.toString());
        }
    }

    @Test
    void craftEngineTemplateDefinesEveryReferencedEvent() throws IOException {
        String sounds = new String(
                Files.readAllBytes(CRAFT_ENGINE_SOUNDS),
                StandardCharsets.UTF_8
        );
        Set<String> referencedEvents = new HashSet<>();

        for(Path file : findFiles("music.json")){
            RegionMusicConfig config = read(file, RegionMusicConfig.class);
            for(RegionMusicConfig.Track track : config.getMusic()){
                referencedEvents.add(track.getSound());
            }
        }
        for(Path file : findSourceFiles()){
            referencedEvents.add(read(file, SoundSourceConfig.class).getSound());
        }

        assertFalse(referencedEvents.isEmpty());
        for(String event : referencedEvents){
            assertTrue(
                    sounds.contains("  " + event + ":"),
                    "CraftEngine sounds.yml 缺少事件: " + event
            );
        }
    }

    private List<Path> findFiles(String fileName) throws IOException {
        List<Path> result = new ArrayList<>();
        try(Stream<Path> stream = Files.walk(WORLDS)){
            stream.filter(Files::isRegularFile)
                    .filter(path -> fileName.equals(path.getFileName().toString()))
                    .forEach(result::add);
        }
        return result;
    }

    private List<Path> findSourceFiles() throws IOException {
        List<Path> result = new ArrayList<>();
        try(Stream<Path> stream = Files.walk(WORLDS)){
            stream.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".json"))
                    .filter(path -> path.getParent() != null
                            && "sources".equals(path.getParent().getFileName().toString()))
                    .forEach(result::add);
        }
        return result;
    }

    private <T> T read(Path file, Class<T> type) throws IOException {
        try(BufferedReader reader = Files.newBufferedReader(
                file,
                StandardCharsets.UTF_8
        )){
            return gson.fromJson(reader, type);
        }
    }

    private void assertText(String value, Path file){
        assertTrue(value != null && !value.trim().isEmpty(), file.toString());
    }
}
