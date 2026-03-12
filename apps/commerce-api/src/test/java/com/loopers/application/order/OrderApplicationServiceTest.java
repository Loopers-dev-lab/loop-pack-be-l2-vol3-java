package com.loopers.application.order;

import com.loopers.domain.common.Money;
import com.loopers.domain.order.OrderStatus;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.Stock;
import com.loopers.fake.FakeOrderRepository;
import com.loopers.fake.FakeProductRepository;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DisplayName("OrderApplicationService 테스트")
class OrderApplicationServiceTest {

    private FakeProductRepository fakeProductRepository;
    private FakeOrderRepository fakeOrderRepository;
    private OrderApplicationService orderApplicationService;

    @BeforeEach
    void setUp() {
        fakeProductRepository = new FakeProductRepository();
        fakeOrderRepository = new FakeOrderRepository();
        orderApplicationService = new OrderApplicationService(fakeProductRepository, fakeOrderRepository);
    }

    private Product createAndSaveProduct(String name, long price, int stock) {
        Product product = Product.create(1L, name, "설명",
            new Money(price), new Stock(stock), "http://image.url");
        return fakeProductRepository.save(product);
    }

    @Nested
    @DisplayName("주문 생성")
    class PlaceOrder {

        @Test
        @DisplayName("성공 - 단일 상품 주문")
        void 단일_상품_주문_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product.getId(), 2)
            );

            // Act
            OrderResult result = orderApplicationService.placeOrder(userId, items);

            // Assert
            assertThat(result.userId()).isEqualTo(userId);
            assertThat(result.items()).hasSize(1);
            assertThat(result.items().get(0).productId()).isEqualTo(product.getId());
            assertThat(result.items().get(0).quantity()).isEqualTo(2);
            assertThat(result.items().get(0).priceSnapshot()).isEqualTo(10000);
            assertThat(result.totalPrice()).isEqualTo(20000); // 10000 * 2
            assertThat(result.status()).isEqualTo(OrderStatus.CREATED);

            // 재고 차감 확인
            Product updatedProduct = fakeProductRepository.findById(product.getId()).orElseThrow();
            assertThat(updatedProduct.getStock().quantity()).isEqualTo(98); // 100 - 2
        }

        @Test
        @DisplayName("성공 - 복수 상품 주문")
        void 복수_상품_주문_성공() {
            // Arrange
            Product product1 = createAndSaveProduct("상품1", 10000, 100);
            Product product2 = createAndSaveProduct("상품2", 20000, 50);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product1.getId(), 2),
                new OrderItemRequest(product2.getId(), 1)
            );

            // Act
            OrderResult result = orderApplicationService.placeOrder(userId, items);

            // Assert
            assertThat(result.items()).hasSize(2);
            assertThat(result.totalPrice()).isEqualTo(40000); // 10000*2 + 20000*1
        }

        @Test
        @DisplayName("실패 - 주문 항목이 비어있는 경우")
        void 주문항목_비어있음_예외() {
            // Arrange
            Long userId = 1L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, List.of()));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패 - 주문 항목이 null인 경우")
        void 주문항목_null_예외() {
            // Arrange
            Long userId = 1L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, null));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.BAD_REQUEST);
        }

        @Test
        @DisplayName("실패 - 상품이 존재하지 않는 경우")
        void 상품_미존재_예외() {
            // Arrange
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(999L, 1)
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 재고 부족")
        void 재고_부족_예외() {
            // Arrange
            Product product = createAndSaveProduct("재고 적은 상품", 10000, 5);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(
                new OrderItemRequest(product.getId(), 10) // 재고 5인데 10개 주문
            );

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.placeOrder(userId, items));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.INSUFFICIENT_STOCK);
        }
    }

    @Nested
    @DisplayName("주문 조회")
    class GetOrder {

        @Test
        @DisplayName("성공 - 주문 ID와 사용자 ID로 조회")
        void 주문_조회_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            List<OrderItemRequest> items = List.of(new OrderItemRequest(product.getId(), 2));
            OrderResult created = orderApplicationService.placeOrder(userId, items);

            // Act
            OrderResult result = orderApplicationService.getOrder(created.id(), userId);

            // Assert
            assertThat(result.id()).isEqualTo(created.id());
            assertThat(result.userId()).isEqualTo(userId);
        }

        @Test
        @DisplayName("실패 - 주문이 존재하지 않는 경우")
        void 주문_미존재_예외() {
            // Arrange
            Long userId = 1L;
            Long nonExistentOrderId = 999L;

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.getOrder(nonExistentOrderId, userId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }

        @Test
        @DisplayName("실패 - 다른 사용자의 주문 조회 시")
        void 타인_주문_조회_예외() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            Long otherUserId = 2L;
            List<OrderItemRequest> items = List.of(new OrderItemRequest(product.getId(), 2));
            OrderResult created = orderApplicationService.placeOrder(userId, items);

            // Act & Assert
            CoreException ex = assertThrows(CoreException.class,
                () -> orderApplicationService.getOrder(created.id(), otherUserId));
            assertThat(ex.getErrorType()).isEqualTo(ErrorType.NOT_FOUND);
        }
    }

    @Nested
    @DisplayName("주문 목록 조회")
    class GetOrders {

        @Test
        @DisplayName("성공 - 사용자의 주문 목록 조회")
        void 주문_목록_조회_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 1)));
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 2)));

            // Act
            List<OrderResult> results = orderApplicationService.getOrders(userId, 0, 10);

            // Assert
            assertThat(results).hasSize(2);
        }

        @Test
        @DisplayName("성공 - 주문이 없는 경우 빈 목록 반환")
        void 주문_없음_빈목록() {
            // Arrange
            Long userId = 1L;

            // Act
            List<OrderResult> results = orderApplicationService.getOrders(userId, 0, 10);

            // Assert
            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("주문 수 조회")
    class CountOrders {

        @Test
        @DisplayName("성공 - 사용자의 주문 수 조회")
        void 주문_수_조회_성공() {
            // Arrange
            Product product = createAndSaveProduct("테스트 상품", 10000, 100);
            Long userId = 1L;
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 1)));
            orderApplicationService.placeOrder(userId, List.of(new OrderItemRequest(product.getId(), 2)));

            // Act
            long count = orderApplicationService.countOrders(userId);

            // Assert
            assertThat(count).isEqualTo(2);
        }
    }
}
