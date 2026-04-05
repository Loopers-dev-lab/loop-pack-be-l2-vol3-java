package com.loopers.interfaces.event.queue;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;

import com.loopers.application.order.PlaceOrderCommand;
import com.loopers.application.order.PlaceOrderCommand.OrderItemCommand;
import com.loopers.application.order.PlaceOrderUseCase;
import com.loopers.config.redis.RedisConfig;
import com.loopers.support.BaseIntegrationTest;

@DisplayName("EntryTokenEventListener 통합 테스트")
class EntryTokenEventListenerIntegrationTest extends BaseIntegrationTest {

    private static final String TOKEN_KEY_PREFIX = "entry-token:";

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    @Qualifier(RedisConfig.REDIS_TEMPLATE_MASTER)
    private RedisTemplate<String, String> redisTemplate;

    private Long brandId;
    private Long productId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
        productId = createProduct(brandId, "상품", 10000L, 100L);
    }

    @DisplayName("주문 생성 이벤트가 발행되면,")
    @Nested
    class OrderPlacedEvent {

        @DisplayName("해당 유저의 입장 토큰이 삭제된다.")
        @Test
        void deletesEntryToken_whenOrderPlaced() {
            // arrange
            Long userId = 1L;
            redisTemplate.opsForValue().set(TOKEN_KEY_PREFIX + userId, "test-entry-token");

            PlaceOrderCommand command = new PlaceOrderCommand(
                    userId,
                    List.of(new OrderItemCommand(productId, 1L)),
                    null
            );

            // act
            placeOrderUseCase.execute(command);

            // assert — AFTER_COMMIT + @Async이므로 비동기 대기
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertThat(redisTemplate.opsForValue().get(TOKEN_KEY_PREFIX + userId)).isNull()
            );
        }

        @DisplayName("입장 토큰이 없어도, 주문은 정상적으로 처리된다.")
        @Test
        void orderSucceeds_whenNoEntryTokenExists() {
            // arrange
            Long userId = 2L;
            PlaceOrderCommand command = new PlaceOrderCommand(
                    userId,
                    List.of(new OrderItemCommand(productId, 1L)),
                    null
            );

            // act
            var result = placeOrderUseCase.execute(command);

            // assert
            assertThat(result.orderId()).isNotNull();
        }
    }
}
