package com.loopers.application.event;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.application.like.LikeResult;
import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.common.Money;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.infrastructure.cache.LikeCountCacheService;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("이벤트 리스너 통합 테스트")
class EventListenerIntegrationTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private LikeCountCacheService likeCountCacheService;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        // Redis 정리
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("좋아요 생성 후 Redis 카운터가 비동기로 갱신된다")
    void 좋아요_생성_후_Redis_카운터_갱신() {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );
        Long userId = 1L;

        // Act
        likeApplicationService.like(userId, product.getId());

        // Assert - Awaitility로 비동기 갱신 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long count = likeCountCacheService.get(product.getId()).orElse(0L);
            assertThat(count).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("좋아요 취소 후 Redis 카운터가 비동기로 감소한다")
    void 좋아요_취소_후_Redis_카운터_감소() {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );
        Long userId = 1L;
        likeApplicationService.like(userId, product.getId());

        // 좋아요 카운터가 증가할 때까지 대기
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long count = likeCountCacheService.get(product.getId()).orElse(0L);
            assertThat(count).isEqualTo(1L);
        });

        // Act
        likeApplicationService.unlike(userId, product.getId());

        // Assert - Awaitility로 비동기 갱신 확인
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long count = likeCountCacheService.get(product.getId()).orElse(0L);
            assertThat(count).isEqualTo(0L);
        });
    }

    @Test
    @DisplayName("여러 사용자가 좋아요하면 Redis 카운터가 정확하게 증가한다")
    void 여러_사용자_좋아요_후_Redis_카운터_정확성() {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );
        int userCount = 5;

        // Act
        for (int i = 1; i <= userCount; i++) {
            likeApplicationService.like((long) i, product.getId());
        }

        // Assert
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            Long count = likeCountCacheService.get(product.getId()).orElse(0L);
            assertThat(count).isEqualTo((long) userCount);
        });
    }

    @Test
    @DisplayName("좋아요 DB 저장과 Redis 캐시 갱신이 분리되어 있어 캐시 실패해도 좋아요는 유지된다")
    void 좋아요_저장_성공_검증() {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );
        Long userId = 1L;

        // Act
        LikeResult result = likeApplicationService.like(userId, product.getId());

        // Assert - 좋아요 결과가 즉시 반환됨 (이벤트 처리와 무관)
        assertThat(result).isNotNull();
        assertThat(result.userId()).isEqualTo(userId);
        assertThat(result.productId()).isEqualTo(product.getId());
    }
}
