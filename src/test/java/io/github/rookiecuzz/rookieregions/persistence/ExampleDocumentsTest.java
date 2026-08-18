package io.github.rookiecuzz.rookieregions.persistence;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExampleDocumentsTest {
    @Test
    void bundledOuterBirdsAndInnerSilenceDocumentsStrictlyLoad() throws Exception {
        WorldId world = new WorldId(
                UUID.fromString("00000000-0000-0000-0000-000000000001"),
                "minecraft:overworld"
        );
        var snapshot = new RegionRepository(Path.of("examples"))
                .load(1L, List.of(world));

        var forest = snapshot.records().get(new RegionKey(world, "forest"));
        var quiet = snapshot.records().get(new RegionKey(world, "quiet_grove"));
        assertTrue(forest != null && quiet != null);
        assertEquals(
                MusicPolicyMode.ADD,
                forest.music().getChannel("ambience").getPolicy()
        );
        assertEquals(
                MusicPolicyMode.BLOCK,
                quiet.music().getChannel("ambience").getPolicy()
        );
        assertEquals(
                forest.region().key(), quiet.region().parent().orElseThrow()
        );
    }
}
