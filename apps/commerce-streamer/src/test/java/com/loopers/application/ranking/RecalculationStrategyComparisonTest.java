package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@DisplayName("재집계 전략 비교 실험 — 4가지 방식의 성능·일관성·UX 영향 측정")
class RecalculationStrategyComparisonTest {

    @Autowired
    @Qualifier(REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final Duration TTL = Duration.ofDays(2);
    private static final LocalDate DATE = LocalDate.of(2026, 4, 8);
    private static final String MAIN_KEY = RankingKeyGenerator.dailyKey(DATE);
    private static final String SHADOW_KEY = RankingKeyGenerator.shadowKey(DATE);

    private Map<Long, Double> oldScores;
    private Map<Long, Double> newScores;

    @BeforeEach
    void setUp() {
        oldScores = new HashMap<>();
        newScores = new HashMap<>();
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    private void seedZset(String key, Map<Long, Double> scores) {
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            tuples.add(new DefaultTypedTuple<>(String.valueOf(entry.getKey()), entry.getValue()));
        }
        redisTemplate.opsForZSet().add(key, tuples);
        redisTemplate.expire(key, TTL);
    }

    private Map<Long, Double> generateScores(int count, double baseScore) {
        Map<Long, Double> scores = new HashMap<>();
        for (long i = 1; i <= count; i++) {
            scores.put(i, baseScore * i);
        }
        return scores;
    }

    // ===== 전략 A: Direct ZADD =====
    private void recalculateDirectZadd(Map<Long, Double> scores) {
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            tuples.add(new DefaultTypedTuple<>(String.valueOf(entry.getKey()), entry.getValue()));
        }
        redisTemplate.opsForZSet().add(MAIN_KEY, tuples);
    }

