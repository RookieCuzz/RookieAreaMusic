package com.gitee.niocho.areamusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionConfigFormatTest {
    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

    @Test
    void areaJsonContainsOnlyRegionLocalSettings(){
        RegionAreaConfig config = RegionAreaConfig.builder()
                .channel("ambience")
                .order(12)
                .priority(Priority.NORMAL)
                .random(false)
                .loop(true)
                .enabled(true)
                .overWrite(true)
                .volume(1.0f)
                .pitch(1.0f)
                .shape(RegionShapeConfig.builder()
                        .slices(Collections.singletonList(RegionShapeConfig.Slice.builder()
                                .y(1.0)
                                .polygon(Arrays.asList(
                                        RegionShapeConfig.Point.builder().x(0.0).z(0.0).build(),
                                        RegionShapeConfig.Point.builder().x(10.0).z(0.0).build(),
                                        RegionShapeConfig.Point.builder().x(0.0).z(10.0).build()
                                ))
                                .build()))
                        .build())
                .build();

        String json = gson.toJson(config);

        assertFalse(json.contains("\"world\""));
        assertFalse(json.contains("\"areaId\""));
        assertFalse(json.contains("\"uuid\""));
        assertFalse(json.contains("\"overWrite\""));
        assertFalse(json.contains("\"minPoint\""));
        assertFalse(json.contains("\"maxPoint\""));
        assertFalse(json.contains("\"min\":"));
        assertFalse(json.contains("\"max\":"));
        assertTrue(json.contains("\"shape\""));
        assertTrue(json.contains("\"channel\": \"ambience\""));
        assertTrue(json.contains("\"order\": 12"));
        assertEquals(config, gson.fromJson(json, RegionAreaConfig.class));
    }

    @Test
    void legacyBoxCoordinatesAreIgnoredAndDoNotCreateAShape(){
        RegionAreaConfig config = gson.fromJson(
                "{\"minPoint\":{\"x\":0,\"y\":0,\"z\":0},"
                        + "\"maxPoint\":{\"x\":10,\"y\":10,\"z\":10}}",
                RegionAreaConfig.class
        );

        assertEquals(null, config.getShape());
    }

    @Test
    void musicJsonUsesReadableLocalTrackFields(){
        RegionMusicConfig config = RegionMusicConfig.builder()
                .music(Collections.singletonList(RegionMusicConfig.Track.builder()
                        .id("spawn_day")
                        .sound("rpg.spawn_day")
                        .duration(180L)
                        .build()))
                .build();

        String json = gson.toJson(config);

        assertFalse(json.contains("\"uuid\""));
        assertEquals("spawn_day", gson.fromJson(json, RegionMusicConfig.class)
                .getMusic().get(0).getId());
    }

    @Test
    void derivedIdsAreStableAndRegionScoped(){
        String first = ConfigManager.createTrackUuid("world", "spawn", "theme");
        String second = ConfigManager.createTrackUuid("world", "spawn", "theme");
        String anotherRegion = ConfigManager.createTrackUuid("world", "dungeon", "theme");

        assertEquals(first, second);
        assertNotEquals(first, anotherRegion);
    }

    @Test
    void soundSourceJsonContainsOnlyOneSourcesLocalSettings(){
        SoundSourceConfig config = SoundSourceConfig.builder()
                .position(SoundSourceConfig.Position.builder()
                        .x(12.5)
                        .y(70.5)
                        .z(-8.5)
                        .build())
                .sound("ambient.tree_birds")
                .duration(6L)
                .interval(12L)
                .volume(1.0f)
                .pitch(1.0f)
                .enabled(true)
                .build();

        String json = gson.toJson(config);

        assertFalse(json.contains("\"world\""));
        assertFalse(json.contains("\"sourceId\""));
        assertFalse(json.contains("\"uuid\""));
        assertEquals(config, gson.fromJson(json, SoundSourceConfig.class));
        assertEquals(
                ConfigManager.createSoundSourceUuid("world", "tree_birds"),
                ConfigManager.createSoundSourceUuid("world", "tree_birds")
        );
        assertNotEquals(
                ConfigManager.createSoundSourceUuid("world", "tree_birds"),
                ConfigManager.createSoundSourceUuid("world_nether", "tree_birds")
        );
    }
}
