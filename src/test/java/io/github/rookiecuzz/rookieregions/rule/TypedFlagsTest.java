package io.github.rookiecuzz.rookieregions.rule;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedFlagsTest {
    private enum Mode { FIRST, SECOND }

    @Test
    void enumAndSetFlagsUseStrictTypedCodecsAndDeclaredConflictPolicy() {
        EnumFlag<Mode> mode = new EnumFlag<>(
                "test.mode", Mode.class, ConflictStrategies.reject()
        );
        assertEquals("second", mode.codec().encode(Mode.SECOND));
        assertEquals(Mode.FIRST, mode.codec().decode("FIRST"));

        SetFlag<String> tags = new SetFlag<>(
                "test.tags", FlagCodecs.STRING, true
        );
        Set<String> decoded = tags.codec().decode(List.of("one", "two"));
        assertEquals(Set.of("one", "two"), decoded);
        assertEquals(
                Set.of("one", "two", "three"),
                tags.conflictStrategy().resolve(List.of(
                        Set.of("one", "two"), Set.of("three")
                )).orElseThrow()
        );
    }

    @Test
    void flagsDeclareActorScopeAndModificationPermission() {
        StringFlag playerOnly = new StringFlag(
                "test.player-only",
                InheritanceMode.INHERIT,
                FlagDefault.unset(),
                ConflictStrategies.reject(),
                FlagScope.LOCAL_REGION,
                ActorScope.PLAYER,
                "rookieregions.custom.modify"
        );

        assertTrue(playerOnly.actorScope().accepts(Subject.player(
                java.util.UUID.randomUUID()
        )));
        assertEquals(
                "rookieregions.custom.modify",
                playerOnly.modificationPermission()
        );
        assertEquals(
                "rookieregions.admin",
                ProtectionFlags.ALLOW_PLAYER_REGIONS.modificationPermission()
        );
    }
}
