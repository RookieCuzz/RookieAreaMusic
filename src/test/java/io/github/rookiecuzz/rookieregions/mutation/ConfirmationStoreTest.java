package io.github.rookiecuzz.rookieregions.mutation;

import io.github.rookiecuzz.rookieregions.core.RegionKey;
import io.github.rookiecuzz.rookieregions.core.WorldId;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfirmationStoreTest {
    private final AtomicLong now = new AtomicLong(1_000L);
    private final AtomicInteger tokenSequence = new AtomicInteger();
    private final ConfirmationStore store = new ConfirmationStore(
            now::get,
            () -> "token-" + tokenSequence.incrementAndGet()
    );

    @Test
    void tokenBindsActorSessionCandidatePlanRevisionAndChoice(){
        List<ConfirmationOption> issued = store.issue(
                "actor", "session", "candidate", "plan", 7L,
                Arrays.asList(
                        PlacementOption.keepOverlap(),
                        PlacementOption.setParent(parentKey())
                )
        );

        ConfirmationConsumption wrongActor = store.consume(
                issued.get(0).token(), "other", "session", "candidate"
        );
        assertEquals(ConfirmationConsumeStatus.BINDING_MISMATCH,
                wrongActor.status());
        assertEquals(2, store.size());

        ConfirmationConsumption consumed = store.consume(
                issued.get(1).token(), "actor", "session", "candidate"
        );
        assertEquals(ConfirmationConsumeStatus.AUTHORIZED, consumed.status());
        ConfirmationAuthorization authorization = consumed.authorization()
                .orElseThrow();
        assertEquals("plan", authorization.placementPlanFingerprint());
        assertEquals(7L, authorization.snapshotRevision());
        assertEquals(PlacementOption.setParent(parentKey()),
                authorization.option());
        assertEquals(0, store.size());

        assertEquals(ConfirmationConsumeStatus.INVALID, store.consume(
                issued.get(0).token(), "actor", "session", "candidate"
        ).status());
    }

    @Test
    void tokenExpiresAtExactlyThirtySeconds(){
        String token = issueKeep();
        now.set(30_999L);
        assertEquals(ConfirmationConsumeStatus.AUTHORIZED, store.consume(
                token, "actor", "session", "candidate"
        ).status());

        now.set(50_000L);
        token = issueKeep();
        now.set(80_000L);
        assertEquals(ConfirmationConsumeStatus.EXPIRED, store.consume(
                token, "actor", "session", "candidate"
        ).status());
    }

    @Test
    void newChallengeForSessionInvalidatesAllOldOptions(){
        String old = issueKeep();
        String replacement = issueKeep();

        assertEquals(ConfirmationConsumeStatus.INVALID, store.consume(
                old, "actor", "session", "candidate"
        ).status());
        assertEquals(ConfirmationConsumeStatus.AUTHORIZED, store.consume(
                replacement, "actor", "session", "candidate"
        ).status());
    }

    @Test
    void changedCandidateCannotUseOldToken(){
        String token = issueKeep();

        ConfirmationConsumption changed = store.consume(
                token, "actor", "session", "different"
        );

        assertEquals(ConfirmationConsumeStatus.BINDING_MISMATCH,
                changed.status());
        assertTrue(changed.authorization().isEmpty());
    }

    @Test
    void reloadStyleGlobalInvalidationDestroysEverySessionToken(){
        String first = issueKeep();
        String second = store.issue(
                "other", "other-session", "other-candidate", "other-plan", 2L,
                List.of(PlacementOption.keepOverlap())
        ).getFirst().token();

        store.invalidateAll();

        assertEquals(0, store.size());
        assertEquals(ConfirmationConsumeStatus.INVALID, store.consume(
                first, "actor", "session", "candidate"
        ).status());
        assertEquals(ConfirmationConsumeStatus.INVALID, store.consume(
                second, "other", "other-session", "other-candidate"
        ).status());
    }

    private String issueKeep(){
        return store.issue(
                "actor", "session", "candidate", "plan", 1L,
                List.of(PlacementOption.keepOverlap())
        ).get(0).token();
    }

    private RegionKey parentKey(){
        return new RegionKey(new WorldId(
                UUID.fromString("00000000-0000-0000-0000-000000000099"),
                "minecraft:test"
        ), "parent");
    }
}
