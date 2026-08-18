package io.github.rookiecuzz.rookieregions.config;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.module.music.ChannelPlaybackMode;
import io.github.rookiecuzz.rookieregions.module.music.MusicChannelDefinition;
import io.github.rookiecuzz.rookieregions.module.music.MusicPolicyMode;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicChannel;
import io.github.rookiecuzz.rookieregions.module.music.RegionMusicProfile;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionRuntimeValidatorTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000921"),
            "minecraft:test"
    );

    @Test
    void acceptsOnlyChannelsDefinedByTheSameStagedSettings() {
        RegionSnapshot snapshot = snapshotWithChannel("ambient");

        assertDoesNotThrow(() -> RegionRuntimeValidator.validate(
                snapshot, settings("ambient")
        ));
    }

    @Test
    void rejectsUnknownOrRemovedChannelsBeforePublication() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> RegionRuntimeValidator.validate(
                        snapshotWithChannel("removed"), settings("ambient")
                )
        );

        assertTrue(failure.getMessage().contains("removed"));
        assertTrue(failure.getMessage().contains("__global__"));
    }

    private RegionSnapshot snapshotWithChannel(String channel) {
        Region global = Region.builder(
                RegionKey.global(world), GlobalShape.INSTANCE
        ).build();
        RegionMusicProfile music = new RegionMusicProfile(Map.of(
                channel,
                RegionMusicChannel.builder()
                        .policy(MusicPolicyMode.BLOCK)
                        .build()
        ));
        return RegionSnapshot.ofRecords(
                7L, java.util.List.of(new RegionRecord(
                        global, music,
                        io.github.rookiecuzz.rookieregions.module.commands
                                .RegionCommandProfile.empty()
                ))
        );
    }

    private RookieRegionsSettings settings(String channel) {
        return new RookieRegionsSettings(
                Duration.ofSeconds(30),
                20L,
                true,
                true,
                Map.of(channel, new MusicChannelDefinition(
                        channel, ChannelPlaybackMode.EXCLUSIVE, 1
                ))
        );
    }
}
