package com.gitee.niocho.areamusic.async;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTaskCoordinatorTest {
    @Test
    void discardsAnOlderPositionRevision(){
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        long oldRevision = coordinator.nextRevision(player);
        long currentRevision = coordinator.nextRevision(player);
        AtomicBoolean oldTaskRan = new AtomicBoolean();
        AtomicBoolean currentTaskRan = new AtomicBoolean();

        assertFalse(coordinator.runIfCurrent(
                player,
                oldRevision,
                () -> oldTaskRan.set(true)
        ));
        assertTrue(coordinator.runIfCurrent(
                player,
                currentRevision,
                () -> currentTaskRan.set(true)
        ));
        assertFalse(oldTaskRan.get());
        assertTrue(currentTaskRan.get());
        assertFalse(coordinator.isCurrent(player, oldRevision));
        assertTrue(coordinator.isCurrent(player, currentRevision));
    }

    @Test
    void invalidationRejectsAlreadyQueuedWork(){
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        long revision = coordinator.nextRevision(player);
        coordinator.invalidate(player);

        assertFalse(coordinator.runIfCurrent(player, revision, () -> {
            throw new AssertionError("失效任务不应执行");
        }));
    }

    @Test
    void serializesConcurrentWorkForTheSamePlayer() throws Exception {
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondSubmitted = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);

        try {
            executor.submit(() -> coordinator.runSerialized(player, () -> {
                firstEntered.countDown();
                await(releaseFirst);
            }));
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            executor.submit(() -> {
                secondSubmitted.countDown();
                coordinator.runSerialized(player, secondFinished::countDown);
            });
            assertTrue(secondSubmitted.await(1, TimeUnit.SECONDS));
            assertFalse(secondFinished.await(100, TimeUnit.MILLISECONDS));

            releaseFirst.countDown();
            assertTrue(secondFinished.await(1, TimeUnit.SECONDS));
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void alreadyStartedTaskCommitsBeforeANewerRevision() throws Exception {
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        long firstRevision = coordinator.nextRevision(player);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        List<String> committed = new CopyOnWriteArrayList<>();

        try {
            Future<Boolean> first = executor.submit(() ->
                    coordinator.runIfCurrent(player, firstRevision, () -> {
                        firstEntered.countDown();
                        await(releaseFirst);
                        committed.add("first");
                    })
            );
            assertTrue(firstEntered.await(1, TimeUnit.SECONDS));

            long secondRevision = coordinator.nextRevision(player);
            Future<Boolean> second = executor.submit(() ->
                    coordinator.runIfCurrent(player, secondRevision,
                            () -> committed.add("second"))
            );

            releaseFirst.countDown();
            assertTrue(first.get(1, TimeUnit.SECONDS));
            assertTrue(second.get(1, TimeUnit.SECONDS));
            assertEquals(Arrays.asList("first", "second"), committed);
        } finally {
            releaseFirst.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void removingPlayerStateRejectsOldWorkAfterRejoin(){
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        long oldRevision = coordinator.nextRevision(player);

        coordinator.remove(player);
        assertEquals(0, coordinator.size());

        long newRevision = coordinator.nextRevision(player);
        assertFalse(coordinator.runIfCurrent(player, oldRevision, () -> {
            throw new AssertionError("离线前任务不应在重新登录后执行");
        }));
        assertTrue(coordinator.runIfCurrent(player, newRevision, () -> { }));
    }

    @Test
    void staleWorkDoesNotRecreateRemovedPlayerState(){
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        long revision = coordinator.nextRevision(player);

        coordinator.remove(player);

        assertFalse(coordinator.runIfCurrent(player, revision, () -> {
            throw new AssertionError("Removed player work must not run");
        }));
        assertEquals(0, coordinator.size());
    }

    @Test
    void concurrentStaleWorkDoesNotRecreateRemovedPlayerState() throws Exception {
        PlayerTaskCoordinator coordinator = new PlayerTaskCoordinator();
        UUID player = UUID.randomUUID();
        long revision = coordinator.nextRevision(player);
        coordinator.remove(player);

        int workerCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(workerCount);
        CountDownLatch ready = new CountDownLatch(workerCount);
        CountDownLatch start = new CountDownLatch(1);
        @SuppressWarnings("unchecked")
        Future<Boolean>[] results = new Future[workerCount];

        try {
            for(int index = 0; index < workerCount; index++){
                results[index] = executor.submit(() -> {
                    ready.countDown();
                    await(start);
                    boolean accepted = false;
                    for(int attempt = 0; attempt < 100; attempt++){
                        accepted |= coordinator.runIfCurrent(
                                player,
                                revision,
                                () -> {
                                    throw new AssertionError(
                                            "Removed player work must not run"
                                    );
                                }
                        );
                    }
                    return accepted;
                });
            }

            assertTrue(ready.await(1, TimeUnit.SECONDS));
            start.countDown();
            for(Future<Boolean> result : results){
                assertFalse(result.get(1, TimeUnit.SECONDS));
            }
            assertEquals(0, coordinator.size());
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch){
        try {
            latch.await();
        } catch (InterruptedException e){
            Thread.currentThread().interrupt();
            throw new AssertionError(e);
        }
    }
}
