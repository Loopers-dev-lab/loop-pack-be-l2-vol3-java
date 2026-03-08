package com.loopers.support;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import lombok.experimental.UtilityClass;

@UtilityClass
public final class ConcurrentTestHelper {

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static ConcurrentResult executeConcurrently(int threadCount, ThrowingRunnable task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);
        List<Exception> exceptions = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.execute(() -> {
                try {
                    task.run();
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failCount.incrementAndGet();
                    exceptions.add(e);
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();
        executorService.shutdown();

        return new ConcurrentResult(successCount.get(), failCount.get(), exceptions);
    }

    public record ConcurrentResult(
            int successCount,
            int failCount,
            List<Exception> exceptions
    ) {}
}
