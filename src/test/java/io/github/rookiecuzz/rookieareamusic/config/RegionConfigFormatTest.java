package io.github.rookiecuzz.rookieareamusic.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void enterCommandsRoundTripWithStableFieldNameAndNormalization()
            throws IOException {
        List<String> normalized = ConfigManager.normalizeEnterCommands(
                Arrays.asList(
                        "  say welcome %player%  ",
                        "  /effect give %player% minecraft:glowing 10 0 true  "
                ),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("area.json")
        );
        assertEquals(Arrays.asList(
                "say welcome %player%",
                "effect give %player% minecraft:glowing 10 0 true"
        ), normalized);

        RegionAreaConfig config = RegionAreaConfig.builder()
                .channel("stinger")
                .enterCommands(normalized)
                .build();
        String json = gson.toJson(config);

        assertTrue(json.contains("\"enterCommands\""));
        assertFalse(json.contains("\"commands\""));
        assertEquals(normalized, gson.fromJson(
                json,
                RegionAreaConfig.class
        ).getEnterCommands());
        assertTrue(ConfigManager.normalizeEnterCommands(
                null,
                ChannelTrigger.ENTER_ONCE,
                Paths.get("legacy-area.json")
        ).isEmpty());
    }

    @Test
    void exitCommandsRoundTripWithStableFieldNameAndNormalization()
            throws IOException {
        List<String> normalized = ConfigManager.normalizeExitCommands(
                Arrays.asList(
                        "  tag %player% remove boss_gate  ",
                        "  /effect clear %player% minecraft:glowing  "
                ),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("area.json")
        );
        assertEquals(Arrays.asList(
                "tag %player% remove boss_gate",
                "effect clear %player% minecraft:glowing"
        ), normalized);

        RegionAreaConfig config = RegionAreaConfig.builder()
                .channel("stinger")
                .exitCommands(normalized)
                .build();
        String json = gson.toJson(config);

        assertTrue(json.contains("\"exitCommands\""));
        assertEquals(normalized, gson.fromJson(
                json,
                RegionAreaConfig.class
        ).getExitCommands());
        assertTrue(gson.fromJson(
                "{\"channel\":\"stinger\"}",
                RegionAreaConfig.class
        ).getExitCommands().isEmpty());
        assertTrue(ConfigManager.normalizeExitCommands(
                gson.fromJson(
                        "{\"exitCommands\":null}",
                        RegionAreaConfig.class
                ).getExitCommands(),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("legacy-area.json")
        ).isEmpty());
    }

    @Test
    void invalidEnterCommandsAreRejectedBeforePublication(){
        List<List<String>> invalid = Arrays.asList(
                Collections.singletonList(null),
                Collections.singletonList("   "),
                Collections.singletonList("/"),
                Collections.singletonList("//say unsafe"),
                Collections.singletonList("/ /say unsafe"),
                Collections.singletonList("say first\nsay second"),
                Collections.singletonList("say first\rsay second"),
                Collections.singletonList("say \0 unsafe"),
                Collections.singletonList(repeat('x', 1025)),
                Collections.singletonList(" " + repeat('x', 1024)),
                Collections.nCopies(17, "say ok"),
                Collections.nCopies(9, repeat('x', 1024))
        );

        for(List<String> commands : invalid){
            assertThrows(IOException.class, () ->
                    ConfigManager.normalizeEnterCommands(
                            commands,
                            ChannelTrigger.ENTER_ONCE,
                            Paths.get("area.json")
                    ));
        }
        assertThrows(IOException.class, () ->
                ConfigManager.normalizeEnterCommands(
                        Collections.singletonList("say continuous"),
                        ChannelTrigger.CONTINUOUS,
                        Paths.get("area.json")
                ));
    }

    @Test
    void invalidExitCommandsAreRejectedBeforePublication(){
        List<List<String>> invalid = Arrays.asList(
                Collections.singletonList(null),
                Collections.singletonList("   "),
                Collections.singletonList("/"),
                Collections.singletonList("//say unsafe"),
                Collections.singletonList("/ /say unsafe"),
                Collections.singletonList("say first\nsay second"),
                Collections.singletonList("say first\rsay second"),
                Collections.singletonList("say \0 unsafe"),
                Collections.singletonList(repeat('x', 1025)),
                Collections.singletonList(" " + repeat('x', 1024)),
                Collections.nCopies(17, "say ok"),
                Collections.nCopies(9, repeat('x', 1024))
        );

        for(List<String> commands : invalid){
            assertThrows(IOException.class, () ->
                    ConfigManager.normalizeExitCommands(
                            commands,
                            ChannelTrigger.ENTER_ONCE,
                            Paths.get("area.json")
                    ));
        }
        assertThrows(IOException.class, () ->
                ConfigManager.normalizeExitCommands(
                        Collections.singletonList("say continuous"),
                        ChannelTrigger.CONTINUOUS,
                        Paths.get("area.json")
                ));
    }

    @Test
    void enterCommandResourceLimitsAcceptTheirExactBoundaries()
            throws IOException {
        assertEquals(16, ConfigManager.normalizeEnterCommands(
                Collections.nCopies(16, "say ok"),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("area.json")
        ).size());
        assertEquals(8, ConfigManager.normalizeEnterCommands(
                Collections.nCopies(8, repeat('x', 1024)),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("area.json")
        ).size());
    }

    @Test
    void exitCommandResourceLimitsAcceptTheirExactBoundaries()
            throws IOException {
        assertEquals(16, ConfigManager.normalizeExitCommands(
                Collections.nCopies(16, "say ok"),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("area.json")
        ).size());
        assertEquals(8, ConfigManager.normalizeExitCommands(
                Collections.nCopies(8, repeat('x', 1024)),
                ChannelTrigger.ENTER_ONCE,
                Paths.get("area.json")
        ).size());
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

    private String repeat(char value, int count){
        char[] result = new char[count];
        Arrays.fill(result, value);
        return new String(result);
    }
}