    // ===== 전략 B: Shadow ZSET + RENAME =====
    private void recalculateShadowRename(Map<Long, Double> scores) {
        redisTemplate.delete(SHADOW_KEY);
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            tuples.add(new DefaultTypedTuple<>(String.valueOf(entry.getKey()), entry.getValue()));
        }
        redisTemplate.opsForZSet().add(SHADOW_KEY, tuples);
        redisTemplate.expire(SHADOW_KEY, TTL);
        redisTemplate.rename(SHADOW_KEY, MAIN_KEY);
        redisTemplate.expire(MAIN_KEY, TTL);
    }

    // ===== 전략 C: DEL + ZADD =====
    private void recalculateDelZadd(Map<Long, Double> scores) {
        redisTemplate.delete(MAIN_KEY);
        Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            tuples.add(new DefaultTypedTuple<>(String.valueOf(entry.getKey()), entry.getValue()));
        }
        redisTemplate.opsForZSet().add(MAIN_KEY, tuples);
        redisTemplate.expire(MAIN_KEY, TTL);
    }

    // ===== 전략 D: Lua Script 일괄 =====
    private void recalculateLuaScript(Map<Long, Double> scores) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource("lua/ranking-recalculate.lua")));
        script.setResultType(Long.class);

        List<String> args = new ArrayList<>();
        args.add(String.valueOf(TTL.toSeconds()));
        for (Map.Entry<Long, Double> entry : scores.entrySet()) {
            args.add(String.valueOf(entry.getKey()));
            args.add(String.valueOf(entry.getValue()));
        }
        redisTemplate.execute(script, List.of(MAIN_KEY), args.toArray(new String[0]));
    }

    @Nested
    @DisplayName("Exp 1 — 재집계 소요 시간 비교 (1K / 10K / 50K 상품)")
    class PerformanceComparison {

        @Test
        @DisplayName("1,000 상품 재집계 소요 시간")
        void recalculation1K() {
            runPerformanceTest(1_000);
        }

        @Test
        @DisplayName("10,000 상품 재집계 소요 시간")
        void recalculation10K() {
            runPerformanceTest(10_000);
        }

        @Test
        @DisplayName("50,000 상품 재집계 소요 시간")
        void recalculation50K() {
            runPerformanceTest(50_000);
        }

        private void runPerformanceTest(int productCount) {
            oldScores = generateScores(productCount, 1.0);
            newScores = generateScores(productCount, 2.0);

            seedZset(MAIN_KEY, oldScores);

            long directZaddMs = measureMs(() -> recalculateDirectZadd(newScores));
            redisTemplate.delete(MAIN_KEY);
            seedZset(MAIN_KEY, oldScores);

            long shadowRenameMs = measureMs(() -> recalculateShadowRename(newScores));
            redisTemplate.delete(MAIN_KEY);
            seedZset(MAIN_KEY, oldScores);

            long delZaddMs = measureMs(() -> recalculateDelZadd(newScores));
            redisTemplate.delete(MAIN_KEY);
            seedZset(MAIN_KEY, oldScores);

            long luaMs = measureMs(() -> recalculateLuaScript(newScores));

            System.out.println("=== " + productCount + " 상품 재집계 소요 시간 ===");
            System.out.println("A. Direct ZADD:         " + directZaddMs + " ms");
            System.out.println("B. Shadow + RENAME:     " + shadowRenameMs + " ms");
            System.out.println("C. DEL + ZADD:          " + delZaddMs + " ms");
            System.out.println("D. Lua Script:          " + luaMs + " ms");
        }
    }

    @Nested
    @DisplayName("Exp 2 — 조회 일관성 검증 (재집계 중 빈 결과·혼합 상태 발생 여부)")
    class ConsistencyDuringRecalculation {

        private static final int PRODUCT_COUNT = 10_000;
        private static final int READ_THREAD_COUNT = 20;
        private static final int READ_ITERATIONS = 500;

        @Test
        @DisplayName("A. Direct ZADD — 재집계 중 조회 일관성")
        void directZaddConsistency() {
            ConsistencyResult result = measureConsistency(
                    () -> recalculateDirectZadd(newScores), "Direct ZADD");
            printConsistencyResult("A. Direct ZADD", result);
        }

        @Test
        @DisplayName("B. Shadow + RENAME — 재집계 중 조회 일관성")
        void shadowRenameConsistency() {
            ConsistencyResult result = measureConsistency(
                    () -> recalculateShadowRename(newScores), "Shadow + RENAME");
            printConsistencyResult("B. Shadow + RENAME", result);
        }

        @Test
        @DisplayName("C. DEL + ZADD — 재집계 중 조회 일관성")
        void delZaddConsistency() {
            ConsistencyResult result = measureConsistency(
                    () -> recalculateDelZadd(newScores), "DEL + ZADD");
            printConsistencyResult("C. DEL + ZADD", result);
        }

        @Test
        @DisplayName("D. Lua Script — 재집계 중 조회 일관성")
        void luaScriptConsistency() {
            ConsistencyResult result = measureConsistency(
                    () -> recalculateLuaScript(newScores), "Lua Script");
            printConsistencyResult("D. Lua Script", result);
        }

        private ConsistencyResult measureConsistency(Runnable recalculateAction, String strategyName) {
            oldScores = generateScores(PRODUCT_COUNT, 1.0);
            newScores = generateScores(PRODUCT_COUNT, 2.0);
            seedZset(MAIN_KEY, oldScores);

            AtomicInteger emptyReads = new AtomicInteger(0);
            AtomicInteger partialReads = new AtomicInteger(0);
            AtomicInteger totalReads = new AtomicInteger(0);
            AtomicInteger oldStateReads = new AtomicInteger(0);
            AtomicInteger newStateReads = new AtomicInteger(0);
            AtomicInteger mixedStateReads = new AtomicInteger(0);
            CopyOnWriteArrayList<Long> readLatencies = new CopyOnWriteArrayList<>();

            AtomicBoolean recalculating = new AtomicBoolean(true);
            CountDownLatch startLatch = new CountDownLatch(1);
            ExecutorService readExecutor = Executors.newFixedThreadPool(READ_THREAD_COUNT);

            List<CompletableFuture<Void>> readFutures = new ArrayList<>();
            for (int t = 0; t < READ_THREAD_COUNT; t++) {
                CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                    try { startLatch.await(); } catch (InterruptedException e) { return; }
                    while (recalculating.get()) {
                        long start = System.nanoTime();
                        Set<ZSetOperations.TypedTuple<String>> top20 =
                                redisTemplate.opsForZSet().reverseRangeWithScores(MAIN_KEY, 0, 19);
                        long elapsed = (System.nanoTime() - start) / 1_000_000;
                        readLatencies.add(elapsed);
                        totalReads.incrementAndGet();

                        if (top20 == null || top20.isEmpty()) {
                            emptyReads.incrementAndGet();
                        } else if (top20.size() < 20) {
                            partialReads.incrementAndGet();
                        } else {
                            boolean hasOld = false;
                            boolean hasNew = false;
                            for (ZSetOperations.TypedTuple<String> tuple : top20) {
                                Long id = Long.parseLong(tuple.getValue());
                                Double score = tuple.getScore();
                                double expectedOld = 1.0 * id;
                                double expectedNew = 2.0 * id;
                                if (Math.abs(score - expectedOld) < 0.01) hasOld = true;
                                if (Math.abs(score - expectedNew) < 0.01) hasNew = true;
                            }
                            if (hasOld && hasNew) mixedStateReads.incrementAndGet();
                            else if (hasOld) oldStateReads.incrementAndGet();
                            else if (hasNew) newStateReads.incrementAndGet();
                        }
                    }
                }, readExecutor);
                readFutures.add(future);
            }

            startLatch.countDown();
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
            recalculateAction.run();
            recalculating.set(false);

            CompletableFuture.allOf(readFutures.toArray(new CompletableFuture[0])).join();
            readExecutor.shutdown();

            List<Long> sorted = new ArrayList<>(readLatencies);
            sorted.sort(Long::compareTo);

            return new ConsistencyResult(
                    totalReads.get(),
                    emptyReads.get(),
                    partialReads.get(),
                    oldStateReads.get(),
                    newStateReads.get(),
                    mixedStateReads.get(),
                    sorted.isEmpty() ? 0 : sorted.get(sorted.size() / 2),
                    sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.95)),
                    sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1)
            );
        }

        private void printConsistencyResult(String strategy, ConsistencyResult r) {
            System.out.println("=== " + strategy + " — 조회 일관성 ===");
            System.out.println("총 조회:          " + r.totalReads);
            System.out.println("빈 결과 (empty):  " + r.emptyReads + " (" + pct(r.emptyReads, r.totalReads) + "%)");
            System.out.println("부분 결과:        " + r.partialReads + " (" + pct(r.partialReads, r.totalReads) + "%)");
            System.out.println("구(old) 상태:     " + r.oldStateReads);
            System.out.println("신(new) 상태:     " + r.newStateReads);
            System.out.println("혼합 상태:        " + r.mixedStateReads + " (" + pct(r.mixedStateReads, r.totalReads) + "%)");
            System.out.println("p50 latency:      " + r.p50Ms + " ms");
            System.out.println("p95 latency:      " + r.p95Ms + " ms");
            System.out.println("max latency:      " + r.maxMs + " ms");
            System.out.println();
        }

        private String pct(int numerator, int denominator) {
            if (denominator == 0) return "0.0";
            return String.format("%.1f", (double) numerator / denominator * 100);
        }
    }

    @Nested
    @DisplayName("Exp 3 — 순위 역전 검증 (재집계 중 Top-1이 바뀌는 순간 포착)")
    class RankInversionDetection {

        @Test
        @DisplayName("재집계 전후 Top-1이 바뀌는 시나리오에서 각 전략의 동작")
        void detectRankInversion() {
            int productCount = 5_000;
            oldScores = new HashMap<>();
            newScores = new HashMap<>();
            for (long i = 1; i <= productCount; i++) {
                oldScores.put(i, (double) (productCount - i + 1));
                newScores.put(i, (double) i);
            }

            System.out.println("=== 순위 역전 시나리오 ===");
            System.out.println("재집계 전 Top-1: 상품 1 (score=" + oldScores.get(1L) + ")");
            System.out.println("재집계 후 Top-1: 상품 " + productCount + " (score=" + newScores.get((long) productCount) + ")");
            System.out.println();

            String[] strategies = {"A. Direct ZADD", "B. Shadow + RENAME", "C. DEL + ZADD", "D. Lua Script"};
            Runnable[] actions = {
                    () -> recalculateDirectZadd(newScores),
                    () -> recalculateShadowRename(newScores),
                    () -> recalculateDelZadd(newScores),
                    () -> recalculateLuaScript(newScores)
            };

            for (int s = 0; s < strategies.length; s++) {
                seedZset(MAIN_KEY, oldScores);
                AtomicBoolean running = new AtomicBoolean(true);
                CopyOnWriteArrayList<String> top1History = new CopyOnWriteArrayList<>();
                AtomicInteger emptyCount = new AtomicInteger(0);

                int strategyIndex = s;
                CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
                    while (running.get()) {
                        Set<ZSetOperations.TypedTuple<String>> top1 =
                                redisTemplate.opsForZSet().reverseRangeWithScores(MAIN_KEY, 0, 0);
                        if (top1 == null || top1.isEmpty()) {
                            emptyCount.incrementAndGet();
                            top1History.add("EMPTY");
                        } else {
                            ZSetOperations.TypedTuple<String> first = top1.iterator().next();
                            top1History.add(first.getValue() + "(s=" + String.format("%.0f", first.getScore()) + ")");
                        }
                    }
                });

                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                actions[strategyIndex].run();
                running.set(false);
                reader.join();

                Set<String> uniqueTop1 = new HashSet<>(top1History);
                System.out.println(strategies[s] + ":");
                System.out.println("  총 조회: " + top1History.size() + ", 빈 결과: " + emptyCount.get());
                System.out.println("  Top-1 관측값: " + uniqueTop1);
                System.out.println("  최종 Top-1: " + top1History.get(top1History.size() - 1));
                System.out.println();

                redisTemplate.delete(MAIN_KEY);
                redisTemplate.delete(SHADOW_KEY);
            }
        }
    }

    private long measureMs(Runnable action) {
        long start = System.currentTimeMillis();
        action.run();
        return System.currentTimeMillis() - start;
    }

    record ConsistencyResult(
            int totalReads,
            int emptyReads,
            int partialReads,
            int oldStateReads,
            int newStateReads,
            int mixedStateReads,
            long p50Ms,
            long p95Ms,
            long maxMs
    ) {}
}
