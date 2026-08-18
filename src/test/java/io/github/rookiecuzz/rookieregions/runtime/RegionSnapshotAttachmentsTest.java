package io.github.rookiecuzz.rookieregions.runtime;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.commands.RegionCommandProfile;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class RegionSnapshotAttachmentsTest {
    @Test
    void publishesCoreAndModulesAsOneImmutableSnapshot() {
        WorldId world = new WorldId(UUID.randomUUID(), "minecraft:world");
        Region global = Region.builder(RegionKey.global(world), GlobalShape.INSTANCE)
                .priority(Integer.MIN_VALUE)
                .build();
        RegionCommandProfile commands = new RegionCommandProfile(
                List.of("say enter"),
                List.of("say leave")
        );
        RegionRecord record = new RegionRecord(
                global,
                RegionMusicProfile.empty(),
                commands
        );

        RegionSnapshot snapshot = RegionSnapshot.ofRecords(7L, List.of(record));

        assertEquals(7L, snapshot.revision());
        assertSame(record, snapshot.records().get(global.key()));
        assertEquals(commands, snapshot.records().get(global.key()).commands());
    }
}
