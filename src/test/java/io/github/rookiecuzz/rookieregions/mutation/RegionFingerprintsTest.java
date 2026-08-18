package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.Region;
import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.rule.ProtectionFlags;
import io.github.rookiecuzz.rookieregions.rule.State;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RegionFingerprintsTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000102"),
            "minecraft:overworld"
    );

    @Test
    void structurallyEqualRegionsHaveStableFingerprint(){
        assertEquals(
                RegionFingerprints.region(region(0, State.ALLOW)),
                RegionFingerprints.region(region(0, State.ALLOW))
        );
    }

    @Test
    void geometryPriorityParentAndFlagsParticipateInFingerprint(){
        String baseline = RegionFingerprints.region(region(0, State.ALLOW));

        assertNotEquals(baseline,
                RegionFingerprints.region(region(1, State.ALLOW)));
        assertNotEquals(baseline,
                RegionFingerprints.region(region(0, State.DENY)));
        Region differentShape = Region.builder(
                        key("region"),
                        new CuboidShape(0, 0, 0, 20, 20, 20)
                )
                .parent(key("other-parent"))
                .flag(ProtectionFlags.ALLOW_PLAYER_REGIONS, State.ALLOW)
                .build();
        assertNotEquals(baseline, RegionFingerprints.region(differentShape));
    }

    private Region region(int priority, State state){
        return Region.builder(
                        key("region"),
                        new CuboidShape(0, 0, 0, 10, 10, 10)
                )
                .parent(key("parent"))
                .priority(priority)
                .flag(ProtectionFlags.ALLOW_PLAYER_REGIONS, state)
                .build();
    }

    private RegionKey key(String id){
        return new RegionKey(world, id);
    }
}
