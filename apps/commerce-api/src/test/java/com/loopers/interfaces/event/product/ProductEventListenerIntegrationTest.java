package com.loopers.interfaces.event.product;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.loopers.application.order.PlaceOrderCommand;
import com.loopers.application.order.PlaceOrderCommand.OrderItemCommand;
import com.loopers.application.order.PlaceOrderResult;
import com.loopers.application.order.PlaceOrderUseCase;
import com.loopers.domain.like.LikeRepository;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.order.OrderService;
import com.loopers.domain.product.Product;
import com.loopers.support.BaseIntegrationTest;
import com.loopers.support.error.CoreException;

class ProductEventListenerIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private PlaceOrderUseCase placeOrderUseCase;

    @Autowired
    private OrderService orderService;

    @Autowired
    private LikeService likeService;

    @Autowired
    private LikeRepository likeRepository;

    private Long brandId;

    @BeforeEach
    void setUp() {
        brandId = initDefaultBrand();
    }

    @DisplayName("주문 생성 이벤트가 발행되면,")
    @Nested
    class OrderPlacedEvent {

        @DisplayName("주문 항목의 재고가 차감된다.")
        @Test
        void deductsStock_whenOrderPlaced() {
            // arrange
            Long productId = createProduct(brandId, "상품A", 10000L, 50L);
            PlaceOrderCommand command = new PlaceOrderCommand(
                    1L,
                    List.of(new OrderItemCommand(productId, 3L)),
                    null
            );

            // act
            placeOrderUseCase.execute(command);

            // assert — BEFORE_COMMIT이므로 동기적으로 반영됨
            Product product = productService.getActiveProduct(productId);
            assertThat(product.getStock().getValue()).isEqualTo(47L);
        }

        @DisplayName("재고가 부족하면, 주문 생성이 실패한다.")
        @Test
        void failsOrder_whenInsufficientStock() {
            // arrange
            Long productId = createProduct(brandId, "상품B", 10000L, 2L);
            PlaceOrderCommand command = new PlaceOrderCommand(
                    1L,
                    List.of(new OrderItemCommand(productId, 5L)),
                    null
            );

            // act & assert — BEFORE_COMMIT 실패 시 트랜잭션 롤백
            assertThatThrownBy(() -> placeOrderUseCase.execute(command))
                    .isInstanceOf(CoreException.class);

            Product product = productService.getActiveProduct(productId);
            assertThat(product.getStock().getValue()).isEqualTo(2L);
        }
    }

    @DisplayName("주문 실패 이벤트가 발행되면,")
    @Nested
    class OrderFailedEvent {

        @DisplayName("차감된 재고가 복원된다.")
        @Test
        void restoresStock_whenOrderFailed() {
            // arrange
            Long productId = createProduct(brandId, "상품C", 10000L, 50L);
            PlaceOrderCommand command = new PlaceOrderCommand(
                    1L,
                    List.of(new OrderItemCommand(productId, 10L)),
                    null
            );
            PlaceOrderResult result = placeOrderUseCase.execute(command);

            // act
            orderService.fail(result.orderId());

            // assert — AFTER_COMMIT + @Async이므로 비동기 대기
            await().atMost(5, SECONDS).untilAsserted(() -> {
                Product product = productService.getActiveProduct(productId);
                assertThat(product.getStock().getValue()).isEqualTo(50L);
            });
        }
    }

    @DisplayName("상품 삭제 이벤트가 발행되면,")
    @Nested
    class ProductDeletedEvent {

        @DisplayName("해당 상품의 좋아요가 일괄 삭제된다.")
        @Test
        void deletesAllLikes_whenProductDeleted() {
            // arrange
            Long productId = createProduct(brandId);
            likeService.like(1L, productId);
            likeService.like(2L, productId);
            likeService.like(3L, productId);

            // act
            productService.delete(productId);

            // assert — AFTER_COMMIT + @Async이므로 비동기 대기
            await().atMost(5, SECONDS).untilAsserted(() ->
                    assertThat(likeRepository.existsByUserIdAndProductId(1L, productId)).isFalse()
            );
        }
    }
}
