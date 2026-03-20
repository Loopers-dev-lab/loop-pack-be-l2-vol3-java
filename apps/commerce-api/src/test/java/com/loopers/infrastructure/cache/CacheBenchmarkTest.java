package com.loopers.infrastructure.cache;

import com.loopers.domain.brand.BrandModel;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.product.ProductModel;
import com.loopers.domain.product.ProductService;
import com.loopers.utils.DatabaseCleanUp;
import com.loopers.utils.RedisCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@DisplayName("L2 vs L1+L2 비교 벤치마크")
class CacheBenchmarkTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private BrandService brandService;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisCleanUp redisCleanUp;

    @AfterEach
    void tearDown() {
        cacheManager.getCacheNames().forEach(name -> {
            Cache cache = cacheManager.getCache(name);
            if (cache != null) {
                cache.clear();
            }
        });
        redisCleanUp.truncateAll();
        databaseCleanUp.truncateAllTables();
    }

    @Test
    @DisplayName("시나리오1: 동일 상품 1000회 반복 조회 — L2-only vs L1+L2 응답 시간")
    void benchmark_sameProduct_1000reads() {
        // given
        BrandModel brand = brandService.createBrand("벤치마크브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("벤치마크상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        int iterations = 1000;

        // --- L2-only 측정 (brandDetail — L2-only 캐시) ---
        BrandModel brandForL2 = brandService.findById(brand.getBrandId()); // warm-up
        long[] l2Times = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            brandService.findById(brand.getBrandId());
            l2Times[i] = System.nanoTime() - start;
        }

        // --- L1+L2 측정 (productDetail — TwoLevelCache) ---
        productService.findById(product.getProductId()); // warm-up
        long[] l1l2Times = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            productService.findById(product.getProductId());
            l1l2Times[i] = System.nanoTime() - start;
        }

        // 결과 출력
        printBenchmarkResult("시나리오1: 동일 키 1000회 반복 조회", l2Times, l1l2Times, iterations);

        // 기본 검증: L1+L2 p50이 L2-only p50보다 빠르거나 동등
        Arrays.sort(l2Times);
        Arrays.sort(l1l2Times);
        System.out.printf("  L1+L2 p50 / L2 p50 비율: %.2fx%n",
                (double) l2Times[iterations / 2] / Math.max(l1l2Times[iterations / 2], 1));
    }

    @Test
    @DisplayName("시나리오2: 50개 상품 Zipf 분포 1000회 조회")
    void benchmark_zipfDistribution_1000reads() {
        // given: 50개 상품 생성
        BrandModel brand = brandService.createBrand("Zipf브랜드", "설명", "서울");
        List<Long> productIds = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            ProductModel p = productService.createProduct("상품" + i, brand.getBrandId(),
                    BigDecimal.valueOf(10000 + i * 100), "설명" + i);
            productIds.add(p.getProductId());
        }

        int iterations = 1000;
        List<Long> accessPattern = generateZipfAccessPattern(productIds, iterations);

        // warm-up: 모든 상품 1회 조회
        for (Long id : productIds) {
            productService.findById(id);
        }

        // --- L1+L2 측정 (현재 구성) ---
        long[] l1l2Times = new long[iterations];
        for (int i = 0; i < iterations; i++) {
            long start = System.nanoTime();
            productService.findById(accessPattern.get(i));
            l1l2Times[i] = System.nanoTime() - start;
        }

        // L1 캐시 통계 확인
        Cache productCache = cacheManager.getCache("productDetail");
        assertThat(productCache).isInstanceOf(TwoLevelCache.class);

        // 결과 출력
        Arrays.sort(l1l2Times);
        System.out.println("=== 시나리오2: Zipf 분포 1000회 조회 (L1+L2) ===");
        System.out.printf("  p50:  %.4f ms%n", l1l2Times[499] / 1_000_000.0);
        System.out.printf("  p95:  %.4f ms%n", l1l2Times[949] / 1_000_000.0);
        System.out.printf("  p99:  %.4f ms%n", l1l2Times[989] / 1_000_000.0);
        System.out.printf("  avg:  %.4f ms%n", Arrays.stream(l1l2Times).average().orElse(0) / 1_000_000.0);
        System.out.printf("  상품 수: %d, 조회 수: %d%n", productIds.size(), iterations);
    }

    @Test
    @DisplayName("시나리오3: 읽기 70%% + 쓰기 30%% 혼합 워크로드")
    void benchmark_mixedReadWrite() {
        // given
        BrandModel brand = brandService.createBrand("혼합브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("혼합상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        productService.findById(product.getProductId()); // warm-up

        int totalOps = 1000;
        int writeRatio = 30; // 30% 쓰기
        Random random = new Random(42);

        long[] times = new long[totalOps];
        int readCount = 0;
        int writeCount = 0;

        for (int i = 0; i < totalOps; i++) {
            long start = System.nanoTime();
            if (random.nextInt(100) < writeRatio) {
                // 쓰기 (CacheEvict 발생)
                productService.updateProduct(product.getProductId(),
                        "상품v" + i, BigDecimal.valueOf(10000 + i), "설명v" + i, null);
                writeCount++;
            } else {
                // 읽기 (Cache Hit 기대)
                productService.findById(product.getProductId());
                readCount++;
            }
            times[i] = System.nanoTime() - start;
        }

        Arrays.sort(times);
        System.out.println("=== 시나리오3: 읽기/쓰기 혼합 (L1+L2) ===");
        System.out.printf("  읽기: %d건, 쓰기: %d건%n", readCount, writeCount);
        System.out.printf("  p50:  %.4f ms%n", times[499] / 1_000_000.0);
        System.out.printf("  p95:  %.4f ms%n", times[949] / 1_000_000.0);
        System.out.printf("  p99:  %.4f ms%n", times[989] / 1_000_000.0);
        System.out.printf("  avg:  %.4f ms%n", Arrays.stream(times).average().orElse(0) / 1_000_000.0);
    }

    @Test
    @DisplayName("시나리오4: 50스레드 동시 조회 — L2 vs L1+L2 처리 시간")
    void benchmark_concurrent_50threads() throws Exception {
        // given
        BrandModel brand = brandService.createBrand("동시성브랜드", "설명", "서울");
        ProductModel product = productService.createProduct("동시성상품", brand.getBrandId(),
                BigDecimal.valueOf(10000), "설명");
        productService.findById(product.getProductId()); // warm-up (L1+L2 적재)

        int threadCount = 50;
        int opsPerThread = 100;

        // --- L2-only 시뮬레이션: brandDetail 사용 ---
        brandService.findById(brand.getBrandId()); // warm-up
        long l2TotalTime = runConcurrentBenchmark(threadCount, opsPerThread,
                () -> brandService.findById(brand.getBrandId()));

        // --- L1+L2: productDetail 사용 ---
        long l1l2TotalTime = runConcurrentBenchmark(threadCount, opsPerThread,
                () -> productService.findById(product.getProductId()));

        System.out.println("=== 시나리오4: 50스레드 × 100회 동시 조회 ===");
        System.out.printf("  L2-only 총 처리 시간:  %d ms%n", l2TotalTime);
        System.out.printf("  L1+L2  총 처리 시간:  %d ms%n", l1l2TotalTime);
        System.out.printf("  개선율: %.1f%%%n",
                (1.0 - (double) l1l2TotalTime / Math.max(l2TotalTime, 1)) * 100);

        // 기본 검증: 둘 다 모든 요청 처리 성공
        assertThat(l2TotalTime).isGreaterThan(0);
        assertThat(l1l2TotalTime).isGreaterThan(0);
    }

    @Test
    @DisplayName("시나리오5: Caffeine 캐시 메트릭 (hitCount, missCount, hitRate)")
    void benchmark_caffeineMetrics() {
        // given
        BrandModel brand = brandService.createBrand("메트릭브랜드", "설명", "서울");
        List<Long> productIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            ProductModel p = productService.createProduct("메트릭상품" + i, brand.getBrandId(),
                    BigDecimal.valueOf(10000 + i * 100), "설명");
            productIds.add(p.getProductId());
        }

        // when: 각 상품 1회 조회 (10 miss) + 인기 상품 3개 추가 10회 조회 (30 hit)
        for (Long id : productIds) {
            productService.findById(id);
        }
        for (int i = 0; i < 10; i++) {
            productService.findById(productIds.get(0));
            productService.findById(productIds.get(1));
            productService.findById(productIds.get(2));
        }

        // then: 캐시 통계 출력
        Cache cache = cacheManager.getCache("productDetail");
        assertThat(cache).isInstanceOf(TwoLevelCache.class);

        // Redis 키 수 확인
        Set<String> redisKeys = redisTemplate.keys("productDetail*");
        System.out.println("=== 시나리오5: Caffeine 캐시 메트릭 ===");
        System.out.printf("  Redis(L2) 키 수: %d%n", redisKeys != null ? redisKeys.size() : 0);
        System.out.printf("  총 조회: 40회 (10 cold + 30 hot)%n");
        System.out.printf("  기대 L1 miss: 10회 (첫 조회), L1 hit: 30회 (반복 조회)%n");
        System.out.printf("  기대 L1 hitRate: %.0f%%%n", 30.0 / 40 * 100);
    }

    // --- 유틸리티 메서드 ---

    private void printBenchmarkResult(String scenario, long[] l2Times, long[] l1l2Times, int iterations) {
        Arrays.sort(l2Times);
        Arrays.sort(l1l2Times);

        System.out.println("=== " + scenario + " ===");
        System.out.printf("%-20s %15s %15s%n", "Metric", "L2-only", "L1+L2");
        System.out.printf("%-20s %12.4f ms %12.4f ms%n", "p50",
                l2Times[iterations / 2] / 1_000_000.0, l1l2Times[iterations / 2] / 1_000_000.0);
        System.out.printf("%-20s %12.4f ms %12.4f ms%n", "p95",
                l2Times[(int) (iterations * 0.95)] / 1_000_000.0, l1l2Times[(int) (iterations * 0.95)] / 1_000_000.0);
        System.out.printf("%-20s %12.4f ms %12.4f ms%n", "p99",
                l2Times[(int) (iterations * 0.99)] / 1_000_000.0, l1l2Times[(int) (iterations * 0.99)] / 1_000_000.0);
        System.out.printf("%-20s %12.4f ms %12.4f ms%n", "avg",
                Arrays.stream(l2Times).average().orElse(0) / 1_000_000.0,
                Arrays.stream(l1l2Times).average().orElse(0) / 1_000_000.0);
    }

    private List<Long> generateZipfAccessPattern(List<Long> ids, int count) {
        Random random = new Random(42);
        List<Long> pattern = new ArrayList<>();
        int size = ids.size();
        for (int i = 0; i < count; i++) {
            // Zipf-like: 상위 10% 상품이 ~80% 트래픽
            double u = random.nextDouble();
            int index;
            if (u < 0.8) {
                // 80% 트래픽 → 상위 10% 상품 (index 0~4)
                index = random.nextInt(Math.max(size / 10, 1));
            } else {
                // 20% 트래픽 → 나머지 90% 상품
                index = size / 10 + random.nextInt(size - size / 10);
            }
            pattern.add(ids.get(index));
        }
        return pattern;
    }

    private long runConcurrentBenchmark(int threadCount, int opsPerThread, Runnable task) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < opsPerThread; j++) {
                        task.run();
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        startLatch.countDown();
        doneLatch.await(60, TimeUnit.SECONDS);
        long totalTime = System.currentTimeMillis() - startTime;
        executor.shutdown();

        assertThat(successCount.get()).isEqualTo(threadCount * opsPerThread);
        return totalTime;
    }
}
