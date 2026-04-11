package com.loopers.application.ranking;

import com.loopers.domain.ranking.RankingKeyGenerator;
import com.loopers.testcontainers.RedisTestContainersConfig;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.DefaultTypedTuple;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.loopers.config.redis.RedisConfig.REDIS_TEMPLATE_MASTER;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(RedisTestContainersConfig.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("시간 단위 랭킹 전략 비교 실험 — 4가지 방식의 쓰기·읽기·메모리·일관성 측정")
class HourlyRankingStrategyComparisonTest {

    @Autowired
    @Qualifier(REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private RedisCleanUp redisCleanUp;

    private static final Duration DAILY_TTL = Duration.ofDays(2);
    private static final Duration HOURLY_TTL = Duration.ofHours(2);
    private static final LocalDate DATE = LocalDate.of(2026, 4, 8);
    private static final int HOUR = 14;
    private static final String DAILY_KEY = RankingKeyGenerator.dailyKey(DATE);
    private static final String HOURLY_KEY = DAILY_KEY + ":" + String.format("%02d", HOUR);
    private static final Path RESULT_FILE = Paths.get("build", "hourly-strategy-comparison-result.txt");

    private final List<String> results = new CopyOnWriteArrayList<>();
    private DefaultRedisScript<Double> dualWriteScript;

    private void out(String line) {
        results.add(line);
    }

    @BeforeEach
    void setUp() {
        dualWriteScript = new DefaultRedisScript<>();
        dualWriteScript.setScriptText(
                "local dailyKey = KEYS[1]\n" +
                "local hourlyKey = KEYS[2]\n" +
                "local member = ARGV[1]\n" +
                "local increment = tonumber(ARGV[2])\n" +
                "local dailyTtl = tonumber(ARGV[3])\n" +
                "local hourlyTtl = tonumber(ARGV[4])\n" +
                "local newScore = redis.call('ZINCRBY', dailyKey, increment, member)\n" +
                "if redis.call('TTL', dailyKey) == -1 then\n" +
                "    redis.call('EXPIRE', dailyKey, dailyTtl)\n" +
                "end\n" +
                "redis.call('ZINCRBY', hourlyKey, increment, member)\n" +
                "if redis.call('TTL', hourlyKey) == -1 then\n" +
                "    redis.call('EXPIRE', hourlyKey, hourlyTtl)\n" +
                "end\n" +
                "return newScore"
        );
        dualWriteScript.setResultType(Double.class);
    }

    @AfterEach
    void tearDown() {
        redisCleanUp.truncateAll();
    }

    @AfterAll
    void writeResults() throws IOException {
        Files.createDirectories(RESULT_FILE.getParent());
        Files.write(RESULT_FILE, results, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    @Nested
    @DisplayName("Exp 1 — 쓰기 처리량 비교 (10K 이벤트)")
    class WriteThroughput {

        private static final int EVENT_COUNT = 10_000;
        private static final int PRODUCT_RANGE = 1_000;

        @Test
        @DisplayName("4가지 전략의 10K 이벤트 쓰기 성능 비교")
        void compareWriteThroughput() {
            Random random = new Random(42);
            List<long[]> events = new ArrayList<>(EVENT_COUNT);
            for (int i = 0; i < EVENT_COUNT; i++) {
                long productId = random.nextInt(PRODUCT_RANGE) + 1;
                events.add(new long[]{productId});
            }
            double score = 0.1;

            long strategyAMs = measureWriteA(events, score);
            cleanup();

            long strategyBMs = measureWriteB(events, score);
            cleanup();

            long strategyCMs = measureWriteC(events, score);
            cleanup();

            long strategyDMs = measureWriteD(events, score);
            cleanup();

            out("=== Exp 1: 쓰기 처리량 비교 (" + EVENT_COUNT + " events, " + PRODUCT_RANGE + " products) ===");
            out("A. Dual-Write Lua:        " + strategyAMs + " ms (" + throughput(strategyAMs) + " events/sec)");
            out("B. Hourly Only + Union:   " + strategyBMs + " ms (" + throughput(strategyBMs) + " events/sec)");
            out("C. Hourly + Merge:        " + strategyCMs + " ms (" + throughput(strategyCMs) + " events/sec)");
            out("D. Daily + Snapshot:      " + strategyDMs + " ms (" + throughput(strategyDMs) + " events/sec)");
            out("");
        }

        private long measureWriteA(List<long[]> events, double score) {
            long start = System.currentTimeMillis();
            for (long[] event : events) {
                redisTemplate.execute(
                        dualWriteScript,
                        List.of(DAILY_KEY, HOURLY_KEY),
                        String.valueOf(event[0]),
                        String.valueOf(score),
                        String.valueOf(DAILY_TTL.toSeconds()),
                        String.valueOf(HOURLY_TTL.toSeconds())
                );
            }
            return System.currentTimeMillis() - start;
        }

        private long measureWriteB(List<long[]> events, double score) {
            long start = System.currentTimeMillis();
            for (long[] event : events) {
                redisTemplate.opsForZSet().incrementScore(
                        HOURLY_KEY, String.valueOf(event[0]), score);
            }
            return System.currentTimeMillis() - start;
        }

        private long measureWriteC(List<long[]> events, double score) {
            long start = System.currentTimeMillis();
            for (long[] event : events) {
                redisTemplate.opsForZSet().incrementScore(
                        HOURLY_KEY, String.valueOf(event[0]), score);
            }
            return System.currentTimeMillis() - start;
        }

        private long measureWriteD(List<long[]> events, double score) {
            DefaultRedisScript<Double> singleWriteScript = new DefaultRedisScript<>();
            singleWriteScript.setScriptText(
                    "local key = KEYS[1]\n" +
                    "local member = ARGV[1]\n" +
                    "local increment = tonumber(ARGV[2])\n" +
                    "local ttl = tonumber(ARGV[3])\n" +
                    "local newScore = redis.call('ZINCRBY', key, increment, member)\n" +
                    "if redis.call('TTL', key) == -1 then\n" +
                    "    redis.call('EXPIRE', key, ttl)\n" +
                    "end\n" +
                    "return newScore"
            );
            singleWriteScript.setResultType(Double.class);
            long start = System.currentTimeMillis();
            for (long[] event : events) {
                redisTemplate.execute(
                        singleWriteScript,
                        List.of(DAILY_KEY),
                        String.valueOf(event[0]),
                        String.valueOf(score),
                        String.valueOf(DAILY_TTL.toSeconds())
                );
            }
            return System.currentTimeMillis() - start;
        }

        private String throughput(long ms) {
            if (ms == 0) return "N/A";
            return String.format("%,d", EVENT_COUNT * 1000L / ms);
        }

        private void cleanup() {
            redisTemplate.delete(DAILY_KEY);
            redisTemplate.delete(HOURLY_KEY);
        }
    }

    @Nested
    @DisplayName("Exp 2 — 읽기 지연 비교 (Top-20 조회 1K회)")
    class ReadLatency {

        private static final int READ_COUNT = 1_000;
        private static final int PRODUCT_COUNT = 10_000;

        @Test
        @DisplayName("시간 단위 Top-20 읽기 지연 비교")
        void compareReadLatency() {
            seedProducts(DAILY_KEY, PRODUCT_COUNT);
            seedProducts(HOURLY_KEY, PRODUCT_COUNT);

            List<Long> latencyA = measureReadLatency(HOURLY_KEY);
            List<Long> latencyB = measureReadLatency(HOURLY_KEY);

            seedDailyFromUnion();
            List<Long> latencyBDaily = measureDailyUnionRead();

            List<Long> latencyD = measureReadLatency(HOURLY_KEY);

            out("=== Exp 2: 읽기 지연 비교 (Top-20, 1K회, " + PRODUCT_COUNT + " products) ===");
            out(formatLatency("A. Dual-Write (hourly key)", latencyA));
            out(formatLatency("B. Hourly Only (hourly key)", latencyB));
            out(formatLatency("B. Hourly Only (daily ZUNIONSTORE)", latencyBDaily));
            out(formatLatency("D. Snapshot (hourly key)", latencyD));
            out("");
        }

        private void seedProducts(String key, int count) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (int i = 1; i <= count; i++) {
                tuples.add(new DefaultTypedTuple<>(String.valueOf(i), (double) i));
            }
            redisTemplate.opsForZSet().add(key, tuples);
            redisTemplate.expire(key, DAILY_TTL);
        }

        private void seedDailyFromUnion() {
            for (int h = 0; h < 24; h++) {
                String hKey = DAILY_KEY + ":" + String.format("%02d", h);
                if (h == HOUR) continue;
                Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
                for (int i = 1; i <= 500; i++) {
                    tuples.add(new DefaultTypedTuple<>(String.valueOf(i), (double) i * 0.1));
                }
                redisTemplate.opsForZSet().add(hKey, tuples);
            }
        }

        private List<Long> measureReadLatency(String key) {
            List<Long> latencies = new ArrayList<>(READ_COUNT);
            for (int i = 0; i < READ_COUNT; i++) {
                long start = System.nanoTime();
                redisTemplate.opsForZSet().reverseRangeWithScores(key, 0, 19);
                latencies.add(System.nanoTime() - start);
            }
            return latencies;
        }

        private List<Long> measureDailyUnionRead() {
            List<String> hourlyKeys = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                hourlyKeys.add(DAILY_KEY + ":" + String.format("%02d", h));
            }
            String tempDailyKey = DAILY_KEY + ":union_temp";

            List<Long> latencies = new ArrayList<>(READ_COUNT);
            for (int i = 0; i < READ_COUNT; i++) {
                long start = System.nanoTime();
                redisTemplate.opsForZSet().unionAndStore(
                        hourlyKeys.get(0),
                        hourlyKeys.subList(1, hourlyKeys.size()),
                        tempDailyKey
                );
                redisTemplate.opsForZSet().reverseRangeWithScores(tempDailyKey, 0, 19);
                redisTemplate.delete(tempDailyKey);
                latencies.add(System.nanoTime() - start);
            }
            return latencies;
        }

        private String formatLatency(String strategy, List<Long> latencies) {
            List<Long> sorted = new ArrayList<>(latencies);
            Collections.sort(sorted);
            long p50 = sorted.get(sorted.size() / 2) / 1_000;
            long p95 = sorted.get((int) (sorted.size() * 0.95)) / 1_000;
            long max = sorted.get(sorted.size() - 1) / 1_000;
            long avg = sorted.stream().mapToLong(Long::longValue).sum() / sorted.size() / 1_000;
            return strategy + ": avg=" + avg + "us, p50=" + p50 + "us, p95=" + p95 + "us, max=" + max + "us";
        }
    }

    @Nested
    @DisplayName("Exp 3 — 메모리 사용량 비교")
    class MemoryUsage {

        private static final int PRODUCT_COUNT = 10_000;

        @Test
        @DisplayName("daily + hourly 키 구성에 따른 메모리 사용량 비교")
        void compareMemoryUsage() {
            long baselineMemory = getUsedMemory();

            seedProducts(DAILY_KEY, PRODUCT_COUNT);
            long afterDaily = getUsedMemory();
            long dailyOnlyMemory = afterDaily - baselineMemory;

            seedProducts(HOURLY_KEY, PRODUCT_COUNT);
            long afterDailyPlusHourly = getUsedMemory();
            long dualWriteMemory = afterDailyPlusHourly - baselineMemory;

            redisCleanUp.truncateAll();
            long afterCleanup = getUsedMemory();

            for (int h = 0; h < 24; h++) {
                String hKey = DAILY_KEY + ":" + String.format("%02d", h);
                int perHourCount = PRODUCT_COUNT / 3;
                seedProducts(hKey, perHourCount);
            }
            long after24Hourly = getUsedMemory();
            long hourlyOnly24Memory = after24Hourly - afterCleanup;

            out("=== Exp 3: 메모리 사용량 비교 (" + PRODUCT_COUNT + " products) ===");
            out("D. Daily Only (기존):                " + formatBytes(dailyOnlyMemory));
            out("A. Daily + 1 Hourly (Dual-Write):    " + formatBytes(dualWriteMemory));
            out("B. 24 Hourly Keys (Hourly Only):     " + formatBytes(hourlyOnly24Memory));
            out("A 추가 비용 (vs Daily Only):         " + formatBytes(dualWriteMemory - dailyOnlyMemory));
            out("B 추가 비용 (vs Daily Only):         " + formatBytes(hourlyOnly24Memory));
            out("");
        }

        private void seedProducts(String key, int count) {
            Set<ZSetOperations.TypedTuple<String>> tuples = new HashSet<>();
            for (int i = 1; i <= count; i++) {
                tuples.add(new DefaultTypedTuple<>(String.valueOf(i), (double) i));
            }
            redisTemplate.opsForZSet().add(key, tuples);
        }

        private long getUsedMemory() {
            RedisConnection connection = redisTemplate.getConnectionFactory().getConnection();
            try {
                Properties info = connection.serverCommands().info("memory");
                return Long.parseLong(info.getProperty("used_memory"));
            } finally {
                connection.close();
            }
        }

        private String formatBytes(long bytes) {
            if (Math.abs(bytes) < 1024) return bytes + " B";
            if (Math.abs(bytes) < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    @Nested
    @DisplayName("Exp 4 — 일관성 검증 (hourly 합산 == daily 점수)")
    class ConsistencyVerification {

        private static final int EVENT_COUNT = 5_000;
        private static final int PRODUCT_RANGE = 500;

        @Test
        @DisplayName("A. Dual-Write — hourly 점수 == daily 점수 (동일 시간대)")
        void dualWriteConsistency() {
            Random random = new Random(42);
            Map<Long, Double> expectedScores = new HashMap<>();

            for (int i = 0; i < EVENT_COUNT; i++) {
                long productId = random.nextInt(PRODUCT_RANGE) + 1;
                double score = 0.1 + random.nextDouble() * 0.9;
                expectedScores.merge(productId, score, Double::sum);

                redisTemplate.execute(
                        dualWriteScript,
                        List.of(DAILY_KEY, HOURLY_KEY),
                        String.valueOf(productId),
                        String.valueOf(score),
                        String.valueOf(DAILY_TTL.toSeconds()),
                        String.valueOf(HOURLY_TTL.toSeconds())
                );
            }

            int mismatchCount = 0;
            double maxDiff = 0;
            for (Map.Entry<Long, Double> entry : expectedScores.entrySet()) {
                Double dailyScore = redisTemplate.opsForZSet().score(DAILY_KEY, String.valueOf(entry.getKey()));
                Double hourlyScore = redisTemplate.opsForZSet().score(HOURLY_KEY, String.valueOf(entry.getKey()));

                assertThat(dailyScore).isNotNull();
                assertThat(hourlyScore).isNotNull();

                double diff = Math.abs(dailyScore - hourlyScore);
                if (diff > 0.001) {
                    mismatchCount++;
                    maxDiff = Math.max(maxDiff, diff);
                }
            }

            Long dailySize = redisTemplate.opsForZSet().zCard(DAILY_KEY);
            Long hourlySize = redisTemplate.opsForZSet().zCard(HOURLY_KEY);

            out("=== Exp 4-A: Dual-Write 일관성 검증 ===");
            out("이벤트 수:           " + EVENT_COUNT);
            out("고유 상품 수:        " + expectedScores.size());
            out("Daily ZSET 크기:     " + dailySize);
            out("Hourly ZSET 크기:    " + hourlySize);
            out("불일치 상품 수:      " + mismatchCount);
            out("최대 점수 차이:      " + String.format("%.6f", maxDiff));
            out("");

            assertThat(dailySize).isEqualTo(hourlySize);
            assertThat(mismatchCount).isZero();
        }

        @Test
        @DisplayName("B. Hourly Only — ZUNIONSTORE daily == 직접 daily의 점수 차이")
        void hourlyOnlyUnionConsistency() {
            Random random = new Random(42);
            Map<Long, Double> expectedScores = new HashMap<>();

            for (int i = 0; i < EVENT_COUNT; i++) {
                long productId = random.nextInt(PRODUCT_RANGE) + 1;
                double score = 0.1 + random.nextDouble() * 0.9;
                int hour = random.nextInt(24);
                String hKey = DAILY_KEY + ":" + String.format("%02d", hour);
                expectedScores.merge(productId, score, Double::sum);
                redisTemplate.opsForZSet().incrementScore(hKey, String.valueOf(productId), score);
            }

            List<String> hourlyKeys = new ArrayList<>();
            for (int h = 0; h < 24; h++) {
                hourlyKeys.add(DAILY_KEY + ":" + String.format("%02d", h));
            }
            String unionKey = DAILY_KEY + ":union";
            redisTemplate.opsForZSet().unionAndStore(
                    hourlyKeys.get(0),
                    hourlyKeys.subList(1, hourlyKeys.size()),
                    unionKey
            );

            int mismatchCount = 0;
            double maxDiff = 0;
            for (Map.Entry<Long, Double> entry : expectedScores.entrySet()) {
                Double unionScore = redisTemplate.opsForZSet().score(unionKey, String.valueOf(entry.getKey()));
                if (unionScore == null) {
                    mismatchCount++;
                    continue;
                }
                double diff = Math.abs(unionScore - entry.getValue());
                if (diff > 0.01) {
                    mismatchCount++;
                    maxDiff = Math.max(maxDiff, diff);
                }
            }

            Long unionSize = redisTemplate.opsForZSet().zCard(unionKey);

            out("=== Exp 4-B: Hourly Only + ZUNIONSTORE 일관성 검증 ===");
            out("이벤트 수:           " + EVENT_COUNT);
            out("고유 상품 수:        " + expectedScores.size());
            out("Union ZSET 크기:     " + unionSize);
            out("불일치 상품 수:      " + mismatchCount);
            out("최대 점수 차이:      " + String.format("%.6f", maxDiff));
            out("");

            assertThat(mismatchCount).isZero();
        }

        @Test
        @DisplayName("D. Snapshot — 스냅샷 시점 이후 쓰기로 인한 stale 정도 측정")
        void snapshotStaleness() {
            Random random = new Random(42);
            for (int i = 0; i < 5_000; i++) {
                long productId = random.nextInt(PRODUCT_RANGE) + 1;
                redisTemplate.opsForZSet().incrementScore(DAILY_KEY, String.valueOf(productId), 0.1);
            }

            Set<ZSetOperations.TypedTuple<String>> snapshot = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(DAILY_KEY, 0, -1);
            Set<ZSetOperations.TypedTuple<String>> snapshotTuples = new HashSet<>();
            for (ZSetOperations.TypedTuple<String> tuple : snapshot) {
                snapshotTuples.add(new DefaultTypedTuple<>(tuple.getValue(), tuple.getScore()));
            }
            redisTemplate.opsForZSet().add(HOURLY_KEY, snapshotTuples);

            for (int i = 0; i < 2_000; i++) {
                long productId = random.nextInt(PRODUCT_RANGE) + 1;
                redisTemplate.opsForZSet().incrementScore(DAILY_KEY, String.valueOf(productId), 0.5);
            }

            Set<ZSetOperations.TypedTuple<String>> currentDaily = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(DAILY_KEY, 0, 19);
            Set<ZSetOperations.TypedTuple<String>> staleSnapshot = redisTemplate.opsForZSet()
                    .reverseRangeWithScores(HOURLY_KEY, 0, 19);

            List<String> currentTop20 = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> t : currentDaily) {
                currentTop20.add(t.getValue());
            }
            List<String> snapshotTop20 = new ArrayList<>();
            for (ZSetOperations.TypedTuple<String> t : staleSnapshot) {
                snapshotTop20.add(t.getValue());
            }

            int rankMismatch = 0;
            for (int i = 0; i < Math.min(currentTop20.size(), snapshotTop20.size()); i++) {
                if (!currentTop20.get(i).equals(snapshotTop20.get(i))) {
                    rankMismatch++;
                }
            }

            out("=== Exp 4-D: Snapshot Staleness 측정 ===");
            out("스냅샷 후 추가 이벤트: 2,000");
            out("Top-20 순위 불일치:  " + rankMismatch + " / 20");
            out("현재 Top-5:          " + currentTop20.subList(0, Math.min(5, currentTop20.size())));
            out("스냅샷 Top-5:        " + snapshotTop20.subList(0, Math.min(5, snapshotTop20.size())));
            out("");
        }
    }
}
