package com.loopers.infrastructure.cache;

import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.common.Money;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("ProductLikeCountQueryService 테스트")
class ProductLikeCountQueryServiceTest {

    @Autowired
    private ProductLikeCountQueryService productLikeCountQueryService;

    @Autowired
    private LikeCountCacheService likeCountCacheService;

    @Autowired
    private LikeRepository likeRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @BeforeEach
    void setUp() {
        cleanUpRedis();
    }

    @AfterEach
    void tearDown() {
        cleanUpRedis();
        databaseCleanUp.truncateAllTables();
    }

    private void cleanUpRedis() {
        // 샤딩된 캐시 키: {product:*}:like:*
        var cacheKeys = redisTemplate.keys("{product:*}:like:*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            redisTemplate.delete(cacheKeys);
        }
        // 분산 락 키: lock:*
        var lockKeys = redisTemplate.keys("lock:*");
        if (lockKeys != null && !lockKeys.isEmpty()) {
            redisTemplate.delete(lockKeys);
        }
    }

    private Product createProduct() {
        return productRepository.save(
            Product.create(1L, "테스트 상품", "설명",
                new Money(10000), new Stock(100), "http://image.url")
        );
    }

    @Nested
    @DisplayName("getLikeCount 테스트")
    class GetLikeCount {

        @Test
        @DisplayName("성공 - 캐시 히트")
        void 캐시_히트() {
            // Arrange
            Long productId = 1L;
            likeCountCacheService.set(productId, 50L);

            // Act
            long result = productLikeCountQueryService.getLikeCount(productId);

            // Assert
            assertThat(result).isEqualTo(50L);
        }

        @Test
        @DisplayName("성공 - 캐시 미스 시 DB 조회 후 캐시 저장")
        void 캐시_미스_DB_조회() {
            // Arrange
            Product product = createProduct();

            // Act
            long result = productLikeCountQueryService.getLikeCount(product.getId());

            // Assert
            assertThat(result).isEqualTo(0L);
            // 캐시에 저장되었는지 확인
            assertThat(likeCountCacheService.get(product.getId())).isPresent();
        }
    }

    @Nested
    @DisplayName("getLikeCounts (다건 조회) 테스트")
    class GetLikeCounts {

        @Test
        @DisplayName("성공 - 캐시 히트와 미스 혼합")
        void 캐시_히트_미스_혼합() {
            // Arrange
            Product product1 = createProduct();
            Product product2 = productRepository.save(
                Product.create(1L, "테스트 상품2", "설명",
                    new Money(20000), new Stock(200), "http://image2.url")
            );
            likeCountCacheService.set(product1.getId(), 10L);
            // product2는 캐시에 없음

            // Act
            Map<Long, Long> result = productLikeCountQueryService.getLikeCounts(
                List.of(product1.getId(), product2.getId())
            );

            // Assert
            assertThat(result.get(product1.getId())).isEqualTo(10L);
            assertThat(result.get(product2.getId())).isEqualTo(0L);
        }
    }

    @Nested
    @DisplayName("Thundering Herd 방지 테스트")
    class ThunderingHerdPrevention {

        @Test
        @DisplayName("동시 캐시 미스 시 Single Flight 동작")
        void 동시_캐시_미스_단일_DB조회() throws InterruptedException {
            // Arrange
            Product product = createProduct();
            int threadCount = 10;
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch endLatch = new CountDownLatch(threadCount);
            AtomicInteger resultCount = new AtomicInteger(0);

            // Act
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        long count = productLikeCountQueryService.getLikeCount(product.getId());
                        resultCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        endLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            endLatch.await();
            executor.shutdown();

            // Assert
            assertThat(resultCount.get()).isEqualTo(threadCount);
            // 캐시에 값이 저장되어 있어야 함
            assertThat(likeCountCacheService.get(product.getId())).isPresent();
        }
    }
}
