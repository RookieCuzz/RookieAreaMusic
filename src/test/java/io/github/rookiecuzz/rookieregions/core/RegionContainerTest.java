package io.github.rookiecuzz.rookieregions.core;

import io.github.rookiecuzz.rookieregions.core.shape.CuboidShape;
import io.github.rookiecuzz.rookieregions.core.shape.GlobalShape;
import io.github.rookiecuzz.rookieregions.runtime.RegionRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegionContainerTest {
    private final WorldId world = new WorldId(
            UUID.fromString("00000000-0000-0000-0000-000000000091"),
            "minecraft:container_test"
    );

    @Test
    void publicationUsesCasAndAssignsStrictlyIncreasingRevisions() throws Exception {
        RegionContainer container = new RegionContainer();
        RegionContainer.RecordPublication publication = container.recordPublication();
        List<RegionRecord> regions = records();
        int contenders = 12;
        CountDownLatch ready = new CountDownLatch(contenders);
        CountDownLatch start = new CountDownLatch(1);

        try(ExecutorService executor = Executors.newFixedThreadPool(contenders)){
            List<Future<Boolean>> results = new ArrayList<>();
            for(int index = 0; index < contenders; index++){
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return publication.compareAndPublish(0L, regions).isPresent();
                }));
            }
            ready.await();
            start.countDown();

            int successes = 0;
            for(Future<Boolean> result : results){
                if(result.get()){
                    successes++;
                }
            }
            assertEquals(1, successes);
        }

        assertEquals(1L, container.snapshot().revision());
        assertTrue(publication.compareAndPublish(0L, regions).isEmpty());
        RegionSnapshot second = publication.compareAndPublish(1L, regions).orElseThrow();
        assertEquals(2L, second.revision());
        assertSame(second, container.snapshot());
    }

    @Test
    void queriesArePinnedAndInvalidPublicationsNeverBecomeVisible(){
        RegionContainer container = new RegionContainer();
        RegionQuery before = container.query();

        RegionSnapshot published = container.recordPublication()
                .compareAndPublish(0L, records())
                .orElseThrow();
        RegionQuery after = container.query();

        assertEquals(0L, before.snapshot().revision());
        assertTrue(before.at(world, 5, 5, 5).localRegions().isEmpty());
        assertEquals(published, after.snapshot());
        assertFalse(after.at(world, 5, 5, 5).localRegions().isEmpty());

        Region invalid = Region.builder(
                        new RegionKey(world, "orphan"),
                        new CuboidShape(0, 0, 0, 1, 1, 1)
                )
                .parent(new RegionKey(world, "missing"))
                .build();
        assertThrows(
                RegionGraphValidationException.class,
                () -> container.recordPublication().compareAndPublish(
                        published.revision(),
                        List.of(
                                RegionRecord.coreOnly(global()),
                                RegionRecord.coreOnly(invalid)
                        )
                )
        );
        assertSame(published, container.snapshot());
    }

    @Test
    void revisionCannotOverflow(){
        RegionContainer container = new RegionContainer(
                RegionSnapshot.of(Long.MAX_VALUE, List.of())
        );
        assertThrows(
                IllegalStateException.class,
                () -> container.recordPublication().compareAndPublish(
                        Long.MAX_VALUE, List.of()
                )
        );
        assertEquals(Long.MAX_VALUE, container.snapshot().revision());
    }

    private List<Region> regions(){
        Region global = global();
        Region local = Region.builder(
                        new RegionKey(world, "claim"),
                        new CuboidShape(0, 0, 0, 10, 10, 10)
                )
                .parent(global.key())
                .build();
        return List.of(global, local);
    }

    private List<RegionRecord> records(){
        return regions().stream().map(RegionRecord::coreOnly).toList();
    }

    private Region global(){
        return Region.builder(RegionKey.global(world), GlobalShape.INSTANCE).build();
    }
}
