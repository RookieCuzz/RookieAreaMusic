package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.RegionSnapshot;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionSaveRequestsTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000511"),
            "minecraft:request_builder"
    );

    @Test
    void createBuilderPinsRevisionAndAcceptsConfirmationToken() {
        Region global = global();
        RegionSnapshot snapshot = RegionSnapshot.of(7L, List.of(global));
        Region candidate = box("new-region", global.key(), 0, 10);

        RegionSaveRequest request = RegionSaveRequests.create(snapshot, candidate)
                .sessionId("external-session")
                .confirmationToken("opaque-token")
                .build();

        assertEquals(SaveMode.CREATE, request.mode());
        assertEquals(7L, request.expectedSnapshotRevision());
        assertEquals("external-session", request.sessionId());
        assertEquals("opaque-token", request.confirmationToken().orElseThrow());
        assertTrue(request.expectedTargetFingerprint().isEmpty());
    }

    @Test
    void editBuilderCalculatesFingerprintFromPinnedTarget() {
        Region global = global();
        Region current = box("plot", global.key(), 0, 10);
        Region changed = box("plot", global.key(), 0, 12);
        RegionSnapshot snapshot = RegionSnapshot.of(11L, List.of(global, current));

        RegionSaveRequest request = RegionSaveRequests.edit(
                snapshot, current.key(), changed
        ).build();

        assertEquals(SaveMode.EDIT, request.mode());
        assertEquals(11L, request.expectedSnapshotRevision());
        assertEquals(
                RegionFingerprints.region(current),
                request.expectedTargetFingerprint().orElseThrow()
        );
    }

    private Region global() {
        return Region.builder(RegionKey.global(world), GlobalShape.INSTANCE).build();
    }

    private Region box(String id, RegionKey parent, double min, double max) {
        return Region.builder(
                        new RegionKey(world, id),
                        new CuboidShape(min, min, min, max, max, max)
                )
                .parent(parent)
                .build();
    }
}
