package com.loopers.infrastructure.outbox;

import com.loopers.application.like.LikeApplicationService;
import com.loopers.config.TestRedisConfiguration;
import com.loopers.domain.common.Money;
import com.loopers.domain.outbox.OutboxEvent;
import com.loopers.domain.outbox.OutboxEventRepository;
import com.loopers.domain.outbox.OutboxStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.Stock;
import com.loopers.event.EventType;
import com.loopers.utils.DatabaseCleanUp;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Import(TestRedisConfiguration.class)
@DisplayName("Outbox 통합 테스트")
class OutboxIntegrationTest {

    @Autowired
    private LikeApplicationService likeApplicationService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private DatabaseCleanUp databaseCleanUp;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @AfterEach
    void tearDown() {
        databaseCleanUp.truncateAllTables();
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushAll();
            return null;
        }, true);
    }

    @Test
    @DisplayName("좋아요 생성 시 Outbox에 이벤트가 저장된다")
    void 좋아요_생성_시_Outbox_이벤트_저장() {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );
        Long userId = 1L;

        // Act
        likeApplicationService.like(userId, product.getId());

        // Assert
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxEventRepository.findPendingEvents(100);
            assertThat(events).isNotEmpty();

            OutboxEvent outboxEvent = events.stream()
                .filter(e -> e.getEventType().equals(EventType.LIKE_CREATED.name()))
                .findFirst()
                .orElseThrow();

            assertThat(outboxEvent.getAggregateId()).isEqualTo(String.valueOf(product.getId()));
            assertThat(outboxEvent.getStatus()).isEqualTo(OutboxStatus.PENDING);
        });
    }

    @Test
    @DisplayName("좋아요 취소 시 Outbox에 이벤트가 저장된다")
    void 좋아요_취소_시_Outbox_이벤트_저장() {
        // Arrange
        Product product = productRepository.save(
            Product.create(1L, "테스트 상품", "설명", new Money(10000), new Stock(100), "http://image.url")
        );
        Long userId = 1L;
        likeApplicationService.like(userId, product.getId());

        // Act
        likeApplicationService.unlike(userId, product.getId());

        // Assert
        await().atMost(2, TimeUnit.SECONDS).untilAsserted(() -> {
            List<OutboxEvent> events = outboxEventRepository.findPendingEvents(100);

            boolean hasCanceledEvent = events.stream()
                .anyMatch(e -> e.getEventType().equals(EventType.LIKE_CANCELED.name())
                    && e.getAggregateId().equals(String.valueOf(product.getId())));

            assertThat(hasCanceledEvent).isTrue();
        });
    }
}
